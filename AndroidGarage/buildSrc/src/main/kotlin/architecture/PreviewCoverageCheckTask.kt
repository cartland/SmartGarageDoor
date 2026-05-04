package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces that every public `*Preview` Composable in [sourceRoot] is
 * imported by at least one screenshot-test source file under [testRoot].
 *
 * Catches the silent failure mode where a fixture is added but the
 * screenshot-test wrapper is forgotten — production renders the new UI but
 * the framed README screenshot doesn't, so the README diverges from what
 * users see. PR #623 → #625 surfaced this concretely: 4 `TitleBarCheckInPill`
 * preview helpers existed in production but no screenshot test imported them,
 * which made the "pill moved to Status header" change invisible to the
 * framed-tab references for two PRs in a row.
 *
 * Detection is regex-based, mirroring battery-butler's
 * `screenshotcoverage.PreviewCoverageCheckTask` (the original of this port).
 *
 * Production previews:
 *   `@Preview[^@]*@Composable\s+fun\s+(\w+Preview)\s*\(`
 *
 * Screenshot-test imports:
 *   `import\s+[\w.]+\.(\w+Preview)\s`
 *
 * Coverage = production preview names that appear in the test imports.
 *
 * Side-effect: writes a Markdown report to [reportFile] (typical:
 * `AndroidGarage/android-screenshot-tests/PREVIEW_COVERAGE.md`) so reviewers
 * can see covered/uncovered counts without running the task.
 *
 * Suppression: there is intentionally no `@PreviewCoverageExempt` marker.
 * If a preview is genuinely not worth a screenshot test (e.g., a private
 * helper), make it `private` — the regex requires the `fun` keyword preceded
 * by `@Composable`, so private functions are visible to the regex but in
 * practice if the function is private it cannot be imported, so the
 * gap-detection logic naturally handles it. Public previews are the contract;
 * the type system can't enforce that contract, this task does.
 */
abstract class PreviewCoverageCheckTask : DefaultTask() {
    /** Source root containing production `@Preview`-annotated Composables. */
    @get:Input
    var sourceRoot: String = ""

    /** Source root containing screenshot test files (typically `android-screenshot-tests/src/screenshotTest`). */
    @get:Input
    var testRoot: String = ""

    /** Path to write the human-readable coverage report. */
    @get:Input
    var reportFile: String = ""

    /** Project root, used to compute relative paths in the report. Pass `"$rootDir"` from build.gradle.kts. */
    @get:Input
    var projectRoot: String = ""

    private data class PreviewInfo(
        val name: String,
        val file: String,
    )

    @TaskAction
    fun check() {
        val srcDir = File(sourceRoot)
        if (!srcDir.exists()) {
            throw GradleException("Preview Coverage Check: sourceRoot does not exist: $sourceRoot")
        }
        val testDir = File(testRoot)
        if (!testDir.exists()) {
            throw GradleException("Preview Coverage Check: testRoot does not exist: $testRoot")
        }

        val rootDir = if (projectRoot.isNotBlank()) File(projectRoot) else srcDir

        // Pass 1: collect production previews (pattern matches battery-butler's
        // — handles `@Preview[(args)]` followed by `@Composable fun XPreview(`).
        val productionPreviews = mutableSetOf<PreviewInfo>()
        val previewRegex = Regex("""@Preview[^@]*@Composable\s+fun\s+(\w+Preview)\s*\(""")
        srcDir
            .walk()
            .filter { it.extension == "kt" && !it.path.contains("/build/") }
            .forEach { file ->
                val text = file.readText()
                previewRegex.findAll(text).forEach { match ->
                    productionPreviews.add(
                        PreviewInfo(
                            name = match.groupValues[1],
                            file = file.relativeTo(rootDir).path,
                        ),
                    )
                }
            }

        // Pass 2: collect preview-named imports in screenshot test sources.
        // We don't try to enumerate test functions — battery-butler's
        // experience is that a missing import is the most common failure
        // mode (forgetting to import means forgetting to test).
        val testedPreviews = mutableSetOf<String>()
        val importRegex = Regex("""import\s+[\w.]+\.(\w+Preview)\s""")
        testDir
            .walk()
            .filter { it.extension == "kt" && !it.path.contains("/build/") }
            .forEach { file ->
                val text = file.readText()
                importRegex.findAll(text).forEach { match ->
                    testedPreviews.add(match.groupValues[1])
                }
            }

        val uncovered = productionPreviews.filter { it.name !in testedPreviews }
        val covered = productionPreviews.filter { it.name in testedPreviews }

        // Generate report file (committed alongside SCREENSHOT_GALLERY.md).
        if (reportFile.isNotBlank()) {
            val report = File(reportFile)
            report.parentFile?.mkdirs()
            report.writeText(buildReport(productionPreviews, covered, uncovered))
        }

        val total = productionPreviews.size
        val coveredCount = covered.size
        val pct = if (total > 0) (coveredCount * 100 / total) else 100
        println("Preview Coverage: $coveredCount/$total ($pct%)")

        if (uncovered.isNotEmpty()) {
            val msg =
                uncovered.sortedBy { it.name }.joinToString("\n") {
                    "  - ${it.name} (${it.file})"
                }
            throw GradleException(
                "Preview coverage gap: ${uncovered.size} preview(s) missing screenshot tests:\n" +
                    "$msg\n\n" +
                    "Fix: add `import com.chriscartland.garage.<pkg>.<Name>Preview` and a corresponding\n" +
                    "@PreviewTest wrapper in a file under android-screenshot-tests/src/screenshotTest/.\n" +
                    "If a preview genuinely doesn't need a screenshot test, mark it `private` — the\n" +
                    "import-based detection then naturally excludes it.",
            )
        }
    }

    private fun buildReport(
        all: Set<PreviewInfo>,
        covered: List<PreviewInfo>,
        uncovered: List<PreviewInfo>,
    ): String {
        val total = all.size
        val coveredCount = covered.size
        val pct = if (total > 0) (coveredCount * 100 / total) else 100
        return buildString {
            appendLine("<!-- GENERATED FILE - DO NOT EDIT -->")
            appendLine("<!-- Regenerate: ./gradlew -p AndroidGarage checkPreviewCoverage -->")
            appendLine()
            appendLine("# Preview Screenshot Coverage")
            appendLine()
            appendLine("**$coveredCount / $total ($pct%)**")
            appendLine()
            if (uncovered.isNotEmpty()) {
                appendLine("## Uncovered")
                appendLine()
                uncovered.sortedBy { it.name }.forEach {
                    appendLine("- `${it.name}` — `${it.file}`")
                }
                appendLine()
            }
            appendLine("## Covered")
            appendLine()
            covered.sortedBy { it.name }.forEach {
                appendLine("- `${it.name}` — `${it.file}`")
            }
        }
    }
}
