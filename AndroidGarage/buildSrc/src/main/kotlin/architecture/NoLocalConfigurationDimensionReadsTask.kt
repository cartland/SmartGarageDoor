package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Forbids direct reads of window dimensions from `LocalConfiguration.current`
 * outside the single allowed source.
 *
 * The contract: adaptive layout decisions in this app must read from
 * `LocalAppWindowSizeClass` (see `androidApp/.../ui/AppWindowSizeClass.kt`),
 * which is computed once at the app's Compose root via the M3
 * `calculateWindowSizeClass(activity)` API.
 *
 * Why this matters:
 *  - Foldable unfold and multi-window resize change the window size class
 *    via Activity reconfiguration paths that don't always update
 *    `LocalConfiguration.screenWidthDp` synchronously on all OEM Android
 *    builds. The M3 size-class API hides that variability.
 *  - Two-pane / single-pane branching needs to be deterministic across the
 *    whole UI tree; "every screen reads the same Composition local" is a
 *    stronger guarantee than "every screen reads `LocalConfiguration` and
 *    we hope it's the same value."
 *  - Screens stay layout-agnostic: layout decisions live at one wrapper
 *    layer (`RouteContent` today; `RouteContent` vs. `TwoPaneRouteContent`
 *    tomorrow), not scattered through every screen file.
 *
 * Banned patterns:
 *   `LocalConfiguration.current.screenWidthDp`
 *   `LocalConfiguration.current.screenHeightDp`
 *   `LocalConfiguration.current.smallestScreenWidthDp`
 *   `LocalConfiguration.current.densityDpi`
 *
 * Allowed reads:
 *   - `LocalConfiguration.current.uiMode` — dark mode is not a size signal
 *   - `LocalConfiguration.current.fontScale` — accessibility / typography
 *   - Anything inside `AppWindowSizeClass.kt` itself (the source of truth)
 *   - Test sources under src/test or src/androidTest — fixtures may
 *     legitimately stub these
 */
abstract class NoLocalConfigurationDimensionReadsTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Files exempt from the check. Add the source-of-truth file here so its
     * legitimate use of the underlying API doesn't trip the check.
     */
    @get:Input
    var exemptFileNames: List<String> = listOf("AppWindowSizeClass.kt")

    /**
     * Banned property reads on `LocalConfiguration.current`. Each entry is
     * matched as a substring on the trimmed source line.
     */
    @get:Input
    var bannedSuffixes: List<String> = listOf(
        ".screenWidthDp",
        ".screenHeightDp",
        ".smallestScreenWidthDp",
        ".densityDpi",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name !in exemptFileNames }
                .forEach { file ->
                    val content = file.readText()
                    if ("LocalConfiguration" !in content) return@forEach
                    val lines = file.readLines()
                    val relativePath = file.relativeTo(dir).path
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") ||
                            trimmed.startsWith("*") ||
                            trimmed.startsWith("import ")
                        ) {
                            return@forEachIndexed
                        }
                        if ("LocalConfiguration" !in trimmed) return@forEachIndexed
                        val hit = bannedSuffixes.any { suffix -> suffix in trimmed }
                        if (hit) {
                            violations.add("$relativePath:${index + 1}: $trimmed")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoLocalConfigurationDimensionReads check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Adaptive layout reads must come from `LocalAppWindowSizeClass`,\n")
                    append("not `LocalConfiguration.current.<dimension>` directly. See\n")
                    append("`androidApp/.../ui/AppWindowSizeClass.kt` for the API and the\n")
                    append("rationale.\n\n")
                    append("Fix:\n")
                    append("  - Add `import com.chriscartland.garage.ui.LocalAppWindowSizeClass`\n")
                    append("  - Replace `LocalConfiguration.current.screenWidthDp` etc. with\n")
                    append("    `LocalAppWindowSizeClass.current.widthSizeClass` (or\n")
                    append("    `.heightSizeClass`).\n")
                    append("  - Branch on `WindowWidthSizeClass.Compact` / `Medium` / `Expanded`\n")
                    append("    instead of raw dp comparisons.\n\n")
                    append("If you're touching the source-of-truth file itself\n")
                    append("(`AppWindowSizeClass.kt`), it's already exempt — confirm the file\n")
                    append("name in the violation matches.\n")
                },
            )
        }

        logger.lifecycle("NoLocalConfigurationDimensionReads check passed.")
    }
}
