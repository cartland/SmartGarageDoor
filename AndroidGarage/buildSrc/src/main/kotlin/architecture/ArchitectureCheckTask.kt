package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Enforces the module dependency graph at build time.
 *
 * Each module declares which other modules it may depend on.
 * Any undeclared project dependency fails the build, preventing
 * accidental layer violations (e.g., usecase depending on data).
 *
 * Modules not listed are unchecked. Modules with "*" allow all deps.
 * Add new modules here as they're created.
 */
abstract class ArchitectureCheckTask : DefaultTask() {
    /**
     * Map of module name → allowed dependency module names.
     * Use "*" to allow all dependencies (for app modules).
     * Set at configuration time from build.gradle.kts.
     */
    @get:Input
    var allowedDependencies: Map<String, List<String>> = mapOf(
        ":domain" to listOf(),
        ":data" to listOf(":domain"),
        ":data-local" to listOf(":domain", ":data"),
        ":usecase" to listOf(":domain"),
        ":presentation-model" to listOf(":domain"),
        ":androidApp" to listOf("*"),
        ":android-screenshot-tests" to listOf("*"),
        ":macrobenchmark" to listOf("*"),
        ":shared" to listOf("*"),
    )

    /**
     * Actual module dependencies, captured at configuration time.
     * Each entry: "moduleName -> depPath"
     */
    @get:Input
    var moduleDependencies: List<String> = emptyList()

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (entry in moduleDependencies) {
            val (moduleName, depPath) = entry.split(" -> ")
            val allowed = allowedDependencies[moduleName] ?: continue
            if (allowed.contains("*")) continue

            if (!allowed.contains(depPath)) {
                violations.add(
                    "Module '$moduleName' depends on '$depPath' " +
                        "which is not in its allowed list: $allowed",
                )
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture Check Failed:\n" +
                    violations.distinct().joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Architecture check passed: module dependency graph is clean.")
    }
}
