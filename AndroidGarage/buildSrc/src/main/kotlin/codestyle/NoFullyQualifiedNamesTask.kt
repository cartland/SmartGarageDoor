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
 * dependencies are visible at the top. Uses two detection strategies:
 *
 * 1. **Known prefixes** — exact package prefixes for project and dependencies
 * 2. **TLD regex** — catches `com.`, `org.`, `io.`, `net.` followed by lowercase
 *    segments (looks like a Java/Kotlin package path)
 */
abstract class NoFullyQualifiedNamesTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Known package prefixes to flag. Covers project code and all dependencies.
     */
    @get:Input
    var checkedPrefixes: List<String> = listOf(
        // Project
        "com.chriscartland.garage.",
        // Kotlin/KotlinX
        "kotlinx.coroutines.",
        "kotlinx.serialization.",
        "kotlin.time.",
        // AndroidX
        "androidx.lifecycle.",
        "androidx.compose.",
        "androidx.navigation.",
        "androidx.room.",
        // Ktor
        "io.ktor.",
        // Firebase
        "com.google.firebase.",
        "com.google.android.",
        // Kermit logging
        "co.touchlab.kermit.",
        // kotlin-inject
        "me.tatarka.inject.",
    )

    /**
     * Regex patterns that match common TLD-based package paths.
     * Catches FQNs from libraries not in the known prefix list.
     * Pattern: TLD + lowercase segment + dot + more segments + uppercase class name.
     */
    private val tldPatterns: List<Regex> = listOf(
        // com.foo.bar.Baz, org.foo.bar.Baz, io.foo.bar.Baz, net.foo.bar.Baz
        Regex("""(?<!\w)(com|org|io|net)\.[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*\.[A-Z]"""),
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

                        // Check known prefixes
                        for (prefix in checkedPrefixes) {
                            if (withoutStrings.contains(prefix)) {
                                val relativePath = file.relativeTo(dir).path
                                violations.add("  $relativePath:${index + 1}: $trimmed")
                                return@forEachIndexed // One violation per line
                            }
                        }

                        // Check TLD regex patterns
                        for (pattern in tldPatterns) {
                            if (pattern.containsMatchIn(withoutStrings)) {
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
