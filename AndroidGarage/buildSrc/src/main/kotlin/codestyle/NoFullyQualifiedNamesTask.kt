package codestyle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that detects fully qualified class names used inline in code.
 *
 * Fully qualified names hide dependencies — use imports instead so each file's
 * dependencies are visible at the top. This check scans Kotlin source files
 * for patterns like `com.example.Foo` outside of import statements.
 */
abstract class NoFullyQualifiedNamesTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Package prefixes to check for inline usage.
     * Only these prefixes are flagged — standard library and annotation usage is allowed.
     */
    @get:Input
    var checkedPrefixes: List<String> = listOf(
        "com.chriscartland.garage.",
        "kotlinx.coroutines.",
        "kotlinx.serialization.",
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
                        // Skip import lines, package declarations, and comments
                        if (trimmed.startsWith("import ") ||
                            trimmed.startsWith("package ") ||
                            trimmed.startsWith("//") ||
                            trimmed.startsWith("*") ||
                            trimmed.startsWith("/*")
                        ) {
                            return@forEachIndexed
                        }
                        // Strip string literals to avoid false positives
                        val withoutStrings = trimmed.replace(Regex("\"[^\"]*\""), "\"\"")
                        for (prefix in checkedPrefixes) {
                            if (withoutStrings.contains(prefix)) {
                                val relativePath = file.relativeTo(dir).path
                                violations.add("  $relativePath:${index + 1}: $trimmed")
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Fully qualified names found in code — use imports instead:")
                appendLine()
                violations.forEach { appendLine(it) }
                appendLine()
                appendLine("${violations.size} violation(s). Add proper imports at the top of the file.")
            }
            throw GradleException(message)
        }

        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle("No fully qualified names: $fileCount files scanned, 0 violations.")
    }
}
