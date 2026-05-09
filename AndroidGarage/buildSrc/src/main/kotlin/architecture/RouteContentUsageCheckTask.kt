package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces that every nav-graph entry in `Main.kt` wraps its content in
 * either `RouteContent { ... }` (single-pane wrapper) or
 * `DashboardRouteContent { ... }` (wide-screen dashboard wrapper) — the
 * single source of horizontal layout (padding + max-width + centering)
 * introduced in 2.13.0 (PR #682) and extended in 2.14.x for the wide
 * Home dashboard.
 *
 * Why this matters: both wrappers apply `Modifier.widthIn(max = ...)` and
 * horizontal centering via `Box(TopCenter)`. A new `entry<Screen.X>` block
 * that skips both wrappers would silently break the cap on that route, and
 * the regression would only be visible on tablets — easy to miss in
 * phone-only screenshot tests.
 *
 * Detection (heuristic): scans `Main.kt` for `entry<Screen.X>` lines and
 * verifies that within the next [lookAheadLines] lines a route-wrapper
 * call appears. The window is large enough to cover the entry's body
 * (including a wide-vs-narrow `if/else` branch with one wrapper per arm)
 * without crossing into the next entry block.
 */
abstract class RouteContentUsageCheckTask : DefaultTask() {
    @get:Input
    var mainKtPath: String = ""

    /**
     * How many lines to scan after each `entry<...>` line for a route
     * wrapper call. 25 covers the current shape including the wide-vs-
     * narrow branch with one wrapper per arm in the Home / History
     * entries (~5 lines for narrow path + ~5 lines for wide path +
     * surrounding `if`/`else` braces).
     */
    @get:Input
    var lookAheadLines: Int = 25

    @TaskAction
    fun check() {
        val file = File(mainKtPath)
        if (!file.exists()) {
            throw GradleException("RouteContentUsage check: $mainKtPath not found.")
        }

        val lines = file.readLines()
        val entryPattern = Regex("""\bentry<Screen\.[A-Za-z]+>\s*\{""")
        // Accept any of the route wrappers / dispatch helpers known to
        // own a route's horizontal layout:
        //   `RouteContent {` — single-pane wrapper.
        //   `DashboardRouteContent {` — wide-screen wrapper.
        //   `routeFor(` / `RouteEntryFor(` — the dispatch table that
        //     internally selects the right wrapper based on
        //     `AppLayoutMode`. The dispatch table itself is checked at
        //     `AppLayoutMode` consumers via `checkAppLayoutModeBoundary`,
        //     so trusting the helper here is safe.
        val routeContentPattern = Regex(
            """\b(?:RouteContent|DashboardRouteContent)\s*\{|\b(?:routeFor|RouteEntryFor)\s*\(""",
        )

        val violations = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            if (!entryPattern.containsMatchIn(line)) return@forEachIndexed
            // Check the entry line itself first (single-line form like
            // `entry<Screen.X> { routeFor(Screen.X) }` puts the wrapper
            // on the same line), then look ahead for multi-line bodies.
            if (routeContentPattern.containsMatchIn(line)) return@forEachIndexed
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
