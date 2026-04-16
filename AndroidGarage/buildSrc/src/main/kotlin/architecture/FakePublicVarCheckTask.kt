package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Bans public mutable state on `Fake*` and `InMemory*` test doubles (ADR-017 Rule 5).
 *
 * Public mutable state on test doubles lets tests write whenever, gives no
 * single call site to grep for mutations, and makes test ordering load-bearing.
 *
 * `Fake*` is the primary naming for test doubles. `InMemory*` is used for real
 * implementations backed by collections that could ship in production (per
 * ADR-017 naming bonus); they are still subject to Rule 5 because their
 * counter / call-tracking fields exist for tests.
 *
 * Two patterns flagged:
 *
 * 1. Public `var` properties.
 *    Allowed: `private var`, `var ... private set`, `val`.
 *    Use `setX()` methods backed by `private var` instead.
 *
 * 2. Public `val xs = mutableListOf<...>()` (or `mutableMapOf`, `mutableSetOf`).
 *    Even though the field is `val`, the collection itself is mutable —
 *    tests can `xs.add(...)`, `xs.clear()`, etc.
 *    Use the call-list pattern: `private val _xs = mutableListOf<...>()` +
 *    `val xs: List<...> get() = _xs`.
 */
abstract class FakePublicVarCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    private val fileNameRegex = Regex("(Fake|InMemory).*\\.kt")

    /** Matches a property declaration `    var name...` (4-space indent, class-level). */
    private val varPattern = Regex("""^\s{4}var\s+\w+""")

    /** Matches `    val name = mutableListOf/MapOf/SetOf(...)` at class level. */
    private val mutableCollectionPattern =
        Regex("""^\s{4}val\s+\w+\s*=\s*mutable(List|Map|Set)Of""")

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
                        checkVarLine(file, dir, lines, index, line, violations)
                        checkMutableCollectionLine(file, dir, index, line, violations)
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Fake Public Var Check Failed (${violations.size} violation(s)):\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }
        logger.lifecycle("Fake public var check passed: no unguarded public mutable state on Fake*/InMemory* classes.")
    }

    private fun checkVarLine(
        file: File,
        dir: File,
        lines: List<String>,
        index: Int,
        line: String,
        violations: MutableList<String>,
    ) {
        if (!varPattern.containsMatchIn(line)) return
        if (line.contains("private")) return
        // Allow `var x = 0` followed by `        private set` on next line.
        val nextLine = lines.getOrNull(index + 1)?.trim()
        if (nextLine == "private set") return
        violations.add(
            "${file.relativeTo(dir).path}:${index + 1}: " +
                "public `var` on a Fake*/InMemory* class is banned (ADR-017 Rule 5). " +
                "Use `private var` + `setX()` method, or add `private set`. " +
                "Line: ${line.trim()}",
        )
    }

    private fun checkMutableCollectionLine(
        file: File,
        dir: File,
        index: Int,
        line: String,
        violations: MutableList<String>,
    ) {
        if (!mutableCollectionPattern.containsMatchIn(line)) return
        if (line.contains("private")) return
        violations.add(
            "${file.relativeTo(dir).path}:${index + 1}: " +
                "public `val xs = mutableListOf(...)` on a Fake*/InMemory* class is banned (ADR-017 Rule 5). " +
                "The collection is mutable even though the field is `val`. " +
                "Use call-list pattern: `private val _xs = mutableListOf<...>(); " +
                "val xs: List<...> get() = _xs`. " +
                "Line: ${line.trim()}",
        )
    }
}
