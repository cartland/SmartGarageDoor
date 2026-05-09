package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces that `Modifier.widthIn(max = ...)` for the route-level content
 * cap is applied **only at `RouteContent.kt`** — the single source of
 * horizontal layout per ADR / SPACING_PLAN.md.
 *
 * Why this matters: applying `widthIn(max = ...)` at a child Composable
 * inside the route would either (a) double-cap (if narrower than the
 * route cap) or (b) be a no-op (if wider) — both surface as silent
 * tablet-only layout drift that phone screenshot tests miss.
 *
 * Detection: scans every `*.kt` file under [sourceDirs] for the literal
 * string `widthIn(max`. Allows the configured exempt files; fails for
 * any other use site.
 *
 * Note: `widthIn(min = ...)` is allowed everywhere — only the `max =`
 * variant is the route-cap idiom we restrict.
 */
abstract class ContentWidthCapCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * File names that are allowed to call `widthIn(max = ...)`.
     *
     * Default exempts:
     * - `RouteContent.kt` — the canonical single-pane route wrapper.
     * - `AnimatableGarageDoor.kt` — uses `widthIn(max)` to size a preview
     *   icon, not for route-level layout. Sub-component dimension, not
     *   the rule this check enforces.
     * - `HomeDashboardContent.kt` — the wide-screen two-pane route
     *   wrapper. Each pane is conceptually a route-level slot and gets
     *   its own per-pane `widthIn(max = ContentWidth.Standard)` cap so
     *   neither sprawls past readable width on very wide windows. Same
     *   role as `RouteContent` for single-pane, just expressed twice.
     */
    @get:Input
    var exemptFileNames: List<String> = listOf(
        "RouteContent.kt",
        "DashboardRouteContent.kt",
        "AnimatableGarageDoor.kt",
        "HomeDashboardContent.kt",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val widthInMaxPattern = Regex("""\bwidthIn\s*\(\s*max\s*=""")

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name !in exemptFileNames }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        // Skip comment / KDoc lines so prose mentioning
                        // `widthIn(max = ...)` isn't a false positive.
                        val trimmed = line.trimStart()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                        if (widthInMaxPattern.containsMatchIn(line)) {
                            violations.add("$relativePath:${index + 1}: ${line.trim()}")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("ContentWidthCap check FAILED: ")
                    append(violations.size)
                    append(" disallowed `widthIn(max = ...)` use(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("`widthIn(max = ...)` for route-level content capping is the\n")
                    append("single responsibility of RouteContent.kt. Children inside a\n")
                    append("route must NOT re-apply the cap — they receive the capped\n")
                    append("modifier from RouteContent's lambda.\n\n")
                    append("Fix:\n")
                    append("  - If the file is a screen Composable: receive the modifier\n")
                    append("    from the route wrapper instead of declaring a cap inline.\n")
                    append("  - If the file legitimately needs `widthIn(max)` for a\n")
                    append("    sub-component (e.g. a centered confirmation card), add it\n")
                    append("    to ContentWidthCapCheckTask.exemptFileNames in build.gradle.kts\n")
                    append("    with a comment explaining why.\n\n")
                    append("See AndroidGarage/docs/SPACING_PLAN.md (Large-screen extension).\n")
                },
            )
        }

        logger.lifecycle("ContentWidthCap check passed: only RouteContent.kt caps content width.")
    }
}
