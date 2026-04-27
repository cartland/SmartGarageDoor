package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces the screen ↔ ViewModel cardinality rule.
 *
 * Each screen Composable file (matching [fileNamePattern]) must depend on
 * AT MOST ONE ViewModel. New screens get a dedicated ViewModel that
 * aggregates whatever UseCases the screen needs — this keeps each screen's
 * coupling surface narrow, makes the ViewModel obvious from the file name,
 * and makes it easy to test the screen with a single fake VM.
 *
 * Counting rule: distinct `import com.chriscartland.garage.usecase.*ViewModel`
 * imports (the screen's interface-typed imports). The trailing class name
 * with any leading `Default` prefix stripped is treated as the same VM, so a
 * file that imports both `AuthViewModel` and `DefaultAuthViewModel` still
 * counts as one VM.
 *
 * Exemptions: relative file paths (under [sourceRoot]) listed in
 * [exemptionsFile] are skipped. The exemptions file is the place to record
 * legacy multi-VM screens whose split-up is not yet justified — the goal is
 * a shrinking list, not a permanent escape hatch.
 *
 * Why this rule (and why not 100% strict): aggregating UseCases inside a
 * single ViewModel keeps the ViewModel layer responsible for orchestration
 * and the Composable layer free of multi-VM glue. Some legacy screens
 * predate this convention and would require a meaningful refactor to
 * comply, so we exempt them explicitly rather than block the rule.
 */
abstract class ScreenViewModelCheckTask : DefaultTask() {
    /** Source root that contains screen Composables. */
    @get:Input
    var sourceRoot: String = ""

    /** Regex matched against the file name (e.g., ".*Content\\.kt"). */
    @get:Input
    var fileNamePattern: String = ".*Content\\.kt"

    /** Path to a text file with one exempt relative path per line; comments start with #. */
    @get:Input
    var exemptionsFile: String = ""

    @TaskAction
    fun check() {
        val root = File(sourceRoot)
        if (!root.exists()) {
            logger.lifecycle("Screen ↔ ViewModel check skipped: $sourceRoot does not exist.")
            return
        }
        val pattern = Regex(fileNamePattern)
        val exemptions = readExemptions()

        val violations = mutableListOf<String>()
        var scanned = 0

        root
            .walkTopDown()
            .filter { it.extension == "kt" && pattern.matches(it.name) }
            .forEach { file ->
                scanned++
                val relative = file.relativeTo(root).path.replace('\\', '/')
                val exempt = relative in exemptions
                val viewModels = distinctViewModelImports(file)
                if (viewModels.size <= 1) {
                    if (exempt) {
                        violations.add(
                            "$relative is in $exemptionsFile but imports ${viewModels.size} ViewModel(s) — remove from exemptions.",
                        )
                    }
                    return@forEach
                }
                if (!exempt) {
                    violations.add(
                        "$relative imports ${viewModels.size} ViewModels: ${viewModels.sorted().joinToString(", ")}\n" +
                            "    Screens must depend on at most one ViewModel. Aggregate the UseCases this screen\n" +
                            "    needs inside a single ViewModel, or add the path to $exemptionsFile if a legacy\n" +
                            "    multi-VM screen needs a follow-up refactor.",
                    )
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Screen ↔ ViewModel Check Failed (${violations.size} violation(s)):\n\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }
        logger.lifecycle(
            "Screen ↔ ViewModel check passed: $scanned file(s) scanned, ${exemptions.size} exemption(s) tracked.",
        )
    }

    private fun readExemptions(): Set<String> {
        if (exemptionsFile.isBlank()) return emptySet()
        val file = File(exemptionsFile)
        if (!file.exists()) return emptySet()
        return file
            .readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Returns the distinct ViewModel "logical names" imported from the
     * usecase package. `Default` prefix is stripped so impl/interface
     * imports collapse to one.
     */
    private fun distinctViewModelImports(file: File): Set<String> =
        file
            .readLines()
            .map(String::trim)
            .filter { it.startsWith("import com.chriscartland.garage.usecase.") }
            .map { it.removePrefix("import ").substringBefore(';').trim() }
            .filter { it.endsWith("ViewModel") }
            .map { it.substringAfterLast('.') }
            .map { if (it.startsWith("Default")) it.removePrefix("Default") else it }
            .toSet()
}
