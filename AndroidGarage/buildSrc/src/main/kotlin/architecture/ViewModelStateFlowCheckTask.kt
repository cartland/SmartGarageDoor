package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces two ViewModel rules related to state exposure.
 *
 * ### Rule 1 (ADR-017 Rule 6): no `.stateIn(viewModelScope, ...)`
 *
 * The `stateIn(Eagerly)` pattern caused a real production bug where
 * Compose collectors did not observe upstream state changes (see PR #295,
 * AuthViewModel auth state UI regression). The required pattern is
 * explicit `MutableStateFlow + init.collect`.
 *
 * Only literal `viewModelScope` is banned — `stateIn(applicationScope, ...)`
 * in a Manager class is legitimate.
 *
 * ### Rule 2 (ADR-022): no VM-local mirror of repository-owned state types
 *
 * Domain state types whose ownership lives on a `@Singleton` repository
 * must never be re-declared inside a ViewModel as a private
 * `MutableStateFlow<T>`. The repo owns the authoritative flow; the VM
 * exposes it by reference via the observe-UseCase.
 *
 * History: this rule was briefly withdrawn on 2026-04-19 after the
 * android/170 regression, then reinstated on android/174 once Phase 2f
 * fixed the kotlin-inject `@Singleton` scoping bug that silently broke
 * the pass-through chain. See `docs/DI_SINGLETON_REQUIREMENTS.md` —
 * this rule is meaningless without the DI fix, which itself is guarded
 * by `ComponentGraphTest.*IsSingleton` tests.
 *
 * Extend the allowlist (`bannedStateTypesInViewModels`) as more state-y
 * types migrate to the ADR-022 shape.
 */
abstract class ViewModelStateFlowCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Domain state types that must be owned on the repository. A
     * `MutableStateFlow<X>` with any of these type arguments inside a
     * ViewModel is a mirror and fails the check.
     *
     * Add new types as their repository migrations land.
     */
    @get:Input
    var bannedStateTypesInViewModels: List<String> = listOf(
        "SnoozeState",
        "AuthState",
        "FcmRegistrationStatus",
    )

    private val stateInPattern = Regex("""\.stateIn\s*\(\s*viewModelScope""")
    private val fileNameRegex = Regex(".*ViewModel\\.kt")

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        val bannedTypeRegex = bannedStateTypesInViewModels
            .takeIf { it.isNotEmpty() }
            ?.let { Regex("""\bMutableStateFlow\s*<\s*(${it.joinToString("|")})\s*\??\s*>""") }

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" && fileNameRegex.matches(it.name) }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (stateInPattern.containsMatchIn(line)) {
                            violations.add(
                                "${file.relativeTo(dir).path}:${index + 1}: " +
                                    "stateIn(viewModelScope, ...) is banned in ViewModels (ADR-017 Rule 6). " +
                                    "Use explicit MutableStateFlow + init.collect instead. " +
                                    "Line: ${line.trim()}",
                            )
                        }
                        val bannedMatch = bannedTypeRegex?.find(line)
                        if (bannedMatch != null) {
                            val typeArg = bannedMatch.groupValues[1]
                            violations.add(
                                "${file.relativeTo(dir).path}:${index + 1}: " +
                                    "VM-local MutableStateFlow<$typeArg> is banned. " +
                                    "State-y types are owned by a @Singleton repository. " +
                                    "Line: ${line.trim()}",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "ViewModel StateFlow Check Failed (${violations.size} violation(s)):\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }
        logger.lifecycle("ViewModel StateFlow check passed: no stateIn(viewModelScope, ...) usages.")
    }
}
