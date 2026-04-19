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
 * (ADR-022) must never be re-declared inside a ViewModel as a private
 * `MutableStateFlow<T>`. If a VM owns its own `MutableStateFlow<SnoozeState>`,
 * it's mirroring the repo's flow and will drift under multiple VM
 * instances (android/164-168).
 *
 * The allowlist holds the domain state types we've migrated so far. Adding
 * a new type to the list is how we extend enforcement as more state-y data
 * moves to repository ownership.
 */
abstract class ViewModelStateFlowCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Domain state types that must be owned on the repository. A
     * `MutableStateFlow<X>` with any of these type arguments inside a
     * ViewModel is a mirror and fails the check.
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

        val bannedTypeRegex = Regex(
            """\bMutableStateFlow\s*<\s*(${bannedStateTypesInViewModels.joinToString("|")})\s*\??\s*>""",
        )

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
                        val bannedMatch = bannedTypeRegex.find(line)
                        if (bannedMatch != null) {
                            val typeArg = bannedMatch.groupValues[1]
                            violations.add(
                                "${file.relativeTo(dir).path}:${index + 1}: " +
                                    "VM-local MutableStateFlow<$typeArg> is banned (ADR-022). " +
                                    "State-y types are owned by a @Singleton repository — expose the " +
                                    "repo's StateFlow by reference (observeXUseCase()), do not mirror. " +
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
        logger.lifecycle("ViewModel StateFlow check passed: no stateIn(viewModelScope, ...) usages and no banned VM-local mirrors.")
    }
}
