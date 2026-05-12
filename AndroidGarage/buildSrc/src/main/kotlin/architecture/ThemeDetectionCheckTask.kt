package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Forbids `isSystemInDarkTheme()` calls outside the theme module.
 *
 * Theme detection (light vs dark) is the theme module's responsibility.
 * If a screen Composable reaches for `isSystemInDarkTheme()` directly,
 * it's reimplementing what `AppTheme` already resolves and propagates
 * via `MaterialTheme.colorScheme.*` — duplicating the decision and
 * making future theme changes (e.g. dynamic-color opt-in, custom
 * dark-color overrides) leak into screen code.
 *
 * Allowed locations: any file under `androidApp/src/main/.../ui/theme/`.
 * That covers `Theme.kt` (where `AppTheme` reads it as a default
 * parameter) and `PreviewSurface.kt` (where the preview wrapper reads
 * it to mirror the inner `AppTheme`'s configuration).
 *
 * This is the most portable rule from battery-butler's
 * `ThemeLayerCheckTask`. The other two rules — forbidden raw `Color(0x...)`
 * literals and forbidden `import androidx.compose.ui.graphics.Color` —
 * are already covered (the literals by [HardcodedColorCheckTask] with
 * its allowed-files list; the import is too broad for SmartGarageDoor
 * because canvas/drawing files like `GarageDoorCanvas.kt` legitimately
 * need `Color` for rendering primitives).
 *
 * Ported from battery-butler 2026-05-12. Codebase clean at port time.
 *
 * Suppression: add `// @ThemeDetectionExempt` on the line if a
 * non-theme file legitimately needs this (rare).
 */
abstract class ThemeDetectionCheckTask : DefaultTask() {
    /**
     * Source directories to scan, e.g. `androidApp/src/main/java`.
     */
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Path-fragment substrings that mark a file as inside the theme
     * module (and therefore allowed to call `isSystemInDarkTheme()`).
     * For SmartGarageDoor, this is `/ui/theme/`.
     */
    @get:Input
    var themePathFragments: List<String> = emptyList()

    private val pattern = Regex("""\bisSystemInDarkTheme\s*\(\s*\)""")
    private val exemptComment = "@ThemeDetectionExempt"

    private val skipPathFragments = listOf(
        "/build/",
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
                .filter { f -> skipPathFragments.none { frag -> f.path.contains(frag) } }
                .filter { f -> themePathFragments.none { frag -> f.path.contains(frag) } }
                .forEach { file ->
                    scanned++
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        if (line.contains(exemptComment)) return@forEachIndexed
                        if (pattern.containsMatchIn(line)) {
                            violations.add(
                                "$relativePath:${index + 1}: ${line.trim()}",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Theme Detection Check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Theme detection (`isSystemInDarkTheme()`) is the theme module's job.\n")
                    append("Read `MaterialTheme.colorScheme.*` from your screen instead — `AppTheme`\n")
                    append("already resolved the decision when it set up the color scheme.\n\n")
                    append("Allowed locations: files under any path containing one of:\n")
                    themePathFragments.forEach { append("  - $it\n") }
                    append("\nSuppress with `// $exemptComment` on the line if unavoidable.\n")
                },
            )
        }

        logger.lifecycle(
            "Theme Detection Check passed: $scanned file(s) scanned outside theme module.",
        )
    }
}
