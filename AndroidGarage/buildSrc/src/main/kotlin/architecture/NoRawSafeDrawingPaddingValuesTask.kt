package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Forbids reading system insets via `WindowInsets.<x>.asPaddingValues()`
 * in app code outside the single source of truth.
 *
 * **The trap this catches:** `WindowInsets.<x>.asPaddingValues()` reads
 * the **raw view-level inset** — it is **NOT consumption-aware**. Upstream
 * `Modifier.consumeWindowInsets(...)` calls do not affect what
 * `asPaddingValues()` returns. So a leaf doing
 * `LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues())`
 * will pick up the full system inset every time, **even if a parent has
 * already padded for it visually**. That causes the double-padding bug
 * shipped in 2.16.12 and fixed in 2.16.13.
 *
 * Only `Modifier.windowInsetsPadding(...)` (and friends like
 * `windowInsetsTopHeight`, `Modifier.consumeWindowInsets(...)`) participate
 * in the modifier-local consumption chain. If a leaf wants
 * **consumption-aware padding**, it must use one of those modifier APIs
 * — or read from a CompositionLocal that the upstream layout publishes
 * (this codebase uses `LocalContentEdgeInsets`).
 *
 * Banned patterns (substring match on trimmed line):
 *   `WindowInsets.safeDrawing.asPaddingValues`
 *   `WindowInsets.systemBars.asPaddingValues`
 *   `WindowInsets.systemGestures.asPaddingValues`
 *   `WindowInsets.displayCutout.asPaddingValues`
 *   `WindowInsets.ime.asPaddingValues`
 *   `WindowInsets.navigationBars.asPaddingValues`
 *   `WindowInsets.statusBars.asPaddingValues`
 *   `WindowInsets.waterfall.asPaddingValues`
 *
 * Allowed reads (exempt by file name):
 *   - `Spacing.kt` — defines the `safeListContentPadding()` helper and the
 *     `LocalContentEdgeInsets` CompositionLocal. The helper reads from the
 *     local (not raw `WindowInsets.safeDrawing`), but it does call
 *     `.asPaddingValues()` on the local's value, which is fine — the local
 *     is a managed bridge.
 *
 * **If you find yourself wanting to bypass this lint**: the right answer
 * is almost always to read from `LocalContentEdgeInsets` (defined in
 * `Spacing.kt`) or to use the `safeListContentPadding()` helper. If a
 * new screen-level scrollable needs a different inset shape (e.g. IME),
 * extend the local to carry the new inset and update the body wrapper
 * in `Main.kt` to publish it.
 */
abstract class NoRawSafeDrawingPaddingValuesTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Files exempt from the check. The bridge file `Spacing.kt` is
     * exempt — it defines the helper that wraps a managed
     * CompositionLocal.
     */
    @get:Input
    var exemptFileNames: List<String> = listOf("Spacing.kt")

    /**
     * Banned `WindowInsets.<inset>.asPaddingValues` substrings.
     */
    @get:Input
    var bannedPatterns: List<String> = listOf(
        "WindowInsets.safeDrawing.asPaddingValues",
        "WindowInsets.systemBars.asPaddingValues",
        "WindowInsets.systemGestures.asPaddingValues",
        "WindowInsets.displayCutout.asPaddingValues",
        "WindowInsets.ime.asPaddingValues",
        "WindowInsets.navigationBars.asPaddingValues",
        "WindowInsets.statusBars.asPaddingValues",
        "WindowInsets.waterfall.asPaddingValues",
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
                    if ("asPaddingValues" !in content) return@forEach
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
                        val hit = bannedPatterns.any { pattern -> pattern in trimmed }
                        if (hit) {
                            violations.add("$relativePath:${index + 1}: $trimmed")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoRawSafeDrawingPaddingValues check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("`WindowInsets.<x>.asPaddingValues()` reads the raw view-level inset.\n")
                    append("It is NOT consumption-aware — upstream `Modifier.consumeWindowInsets(...)`\n")
                    append("does NOT affect what it returns. So a leaf using this in `contentPadding`\n")
                    append("will double-pad whenever a parent already pads for the same inset\n")
                    append("(canonical example: 2.16.12 top-padding regression, fixed in 2.16.13).\n\n")
                    append("Fix:\n")
                    append("  - For screen-level scrollables, use `Spacing.safeListContentPadding()`\n")
                    append("    (in `theme/Spacing.kt`). That helper reads `LocalContentEdgeInsets`,\n")
                    append("    which the Scaffold body wrapper in `Main.kt` publishes per layout mode.\n")
                    append("  - For padding modifiers, use `Modifier.windowInsetsPadding(...)` or\n")
                    append("    `Modifier.consumeWindowInsets(...)` — these ARE consumption-aware.\n")
                    append("  - If you need a fresh edge inset that the local doesn't yet carry\n")
                    append("    (e.g. IME), extend `LocalContentEdgeInsets` and publish from the\n")
                    append("    body wrapper. Don't bypass the bridge.\n\n")
                    append("Test fixtures injecting fake insets via `CompositionLocalProvider` are\n")
                    append("allowed — they don't read raw `safeDrawing`. The fixture wrapper\n")
                    append("`PreviewWithSimulatedInsets` in `theme/PreviewSurface.kt` shows the pattern.\n")
                },
            )
        }

        logger.lifecycle("NoRawSafeDrawingPaddingValues check passed.")
    }
}
