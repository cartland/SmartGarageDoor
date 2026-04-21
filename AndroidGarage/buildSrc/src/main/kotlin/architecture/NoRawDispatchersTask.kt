package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-005: ViewModels and UseCases must inject `DispatcherProvider`,
 * not reference `Dispatchers.IO`, `Dispatchers.Main`, `Dispatchers.Default`,
 * or `Dispatchers.Unconfined` directly.
 *
 * Rationale: hardcoded dispatchers are untestable. Production gets real
 * dispatchers; tests get `TestDispatcher` (virtual time). A single file
 * that references `Dispatchers.IO` directly breaks the chain — its tests
 * can't use virtual time, flake timing assertions, and the rule is
 * silently eroded.
 *
 * Scope: `*ViewModel*.kt` and `*UseCase*.kt` in `androidApp/` +
 * `usecase/` + `presentation-model/` source trees. The
 * `DefaultDispatcherProvider` implementation is explicitly allowlisted
 * because it IS the provider — it necessarily references raw
 * Dispatchers.
 *
 * This lint had zero violations when added, so it locks the current
 * state rather than prescribing a migration.
 */
abstract class NoRawDispatchersTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @get:Input
    var allowedFilePatterns: List<String> = listOf(
        // The provider implementation is the only legitimate site of
        // raw Dispatchers references.
        "DefaultDispatcherProvider\\.kt",
    )

    @get:Input
    var targetFilePatterns: List<String> = listOf(
        ".*ViewModel.*\\.kt",
        ".*UseCase.*\\.kt",
    )

    @TaskAction
    fun check() {
        val pattern = Regex("""\bDispatchers\.(IO|Main|Default|Unconfined)\b""")
        val allowedRegexes = allowedFilePatterns.map { Regex(it) }
        val targetRegexes = targetFilePatterns.map { Regex(it) }

        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> targetRegexes.any { it.matches(file.name) } }
                .filter { file -> allowedRegexes.none { it.matches(file.name) } }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        // Skip comments and KDoc mentions.
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                        if (pattern.containsMatchIn(line)) {
                            violations.add("$relativePath:${index + 1}: ${line.trim()}")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoRawDispatchers check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ViewModels and UseCases must inject `DispatcherProvider` (ADR-005),\n")
                    append("never reference `Dispatchers.IO`/`Main`/`Default`/`Unconfined` directly.\n\n")
                    append("Fix:\n")
                    append("  - Add `dispatchers: DispatcherProvider` as a constructor parameter.\n")
                    append("  - Use `dispatchers.io`, `dispatchers.main`, `dispatchers.default` at call sites.\n")
                    append("  - The AppComponent already provides `DispatcherProvider` as a singleton.\n\n")
                    append("Rationale: tests inject `TestDispatcher` for virtual-time assertions.\n")
                    append("A hardcoded Dispatchers.IO silently flakes tests and erodes the rule.\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-005).\n")
                },
            )
        }

        logger.lifecycle("NoRawDispatchers check passed: no ViewModel/UseCase files reference raw Dispatchers.")
    }
}
