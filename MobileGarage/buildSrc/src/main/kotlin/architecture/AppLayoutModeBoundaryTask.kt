package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Forbids direct reads of `LocalAppWindowSizeClass.current` outside the
 * single source-of-truth file (`AppLayoutMode.kt`).
 *
 * The contract: adaptive-layout decisions in this app must be expressed
 * as queries against [AppLayoutMode] (via `currentAppLayoutMode()` or by
 * taking an `AppLayoutMode` parameter), not by recomputing them from raw
 * `LocalAppWindowSizeClass` values at every consumer.
 *
 * Why this matters: PR #704's first iteration had four scattered reads of
 * `LocalAppWindowSizeClass.current.widthSizeClass` (bottom nav visibility,
 * bottom nav highlight, two `entry<>` blocks). Adding a screen, changing
 * the activation threshold, or introducing a third layout mode meant
 * editing every site and remembering the merge rule (`History` → `Home`
 * on wide) at each one. The sealed `AppLayoutMode` centralizes both
 * decisions; this lint stops consumers from drifting back to raw reads.
 *
 * Banned patterns:
 *   `LocalAppWindowSizeClass.current.widthSizeClass`
 *   `LocalAppWindowSizeClass.current.heightSizeClass`
 *   `LocalAppWindowSizeClass.current` (any property access on it)
 *
 * Exempt files:
 *   - `AppLayoutMode.kt` — the canonical reader (`currentAppLayoutMode()`).
 *   - `AppWindowSizeClass.kt` — defines the local + provider; constructs
 *     and provides values, doesn't read consumer-side.
 */
abstract class AppLayoutModeBoundaryTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @get:Input
    var exemptFileNames: List<String> = listOf(
        "AppLayoutMode.kt",
        "AppWindowSizeClass.kt",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        // Match any read of `.current` on the local. Allows mentioning the
        // local symbol in comments, KDoc, and import lines (handled
        // separately below).
        val accessPattern = Regex("""\bLocalAppWindowSizeClass\.current\b""")

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name !in exemptFileNames }
                .forEach { file ->
                    val content = file.readText()
                    if ("LocalAppWindowSizeClass" !in content) return@forEach

                    val relativePath = file.relativeTo(dir).path
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") ||
                            trimmed.startsWith("*") ||
                            trimmed.startsWith("import ")
                        ) {
                            return@forEachIndexed
                        }
                        if (accessPattern.containsMatchIn(trimmed)) {
                            violations.add("$relativePath:${index + 1}: $trimmed")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("AppLayoutModeBoundary check FAILED: ")
                    append(violations.size)
                    append(" direct read(s) of `LocalAppWindowSizeClass.current`.\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Adaptive-layout decisions must go through `AppLayoutMode`,\n")
                    append("not raw `LocalAppWindowSizeClass.current` reads. The sealed\n")
                    append("`AppLayoutMode` centralizes the activation threshold and the\n")
                    append("merge rules (e.g. `History` → `Home` on wide); scattered raw\n")
                    append("reads are how those rules drift apart between consumers.\n\n")
                    append("Fix:\n")
                    append("  - In a Composable, call `currentAppLayoutMode()` and pattern-\n")
                    append("    match on the returned `AppLayoutMode`.\n")
                    append("  - In a non-Composable, take `AppLayoutMode` as a parameter.\n")
                    append("  - For the existence of a tab in nav: `mode.visibleTabs`.\n")
                    append("  - For the canonical screen of a back-stack entry under merge\n")
                    append("    rules: `mode.canonicalScreen(currentScreen)`.\n\n")
                    append("If your read genuinely belongs in the source-of-truth file\n")
                    append("(`AppLayoutMode.kt` or `AppWindowSizeClass.kt`), the file is\n")
                    append("already exempt — confirm the file name in the violation matches.\n")
                },
            )
        }

        logger.lifecycle("AppLayoutModeBoundary check passed.")
    }
}
