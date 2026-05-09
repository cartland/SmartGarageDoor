package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces that every nav-graph entry in `Main.kt` wraps its content in
 * `RouteContent { ... }` — the single source of horizontal layout
 * (padding + max-width + centering) introduced in 2.13.0 (PR #682).
 *
 * Why this matters: `RouteContent` applies (a) `Modifier.widthIn(max =
 * ContentWidth.Standard)` to cap content width on tablets/landscape and
 * (b) horizontal centering via `Box(TopCenter)`. A new `entry<Screen.X>`
 * block that skips the wrapper would silently break the cap on that
 * route, and the regression would only be visible on tablets — easy to
 * miss in phone-only screenshot tests.
 *
 * Detection (heuristic): scans `Main.kt` for `entry<Screen.X>` lines and
 * verifies that within the next [lookAheadLines] lines, a `RouteContent {`
 * call appears. The window is large enough to cover the entry's body
 * without crossing into the next entry block.
 */
abstract class RouteContentUsageCheckTask : DefaultTask() {
    @get:Input
    var mainKtPath: String = ""

    /**
     * How many lines to scan after each `entry<...>` line for a
     * `RouteContent {` call. 15 is comfortable for the current shape
     * (~5 lines per entry block including the route's own param list).
     */
    @get:Input
    var lookAheadLines: Int = 15

    @TaskAction
    fun check() {
        val file = File(mainKtPath)
        if (!file.exists()) {
            throw GradleException("RouteContentUsage check: $mainKtPath not found.")
        }

        val lines = file.readLines()
        val entryPattern = Regex("""\bentry<Screen\.[A-Za-z]+>\s*\{""")
        val routeContentPattern = Regex("""\bRouteContent\s*\{""")

        val violations = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            if (!entryPattern.containsMatchIn(line)) return@forEachIndexed
            // Look ahead for RouteContent { within the window.
            val window = lines.subList(
                index + 1,
                minOf(index + 1 + lookAheadLines, lines.size),
            )
            if (window.none { routeContentPattern.containsMatchIn(it) }) {
                violations.add("${file.name}:${index + 1}: ${line.trim()}")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("RouteContentUsage check FAILED: ")
                    append(violations.size)
                    append(" entry block(s) without RouteContent.\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Every NavDisplay entry in Main.kt must wrap its content in\n")
                    append("RouteContent { routeModifier -> ... }. RouteContent is the\n")
                    append("single source of horizontal layout — it applies the\n")
                    append("ContentWidth.Standard cap and centers content on tablets/landscape.\n\n")
                    append("Fix:\n")
                    append("  entry<Screen.X> {\n")
                    append("      RouteContent { routeModifier ->\n")
                    append("          XContent(\n")
                    append("              modifier = routeModifier.padding(horizontal = Spacing.Screen),\n")
                    append("              ...\n")
                    append("          )\n")
                    append("      }\n")
                    append("  }\n\n")
                    append("See AndroidGarage/docs/SPACING_PLAN.md (Large-screen extension).\n")
                },
            )
        }

        logger.lifecycle("RouteContentUsage check passed: every entry uses RouteContent.")
    }
}
