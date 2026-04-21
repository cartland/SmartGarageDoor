package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-009: no bare top-level functions — group them in a named
 * `object { }`. Matters most in KMP `commonMain` code, where every
 * top-level function is on the shared API surface; in Android-only code
 * it remains a discoverability win.
 *
 * **Exemptions (not flagged):**
 * - Extension functions (`fun Foo.bar()`) — per ADR-009's KMP-driven
 *   rationale, these stay top-level so the extension-syntax call site
 *   keeps working; an extension can't legally live inside an `object`.
 * - `@Composable` and `@Preview` functions — Compose requires top-level
 *   composables.
 * - Operator functions — same constraint as extensions.
 *
 * Detection strategy: scans each `.kt` file line-by-line for lines that
 * begin a function declaration at column 0 (i.e., `fun X` or
 * `private fun X` etc., no leading whitespace — inside a class they'd
 * be indented). A function qualifies as "bare" if its signature up to
 * the first `(` contains no `.` (not an extension) and the preceding
 * annotation lines don't include `@Composable`, `@Preview`, or similar
 * exempt markers.
 */
abstract class NoBareTopLevelFunctionsTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @get:Input
    var exemptAnnotations: List<String> = listOf(
        "@Composable",
        "@Preview",
        "@PreviewTest",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    val lines = file.readLines()
                    val funLinePattern = Regex("""^(?:(?:public|internal|private)\s+)?(?:inline\s+)?fun\s+[A-Za-z_]\w*""")

                    lines.forEachIndexed { index, line ->
                        if (!funLinePattern.containsMatchIn(line)) return@forEachIndexed

                        val parenIdx = line.indexOf('(')
                        if (parenIdx < 0) return@forEachIndexed
                        val signature = line.substring(0, parenIdx)
                        // Extension functions have a `.` in the signature (e.g., `fun Context.foo()`).
                        if (signature.contains('.')) return@forEachIndexed

                        // Look backward through annotation lines to check for exempt markers.
                        if (isExempt(lines, index)) return@forEachIndexed

                        violations.add("$relativePath:${index + 1}: ${line.trim()}")
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoBareTopLevelFunctions check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ADR-009: bare top-level functions (not extensions, not Composables)\n")
                    append("must be grouped in a named `object { }` so the name is discoverable\n")
                    append("and the KMP shared-module API surface stays tidy.\n\n")
                    append("Fix:\n")
                    append("  - Wrap the function in `object GroupName { fun name(...): T = ... }`\n")
                    append("  - Update call sites to `GroupName.name(...)`\n")
                    append("  - Extension functions (`fun Foo.bar()`) stay top-level\n")
                    append("  - @Composable / @Preview stay top-level\n\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-009).\n")
                },
            )
        }

        logger.lifecycle("NoBareTopLevelFunctions check passed: no unwrapped top-level functions found.")
    }

    private fun isExempt(
        lines: List<String>,
        funIndex: Int,
    ): Boolean {
        // Walk backward through annotation-only lines to see if any exempt
        // marker is present. Stop when we reach a non-annotation line.
        var i = funIndex - 1
        while (i >= 0) {
            val prev = lines[i].trim()
            if (prev.isEmpty()) {
                i--
                continue
            }
            if (prev.startsWith("@")) {
                if (exemptAnnotations.any { prev.startsWith(it) }) return true
                i--
                continue
            }
            // Hit something that's not whitespace or annotation — stop.
            return false
        }
        return false
    }
}
