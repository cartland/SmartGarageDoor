package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Phase 3 of the string-resource migration plan
 * (`AndroidGarage/docs/PENDING_FOLLOWUPS.md` item #1). Forbids hardcoded
 * `Text("literal")` and `Text(text = "literal")` patterns in production
 * Composable code so user-visible labels can't regress to inline strings
 * after the migration.
 *
 * **What gets flagged:**
 * - `Text("anything")`
 * - `Text(text = "anything")`
 *
 * **What gets exempted (not flagged):**
 * - Files matching the configured [exemptFilePatterns] — preview-only
 *   files (`*Preview*.kt`, theme tokens previews) construct fake fixture
 *   data with literal strings. Per the plan, preview fake data is not
 *   user-visible at runtime and stays as Kotlin literals.
 * - KDoc / multi-line-comment lines (start with `*`) — code examples
 *   inside doc comments aren't compiled.
 * - Empty / whitespace-only string literals (`Text("")`, `Text("\n")`).
 * - Lines listed in the [exemptionsFile] — the ratchet pattern. Existing
 *   violations at lint-introduction time go in the exemption file; new
 *   violations are blocked.
 *
 * **Exemption file format:** one violation per line, in the form
 * `relative/path/File.kt:lineNumber`. Lines starting with `#` are comments.
 * If the file does not exist, no exemptions are applied (treat as empty).
 *
 * Detection strategy: simple regex over each `.kt` file. Not AST-aware,
 * so does NOT distinguish between `Text("...")` inside a `@Composable`
 * function vs. inside a `data class` default arg vs. inside a regular
 * function body — but since we only care about Composable bodies, the
 * exemption mechanism handles edge cases (data class defaults that
 * legitimately carry strings can be moved to the exemption file or, in
 * the long run, refactored to typed-hint shapes per Phase 2 of the plan).
 */
abstract class LiteralStringsInComposeCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @get:Input
    var exemptFilePatterns: List<String> = listOf(
        ".*Preview.*\\.kt",
        ".*Tokens.*Preview\\.kt",
    )

    @get:Input
    var exemptionsFile: String = ""

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val filePatterns = exemptFilePatterns.map(::Regex)
        val exemptions = loadExemptions()
        // Match Text("..."), Text(text = "..."), Text(text="..."). Tolerates
        // whitespace and an optional argument-name keyword. Captures the
        // string content so we can skip empty/whitespace-only literals.
        val textPattern = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"([^"]*)"\s*[,)]""")

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file ->
                    val name = file.name
                    filePatterns.any { it.matches(name) }
                }.forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("*") || trimmed.startsWith("//")) return@forEachIndexed

                        val match = textPattern.find(line) ?: return@forEachIndexed
                        val literal = match.groupValues[1]
                        // Empty / whitespace-only literals are never user-visible.
                        if (literal.isBlank()) return@forEachIndexed

                        val location = "$relativePath:${index + 1}"
                        if (location in exemptions) return@forEachIndexed
                        violations.add("$location: ${line.trim()}")
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("LiteralStringsInCompose check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Phase 3 of the string-resource migration plan\n")
                    append("(AndroidGarage/docs/PENDING_FOLLOWUPS.md item #1):\n")
                    append("user-visible Text() literals must move to res/values/strings.xml\n")
                    append("and use stringResource(R.string.X). The mapper / ViewModel layer\n")
                    append("emits typed states; the Composable resolves type → string at\n")
                    append("render time.\n\n")
                    append("Fix:\n")
                    append("  - Add the string to AndroidGarage/androidApp/src/main/res/values/strings.xml\n")
                    append("  - Replace the literal with stringResource(R.string.<key>)\n")
                    append("  - Use formatArgs (%1\$s, %2\$d) for interpolated strings\n")
                    append("  - Use <plurals> + pluralStringResource for count-based strings\n\n")
                    append("If the string is genuinely not user-visible (preview fake data,\n")
                    append("test fixture, dead code), add it to the exemption file:\n")
                    append("  AndroidGarage/string-literal-exemptions.txt\n")
                    append("Format: one entry per line, e.g. `androidApp/src/main/.../File.kt:42`\n")
                },
            )
        }

        logger.lifecycle("LiteralStringsInCompose check passed: no hardcoded Text() literals found.")
    }

    private fun loadExemptions(): Set<String> {
        if (exemptionsFile.isBlank()) return emptySet()
        val file = File(exemptionsFile)
        if (!file.exists()) return emptySet()
        return file
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }
}
