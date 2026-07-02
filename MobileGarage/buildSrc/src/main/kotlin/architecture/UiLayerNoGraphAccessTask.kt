package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces the hard rule: **UI-triggered work goes through the ViewModel.**
 *
 * UI-layer code (Composables / screen state under `androidApp/.../ui/`) must not
 * reach a `UseCase` or `Repository` directly off the DI `component`. Those are
 * the VM's collaborators; a screen depends only on its one ViewModel (ADR-026),
 * and the VM holds the UseCases. The only code allowed to call a UseCase
 * directly is code with **no UI lifecycle** — FCM handlers, `AppStartup`,
 * app-scoped `@Singleton`s — and that code lives outside `ui/` (see ADR-033).
 *
 * Detection is the **member-access** pattern `component.<name>UseCase` /
 * `component.<name>Repository`, NOT imports — a `:usecase`-module *data class*
 * like `ButtonHealthDisplay` is legitimately imported by UI, so an import-based
 * check would false-positive. Pulling a collaborator off `component` is the
 * precise "reaching past the VM into the graph" anti-pattern.
 *
 * [exemptions] grandfathers known pre-rule violations (relative paths under the
 * scanned dir) while they're refactored through their ViewModels; the goal is an
 * empty list. New violations are blocked immediately.
 */
abstract class UiLayerNoGraphAccessTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /** Relative `.kt` paths (under a scanned dir) temporarily allowed to violate. */
    @get:Input
    @get:Optional
    var exemptions: List<String> = emptyList()

    private val graphAccess = Regex("""\bcomponent\s*\.\s*[A-Za-z0-9_]*(UseCase|Repository)\b""")

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val matchedExemptions = mutableSetOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(dir).path
                    val exempt = relativePath in exemptions
                    file.readLines().forEachIndexed { index, line ->
                        if (graphAccess.containsMatchIn(line)) {
                            if (exempt) {
                                matchedExemptions.add(relativePath)
                            } else {
                                violations.add("  $relativePath:${index + 1}: ${line.trim()}")
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("UI-layer code reaches a UseCase/Repository directly off `component` (ADR-033):")
                    appendLine()
                    violations.forEach { appendLine(it) }
                    appendLine()
                    appendLine("Route the action through the screen's ViewModel instead — the UI depends on")
                    appendLine("its one ViewModel (ADR-026), which holds the UseCase. Only UI-less code")
                    appendLine("(FCM handlers, AppStartup, @Singleton) may call a UseCase directly, and it")
                    appendLine("lives outside ui/. ${violations.size} violation(s).")
                },
            )
        }

        // Stale-exemption guard (mirrors screen-viewmodel-exemptions): if an entry
        // no longer violates, it must be removed so the list can't accumulate.
        val stale = exemptions.toSet() - matchedExemptions
        if (stale.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Stale UI-layer graph-access exemption(s) — these files no longer reach")
                    appendLine("`component.*UseCase`/`*Repository`, so remove them from the exemptions list:")
                    appendLine()
                    stale.sorted().forEach { appendLine("  $it") }
                },
            )
        }

        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle(
            "UI layer no direct graph access: $fileCount files scanned, " +
                "0 violations (${exemptions.size} grandfathered).",
        )
    }
}
