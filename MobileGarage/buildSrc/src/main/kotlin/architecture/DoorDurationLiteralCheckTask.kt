package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * The door's slide duration is a shared product decision —
 * `DoorAnimation.ANIMATION_DURATION_SECONDS` in `:domain`, layer 1 ("Spec —
 * Tier 1, fully provable") of docs/UI_FIDELITY_TIERS.md § animation. This
 * keeps every door view DERIVING it rather than re-typing it: a numeric
 * literal handed to `tween(`, `durationMillis =`, `Duration.ofSeconds(` or
 * `Duration.ofMillis(` in a door view is a violation. Spring settles are
 * deliberately NOT covered — the doc calls their feel Tier-3 native taste,
 * and their parameters are named constants, not durations.
 *
 * Two scope-sanity rules so the check cannot pass vacuously: every listed file
 * must exist and animate (`animateTo(` or `Duration.of`), and must reference
 * `DoorAnimation` — the positive presence of the shared spec. A door view that
 * moved, or stopped animating, fails here rather than silently leaving the
 * list stale. (The iOS twin is scripts/check-ios-door-duration-literals.sh.)
 */
abstract class DoorDurationLiteralCheckTask : DefaultTask() {
    /** Absolute paths of the Kotlin door views (phone and Wear). */
    @get:Input
    var doorViewFiles: List<String> = emptyList()

    @TaskAction
    fun check() {
        val literalDuration = Regex("""tween\(\s*[0-9]|durationMillis\s*=\s*[0-9]|Duration\.of(Seconds|Millis)\(\s*[0-9]""")
        val animates = Regex("""animateTo\(|Duration\.of""")
        val problems = mutableListOf<String>()
        if (doorViewFiles.isEmpty()) {
            problems += "no door views listed — the check covers nothing"
        }
        for (path in doorViewFiles) {
            val file = File(path)
            if (!file.exists()) {
                problems += "$path: missing — the door view moved; update the list in build.gradle.kts"
                continue
            }
            val text = file.readText()
            if (!animates.containsMatchIn(text)) {
                problems += "${file.name}: no longer animates (no animateTo/Duration.of) — is it still a door view?"
            }
            if (!text.contains("DoorAnimation")) {
                problems += "${file.name}: does not reference DoorAnimation — the shared spec is the only source of the slide"
            }
            file.readLines().forEachIndexed { index, line ->
                if (literalDuration.containsMatchIn(line)) {
                    problems +=
                        "${file.name}:${index + 1}: ${line.trim()}\n" +
                        "    A literal duration in a door view. Derive it from DoorAnimation.ANIMATION_DURATION_SECONDS " +
                        "(docs/UI_FIDELITY_TIERS.md § animation, layer 1)."
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Door duration literal check failed (${problems.size} problem(s)):\n\n" +
                    problems.joinToString("\n\n") { "  $it" },
            )
        }
        logger.lifecycle(
            "Door duration literal check passed: ${doorViewFiles.size} door view(s) derive the slide from DoorAnimation.",
        )
    }
}
