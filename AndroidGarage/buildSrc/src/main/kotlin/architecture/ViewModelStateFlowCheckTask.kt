package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Bans `.stateIn(viewModelScope, ...)` in ViewModel files (ADR-017 Rule 6).
 *
 * The `stateIn(Eagerly)` pattern caused a real production bug where
 * Compose collectors did not observe upstream state changes (see PR #295,
 * AuthViewModel auth state UI regression). The required pattern is
 * explicit `MutableStateFlow + init.collect`.
 *
 * Only literal `viewModelScope` is banned — `stateIn(applicationScope, ...)`
 * in a Manager class is legitimate.
 */
abstract class ViewModelStateFlowCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    private val pattern = Regex("""\.stateIn\s*\(\s*viewModelScope""")
    private val fileNameRegex = Regex(".*ViewModel\\.kt")

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" && fileNameRegex.matches(it.name) }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (pattern.containsMatchIn(line)) {
                            violations.add(
                                "${file.relativeTo(dir).path}:${index + 1}: " +
                                    "stateIn(viewModelScope, ...) is banned in ViewModels (ADR-017 Rule 6). " +
                                    "Use explicit MutableStateFlow + init.collect instead. " +
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
