package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Bans hardcoded Color() constructors outside designated theme files.
 *
 * Forces all custom colors into the theme (Color.kt, DoorStatusColorScheme.kt)
 * so they are automatically covered by contrast tests. Any new color added
 * to the theme is tested by ThemeContrastTest without additional work.
 *
 * Detects patterns like:
 * - `Color(0xFF...)`
 * - `Color(0x...)`
 *
 * Allows:
 * - `Color.Black`, `Color.White`, `Color.Transparent`, `Color.Unspecified`
 *   (Material constants, not custom colors)
 * - Files in [allowedFiles] (the theme color definitions themselves)
 * - Test files (they may construct Colors for test assertions)
 */
abstract class HardcodedColorCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /** File name patterns (regex) that are allowed to define Color(0x...). */
    @get:Input
    var allowedFilePatterns: List<String> = emptyList()

    @TaskAction
    fun check() {
        val colorPattern = Regex("""Color\(0x""")
        val allowedPatterns = allowedFilePatterns.map { Regex(it) }

        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .filter { file -> allowedPatterns.none { it.matches(file.name) } }
                .forEach { file ->
                    val relativePath = file.relativeTo(dir).path
                    file.readLines().forEachIndexed { index, line ->
                        if (colorPattern.containsMatchIn(line)) {
                            violations.add(
                                "$relativePath:${index + 1}: ${line.trim()}\n" +
                                    "    Hardcoded colors must be defined in theme files (Color.kt). " +
                                    "Use MaterialTheme.colorScheme or DoorStatusColorScheme instead.",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Hardcoded Color Check Failed (${violations.size} violation(s)):\n\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }

        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle("Hardcoded color check passed: $fileCount files scanned.")
    }
}
