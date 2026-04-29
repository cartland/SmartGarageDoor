package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces a `@Singleton` (or other configured scope) annotation on every
 * `@Provides fun <name>(...)` listed in [guardedMethods]. These methods
 * return single-instance backing classes that **crash or corrupt state**
 * when constructed twice for the same on-disk file:
 *
 *  - `DataStore<Preferences>` — second instance throws
 *    `IllegalStateException: There are multiple DataStores active for the
 *     same file`.
 *  - Room `AppDatabase` — second instance can deadlock or corrupt the SQLite
 *    file under concurrent writes.
 *
 * This is the static-analysis half of a defense-in-depth pair with
 * `:checkSingletonCaching` (which validates kotlin-inject's *generated*
 * `_scoped.get(...)` calls). This task validates the *annotation* on the
 * source file; the other task validates the generated cache code. Together
 * they cover both "forgot the @Singleton annotation" and "annotation was
 * present but the cache wasn't generated for it" failure modes.
 *
 * Adapted from
 * https://github.com/cartland/battery-butler `DataStoreSingletonCheckTask`.
 *
 * Why a fresh check rather than just relying on `:checkSingletonCaching`:
 * the existing check enumerates which `@Singleton` providers exist and
 * verifies each has a corresponding `_scoped.get(...)` call in the
 * generated component. If a developer forgets the annotation entirely, the
 * provider isn't in the enumeration — and the existing check is silent.
 * This task closes that gap by forcing the annotation on the named
 * methods.
 */
abstract class DataStoreSingletonCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Names of `@Provides fun` methods that MUST carry a singleton scope
     * annotation. Match is on the literal `fun <name>(` in the source.
     */
    @get:Input
    var guardedMethods: List<String> = listOf(
        "provideAppSettings",
        "provideAppDatabase",
    )

    /**
     * Annotations that satisfy the singleton requirement. This repo uses
     * a single `@Singleton` annotation; the list is overridable for parity
     * with other DI components that introduce sibling scopes.
     */
    @get:Input
    var scopeAnnotations: List<String> = listOf(
        "@Singleton",
    )

    private val providesPattern = Regex("""@Provides""")

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { !it.path.contains("/build/") }
                .filter { !it.path.contains("/test/") && !it.path.contains("/androidTest/") }
                .forEach { file ->
                    val lines = file.readLines()
                    val relativePath = file.relativeTo(rootFile).path

                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        // Skip comments / KDoc.
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

                        val matchedMethod = guardedMethods.firstOrNull { name ->
                            trimmed.contains("fun $name(")
                        } ?: return@forEachIndexed

                        // Look back up to 5 lines for the `@Provides` and
                        // scope annotations. Annotations on Kotlin
                        // functions can stack on separate lines or share
                        // the same line as the `fun` declaration.
                        val lookbackStart = maxOf(0, index - 5)
                        val precedingLines = lines.subList(lookbackStart, index + 1)

                        val hasProvidesAnnotation = precedingLines.any { providesPattern.containsMatchIn(it) }
                        // Skip if it's not a @Provides function — the
                        // method name might also appear in plain
                        // interface declarations or test fakes.
                        if (!hasProvidesAnnotation) return@forEachIndexed

                        val hasScopeAnnotation = precedingLines.any { precedingLine ->
                            scopeAnnotations.any { ann -> precedingLine.trim().contains(ann) }
                        }

                        if (!hasScopeAnnotation) {
                            violations.add(
                                "$relativePath:${index + 1}: $matchedMethod() is missing a singleton scope annotation",
                            )
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("DataStoreSingletonCheck FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("These `@Provides` methods MUST carry a singleton scope annotation\n")
                    append("(${scopeAnnotations.joinToString(" or ")}).\n\n")
                    append("Why this is enforced statically:\n")
                    append("  - DataStore<Preferences>: second instance for the same file throws\n")
                    append("    IllegalStateException at runtime.\n")
                    append("  - Room AppDatabase: second instance can deadlock or corrupt the SQLite\n")
                    append("    file under concurrent writes.\n\n")
                    append("Fix: add `@Singleton` directly above the `@Provides` annotation.\n\n")
                    append("Defense-in-depth note: this is paired with `:checkSingletonCaching`,\n")
                    append("which validates kotlin-inject's generated `_scoped.get(...)` cache.\n")
                    append("Together they catch (a) forgot-the-annotation and (b) annotation-was-\n")
                    append("present-but-cache-was-not-generated failure modes.\n")
                },
            )
        }

        logger.lifecycle(
            "DataStoreSingletonCheck passed: ${guardedMethods.size} guarded methods verified.",
        )
    }
}
