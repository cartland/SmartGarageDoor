package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Forbids two specific class-name patterns that cause friction in KMP
 * codebases:
 *
 * 1. **`Ui` in class names** — collides with iOS UIKit naming when the
 *    class is later moved to `commonMain` for KMP sharing. Prefer
 *    `Screen` for state objects (`HomeUiState` → `HomeScreenState`) or
 *    drop the prefix entirely (`UiMessage` → `Message`).
 * 2. **`View` in class names** (excluding `*ViewModel`) — ambiguous
 *    between Android's View system and Compose. Pick a more specific
 *    name (`ViewState` → `ScreenState`, `ViewData` → `DisplayData`).
 *
 * Ported from battery-butler 2026-05-12. The codebase was clean at port
 * time; this task prevents drift.
 *
 * Suppression: add `// @NamingExempt` to the line if a violation is
 * deliberate (e.g. a pre-existing public API that can't be renamed).
 */
abstract class NamingConventionCheckTask : DefaultTask() {
    /**
     * Source directories to scan, e.g. `usecase/src/commonMain/kotlin`.
     * The task walks each recursively for `*.kt` files; test source
     * sets (paths containing `/test/`, `/androidTest/`, `/commonTest/`,
     * `/screenshotTest/`) are skipped so test fixtures can use
     * conventional `*UiState` / `*View` names if needed.
     */
    @get:Input
    var sourceDirs: List<String> = emptyList()

    private data class Rule(
        val id: String,
        val pattern: Regex,
        val message: String,
        val fix: String,
    )

    private val rules = listOf(
        Rule(
            id = "no-ui-in-class-name",
            pattern = Regex("""(class|interface|object)\s+\w*[a-z]Ui[A-Z]\w*"""),
            message = "Class names must not contain 'Ui' — collides with iOS UIKit naming.",
            fix = "Use 'Screen' (for state objects) or drop the prefix. Examples: " +
                "HomeUiState → HomeScreenState, ChatUiMessage → ChatMessage.",
        ),
        Rule(
            id = "no-view-in-class-name",
            pattern = Regex("""(class|interface|object)\s+\w*View(?!Model)\w*"""),
            message = "Class names must not contain 'View' (except *ViewModel) — " +
                "ambiguous between Android View system and Compose.",
            fix = "Use a more specific name. Examples: ViewState → ScreenState, " +
                "ViewData → DisplayData.",
        ),
    )

    private val suppressionMarker = "@NamingExempt"

    private val testPathFragments = listOf(
        "/test/",
        "/androidTest/",
        "/commonTest/",
        "/jvmTest/",
        "/androidUnitTest/",
        "/androidInstrumentedTest/",
        "/screenshotTest/",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        var scanned = 0

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> testPathFragments.none { frag -> file.path.contains(frag) } }
                .forEach { file ->
                    scanned++
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        if (line.contains(suppressionMarker)) return@forEachIndexed
                        for (rule in rules) {
                            if (rule.pattern.containsMatchIn(line)) {
                                violations.add(
                                    "$relativePath:${index + 1} [${rule.id}] " +
                                        "${rule.message}\n        Fix: ${rule.fix}",
                                )
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Naming Convention Check FAILED (${violations.size} violation(s)):\n\n")
                    violations.forEach { append("  - $it\n\n") }
                    append(
                        "Suppress with `// @NamingExempt` on the line if the violation " +
                            "is deliberate (rare).\n",
                    )
                },
            )
        }
        logger.lifecycle("Naming Convention Check passed: $scanned file(s) scanned.")
    }
}
