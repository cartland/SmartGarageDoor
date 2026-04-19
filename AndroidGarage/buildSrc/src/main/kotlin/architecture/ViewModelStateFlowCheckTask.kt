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
 * ### Rule 2 (ADR-022, withdrawn): previously banned VM-local mirror of
 * repository-owned state types.
 *
 * Withdrawn after android/170 shipped the pass-through pattern and the
 * repo-owned `StateFlow` failed to reach Compose on production devices
 * even though every debug instrumented test passed. The VM-local mirror
 * + init-collect + direct-write-from-command pattern (PR #354 /
 * android/169) is the empirically-reliable shape. The allowlist is now
 * empty — `bannedStateTypesInViewModels` is kept as a knob in case we
 * ever need selective enforcement again, but it's intentionally inert.
 */
abstract class ViewModelStateFlowCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Domain state types that must be owned on the repository. A
     * `MutableStateFlow<X>` with any of these type arguments inside a
     * ViewModel is a mirror and fails the check.
     *
     * Intentionally empty after ADR-022 Rule 2 was withdrawn — see class
     * KDoc. Re-populate only if a future ADR revives the selective ban.
     */
    @get:Input
    var bannedStateTypesInViewModels: List<String> = emptyList()

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
