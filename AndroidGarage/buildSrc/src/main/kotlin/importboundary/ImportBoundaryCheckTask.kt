package importboundary

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that enforces import boundaries in shared KMP modules.
 *
 * Scans `commonMain` source sets for imports that should not appear in shared code
 * (e.g., `android.*`, `androidx.*`). Violations fail the build.
 *
 * Usage in build.gradle.kts:
 * ```
 * tasks.register<ImportBoundaryCheckTask>("checkImportBoundary") {
 *     sourceDir = "$projectDir/src/commonMain/kotlin"
 *     forbiddenPrefixes = listOf("android.", "androidx.", "com.google.firebase.")
 * }
 * ```
 */
abstract class ImportBoundaryCheckTask : DefaultTask() {
    @get:Input
    var sourceDir: String = ""

    @get:Input
    var forbiddenPrefixes: List<String> = listOf(
        "android.",
        "androidx.",
        "com.google.firebase.",
        "com.google.android.",
        "java.time.",
        "java.text.",
        "java.util.Date",
        "java.util.Locale",
    )

    /**
     * Prefixes that are allowed even if they match a forbidden prefix.
     * Use for KMP-compatible androidx libraries (e.g., "androidx.lifecycle.").
     */
    @get:Input
    var allowedPrefixes: List<String> = emptyList()

    @TaskAction
    fun check() {
        val srcDir = File(sourceDir)
        if (!srcDir.exists()) {
            logger.lifecycle("Import boundary check: $sourceDir does not exist, skipping.")
            return
        }

        val violations = mutableListOf<String>()

        srcDir
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val importPath = trimmed.removePrefix("import ").trim()
                        val isAllowed = allowedPrefixes.any { importPath.startsWith(it) }
                        if (!isAllowed) {
                            for (prefix in forbiddenPrefixes) {
                                if (importPath.startsWith(prefix)) {
                                    val relativePath = file.relativeTo(srcDir).path
                                    violations.add("  $relativePath:${index + 1}: import $importPath")
                                }
                            }
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Import boundary violations in commonMain:")
                appendLine("These imports are not allowed in shared KMP modules.")
                appendLine()
                violations.forEach { appendLine(it) }
                appendLine()
                appendLine("${violations.size} violation(s) found.")
            }
            throw GradleException(message)
        }

        logger.lifecycle(
            "Import boundary check passed: ${srcDir.walkTopDown().filter { it.extension == "kt" }.count()} files scanned, 0 violations.",
        )
    }
}
