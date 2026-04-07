package codestyle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that detects Navigation 2 imports in code.
 *
 * After migrating to Navigation 3, this ensures nobody accidentally
 * re-introduces Nav2 dependencies. Nav2 uses `androidx.navigation.compose`
 * while Nav3 uses `androidx.navigation3`.
 */
abstract class NoNav2ImportsTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Nav2 import prefixes that should not appear after migration to Nav3.
     */
    @get:Input
    var forbiddenPrefixes: List<String> = listOf(
        "androidx.navigation.compose.",
        "androidx.navigation.NavHost",
        "androidx.navigation.NavController",
        "androidx.navigation.NavGraph",
        "androidx.navigation.NavDestination",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("import ")) return@forEachIndexed
                        val importPath = trimmed.removePrefix("import ").trim()
                        for (prefix in forbiddenPrefixes) {
                            if (importPath.startsWith(prefix)) {
                                val relativePath = file.relativeTo(dir).path
                                violations.add("  $relativePath:${index + 1}: $trimmed")
                                return@forEachIndexed
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Navigation 2 imports found — use Navigation 3 (androidx.navigation3) instead:")
                appendLine()
                violations.forEach { appendLine(it) }
                appendLine()
                appendLine("${violations.size} violation(s). Migrate to Nav3: NavDisplay + entryProvider.")
            }
            throw GradleException(message)
        }

        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle("No Nav2 imports: $fileCount files scanned, 0 violations.")
    }
}
