package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Bans public `var` properties on `Fake*` test doubles (ADR-017 Rule 5).
 *
 * Public mutable state on fakes lets tests write whenever, gives no single
 * call site to grep for mutations, and makes test ordering load-bearing.
 *
 * Allowed patterns inside `Fake*.kt`:
 * - `private var x = ...` (mutated only by the fake itself)
 * - `var x = ...` followed on the next line by `private set` (counters / last-call)
 * - `val x = ...` (immutable)
 *
 * Forbidden:
 * - `var x: T = ...` (no `private`, no `private set`)
 *
 * Configure result responses with `setX()` methods backed by `private var` instead.
 */
abstract class FakePublicVarCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    private val fileNameRegex = Regex("Fake.*\\.kt")

    /** Matches a property declaration `    var name...` (4-space indent, class-level). */
    private val varPattern = Regex("""^\s{4}var\s+\w+""")

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" && fileNameRegex.matches(it.name) }
                .forEach { file ->
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (!varPattern.containsMatchIn(line)) return@forEachIndexed
                        if (line.contains("private")) return@forEachIndexed
                        // Allow `var x = 0` followed by `        private set` on next line.
                        val nextLine = lines.getOrNull(index + 1)?.trim()
                        if (nextLine == "private set") return@forEachIndexed
                        violations.add(
                            "${file.relativeTo(dir).path}:${index + 1}: " +
                                "public `var` on a Fake* class is banned (ADR-017 Rule 5). " +
                                "Use `private var` + `setX()` method, or add `private set`. " +
                                "Line: ${line.trim()}",
                        )
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Fake Public Var Check Failed (${violations.size} violation(s)):\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }
        logger.lifecycle("Fake public var check passed: no unguarded public `var` on Fake* classes.")
    }
}
