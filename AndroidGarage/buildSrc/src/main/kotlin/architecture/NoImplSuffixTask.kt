package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-008: no `*Impl` suffix on Kotlin class names.
 *
 * `*Impl` says nothing about what the implementation actually does.
 * Prefer descriptive prefixes that name the backing mechanism or role:
 *
 *   - `Network*`, `Ktor*` — HTTP data sources
 *   - `Cached*`, `Room*` — persistence-backed
 *   - `Firebase*`, `Google*` — platform-specific SDK wrappers
 *   - `Default*` — the canonical production impl when there's also a
 *     `Fake*` / `InMemory*` for tests
 *
 * This lint had zero violations when added, so it locks the current
 * state rather than prescribing a migration.
 */
abstract class NoImplSuffixTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * File-name patterns to exempt. Generated KSP output or specific
     * third-party wrappers may need to opt out.
     */
    @get:Input
    var allowedFilePatterns: List<String> = emptyList()

    @TaskAction
    fun check() {
        // Matches class declarations whose name ends in "Impl" at word boundary.
        // Handles the common modifiers: open, abstract, sealed, data, internal,
        // public, private. Also catches `internal class FooImpl`, `data class BarImpl`,
        // and plain `class BazImpl`. Does not flag `*Implementation` (that's a
        // different word), nested class keywords on the same line, or comments.
        val classPattern = Regex(
            pattern = """^\s*(?:(?:open|abstract|sealed|data|internal|public|private|inner)\s+)*class\s+(\w+Impl)\b""",
            option = RegexOption.MULTILINE,
        )
        val allowedRegexes = allowedFilePatterns.map { Regex(it) }

        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> allowedRegexes.none { it.matches(file.name) } }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    val content = file.readText()
                    classPattern.findAll(content).forEach { match ->
                        val className = match.groupValues[1]
                        val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1
                        violations.add("$relativePath:$lineNumber: class $className")
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoImplSuffix check FAILED: ")
                    append(violations.size)
                    append(" class(es) end in `Impl`.\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ADR-008: `*Impl` says nothing about what the implementation does.\n")
                    append("Rename using a descriptive prefix:\n")
                    append("  - `Network*`, `Ktor*`       (HTTP data sources)\n")
                    append("  - `Cached*`, `Room*`        (persistence-backed)\n")
                    append("  - `Firebase*`, `Google*`    (platform SDK wrappers)\n")
                    append("  - `Default*`                (canonical production impl)\n")
                    append("  - `Fake*`, `InMemory*`      (test doubles)\n\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-008).\n")
                },
            )
        }

        logger.lifecycle("NoImplSuffix check passed: no `*Impl` class names found.")
    }
}
