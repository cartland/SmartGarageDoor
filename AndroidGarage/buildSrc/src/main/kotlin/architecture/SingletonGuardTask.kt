package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Ensures critical @Provides methods have @Singleton scope.
 *
 * Database and Settings instances crash or corrupt if created more than once.
 * This check catches missing @Singleton annotations before runtime.
 */
abstract class SingletonGuardTask : DefaultTask() {
    @get:Input
    var sourceDir: String = ""

    /**
     * Method name patterns that MUST be annotated with @Singleton.
     * Uses substring matching — "provideAppDatabase" matches "fun provideAppDatabase()".
     */
    @get:Input
    var guardedMethodPatterns: List<String> = listOf(
        "provideAppDatabase",
        "provideAppSettings",
        "provideHttpClient",
    )

    @get:Input
    var scopeAnnotations: List<String> = listOf("@Singleton")

    @TaskAction
    fun check() {
        val dir = File(sourceDir)
        if (!dir.exists()) return

        val violations = mutableListOf<String>()

        dir
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                val relativePath = file.relativeTo(dir).path

                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    val matchedMethod = guardedMethodPatterns.firstOrNull { pattern ->
                        trimmed.contains("fun $pattern(")
                    } ?: return@forEachIndexed

                    // Check preceding 5 lines for @Provides and @Singleton
                    val lookbackStart = maxOf(0, index - 5)
                    val precedingLines = lines.subList(lookbackStart, index + 1)

                    val hasProvides = precedingLines.any { it.trim().contains("@Provides") }
                    if (!hasProvides) return@forEachIndexed

                    val hasScope = precedingLines.any { precedingLine ->
                        scopeAnnotations.any { precedingLine.trim().contains(it) }
                    }

                    if (!hasScope) {
                        violations.add(
                            "$relativePath:${index + 1}: $matchedMethod() is @Provides " +
                                "but missing ${scopeAnnotations.joinToString(" or ")}. " +
                                "Database/Settings instances crash if created more than once.",
                        )
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Singleton Guard Failed (${violations.size} violation(s)):\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Singleton guard passed: all critical providers are scoped.")
    }
}
