package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces clean layer boundaries by scanning source imports.
 *
 * Catches violations that module-level dependency checks miss —
 * for example, when `:usecase` doesn't depend on `:data` but a
 * ViewModel in androidApp accidentally imports a DataSource directly.
 *
 * Rules:
 * - ViewModel classes must not import DataSource, Repository impl, or Bridge impl
 * - UseCase classes must not import DataSource or Bridge impl
 * - Repository impls must not import other Repository impls
 *
 * Configure via [rules] — each rule specifies a file pattern, forbidden
 * import prefixes, and a human-readable explanation.
 */
abstract class LayerImportCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Each rule: Triple(fileNamePattern, forbiddenImportPrefixes, explanation).
     *
     * fileNamePattern: regex matched against the file name (e.g., ".*ViewModel\\.kt")
     * forbiddenImportPrefixes: import prefixes that should not appear in matching files
     * explanation: shown in the error message
     */
    @get:Input
    var rules: List<List<String>> = emptyList()

    @TaskAction
    fun check() {
        val parsedRules = rules.map { parts ->
            require(parts.size == 3) {
                "Each rule must have 3 parts: [filePattern, forbiddenPrefixes (comma-separated), explanation]"
            }
            Triple(
                Regex(parts[0]),
                parts[1].split(",").map { it.trim() },
                parts[2],
            )
        }

        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    val matchingRules = parsedRules.filter { (pattern, _, _) ->
                        pattern.matches(file.name)
                    }
                    if (matchingRules.isEmpty()) return@forEach

                    val lines = file.readLines()
                    val relativePath = file.relativeTo(dir).path

                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("import ")) return@forEachIndexed
                        val importPath = trimmed.removePrefix("import ").trim()

                        for ((_, forbidden, explanation) in matchingRules) {
                            for (prefix in forbidden) {
                                if (importPath.startsWith(prefix)) {
                                    violations.add(
                                        "$relativePath:${index + 1}: $trimmed\n" +
                                            "    $explanation",
                                    )
                                }
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Layer Import Check Failed (${violations.size} violation(s)):\n\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }

        val ruleCount = parsedRules.size
        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle("Layer import check passed: $fileCount files scanned, $ruleCount rules enforced.")
    }
}
