package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Verifies that every `@Singleton` provider in `AppComponent.kt`
 * produces matching `_scoped.get(...)` caching in the generated
 * `InjectAppComponent.kt`.
 *
 * kotlin-inject's `@Singleton` is annotation-shaped — the compiler
 * accepts any declaration shape, and only *abstract-entry-point
 * overrides with parameter-based `@Provides fun` bodies* emit
 * caching. Concrete `val x: T @Provides @Singleton get() = ...`
 * declarations silently bypass the cache: every consumer receives a
 * fresh instance, tests still pass, and the feature-specific symptom
 * may take months to isolate. That was the shape of the android/170
 * snooze regression.
 *
 * This check reads the generated file (the only authoritative source
 * for what kotlin-inject actually emitted) and fails the build if any
 * `@Singleton` provider in source is missing a corresponding
 * `_scoped.get(...) { provideX() }` call in the generated output.
 *
 * See `AndroidGarage/docs/DI_SINGLETON_REQUIREMENTS.md` and
 * `AndroidGarage/docs/POSTMORTEM_ANDROID_170.md` for context.
 */
abstract class SingletonCachingCheckTask : DefaultTask() {
    @get:Input
    var appComponentPath: String = ""

    /**
     * Path to the generated `InjectAppComponent.kt`. KSP emits one
     * per variant; checking debug is sufficient because all variants
     * generate identical scoping code from the same source.
     */
    @get:Input
    var generatedComponentPath: String = ""

    @TaskAction
    fun check() {
        val appComponentFile = File(appComponentPath)
        if (!appComponentFile.exists()) {
            throw GradleException(
                "Singleton caching check: AppComponent.kt not found at $appComponentPath",
            )
        }

        val generatedFile = File(generatedComponentPath)
        if (!generatedFile.exists()) {
            throw GradleException(
                "Singleton caching check: generated InjectAppComponent.kt not found at " +
                    "$generatedComponentPath. Ensure this task runs after :androidApp:kspDebugKotlin.",
            )
        }

        val sourceContent = appComponentFile.readText()
        val generatedContent = generatedFile.readText()

        val singletonProviders = extractSingletonProviders(sourceContent)

        if (singletonProviders.isEmpty()) {
            throw GradleException(
                "Singleton caching check: no @Singleton providers found in AppComponent.kt. " +
                    "Either the file shape changed or the regex needs updating.",
            )
        }

        // Check 1: each well-shaped @Singleton fun has a matching _scoped.get entry.
        val missingCaching = singletonProviders.filter { providerName ->
            !isCachedInGenerated(providerName, generatedContent)
        }

        // Check 2: the total count of @Singleton annotations on providers (any shape)
        // matches the count of _scoped.get(...) entries in the generated file. This
        // catches the regression where a provider is marked @Singleton but written in a
        // shape that bypasses caching (e.g., `val x: T @Provides @Singleton get() = ...`)
        // — that shape isn't picked up by extractSingletonProviders above, so the name
        // check alone would miss it.
        val providerScopeAnnotations = countProviderScopeAnnotations(sourceContent)
        val cachedEntries = countCachedEntries(generatedContent)

        val errors = buildList {
            if (missingCaching.isNotEmpty()) {
                add(
                    buildString {
                        append("${missingCaching.size} @Singleton provider(s) marked correctly but not cached:\n")
                        missingCaching.forEach { append("  - $it\n") }
                    },
                )
            }
            if (providerScopeAnnotations != cachedEntries) {
                add(
                    "@Singleton provider count ($providerScopeAnnotations) does not match " +
                        "generated _scoped.get(...) count ($cachedEntries). A provider is likely " +
                        "declared in a shape that bypasses caching — see fix guidance below.",
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Singleton caching check FAILED:\n\n")
                    errors.forEach { append("$it\n") }
                    append("\n")
                    append("These @Singleton providers are not being cached by kotlin-inject.\n")
                    append("Every consumer will get a fresh instance; shared state will break.\n\n")
                    append("Root cause is usually provider shape. Fix:\n")
                    append("  - Prefer: @Provides @Singleton fun provideX(...): T = ...\n")
                    append("  - Avoid:  val x: T @Provides @Singleton get() = ...\n")
                    append("    (concrete getters bypass caching)\n")
                    append("  - The abstract entry point `abstract val X: T` must be declared in\n")
                    append("    AppComponent so the generator has something to override.\n\n")
                    append("See docs/DI_SINGLETON_REQUIREMENTS.md and docs/POSTMORTEM_ANDROID_170.md.\n")
                    append("This is the shape of the android/170 snooze regression.\n")
                },
            )
        }

        logger.lifecycle(
            "Singleton caching check passed: ${singletonProviders.size} @Singleton providers " +
                "verified; $cachedEntries generated _scoped.get(...) entries match.",
        )
    }

    private fun extractSingletonProviders(sourceContent: String): List<String> {
        // Match indented `@Provides` and `@Singleton` annotations (in either order)
        // preceding a `fun provideX(` declaration. Whitespace between annotations
        // may include newlines. The provider name is captured in group 1.
        //
        // Class-level `@Singleton` on `abstract class AppComponent` is ignored
        // because it isn't followed by `fun provideX(`.
        val regex = Regex(
            pattern = """(?:@Provides\s+@Singleton|@Singleton\s+@Provides)\s+fun\s+(\w+)\s*\(""",
            option = RegexOption.MULTILINE,
        )
        return regex.findAll(sourceContent).map { it.groupValues[1] }.toList()
    }

    private fun isCachedInGenerated(
        providerName: String,
        generatedContent: String,
    ): Boolean {
        // The generator emits: `_scoped.get("FQN") { provideX() }` or
        // `_scoped.get("FQN") { provideX(arg = ...) }`. We search for the
        // lexical proximity of `_scoped.get(` and the specific `provideX(`
        // call within a small window — the generator always puts them on
        // adjacent lines.
        val cachedCallPattern = Regex(
            pattern = """_scoped\.get\s*\([^)]*\)\s*\{\s*$providerName\s*\(""",
            option = RegexOption.MULTILINE,
        )
        return cachedCallPattern.containsMatchIn(generatedContent)
    }

    /**
     * Counts `@Singleton` annotations that are declaration annotations (on
     * their own line, optionally indented), excluding the class-level
     * `@Singleton` on `AppComponent`. This catches the annotation whether
     * it precedes `fun provideX(` (good shape) or a getter of a concrete
     * `val` property (bad shape — the android/170 regression). Matches on
     * own-line only so `@Singleton` mentions inside KDoc/comments are
     * ignored.
     */
    private fun countProviderScopeAnnotations(sourceContent: String): Int {
        val standaloneAnnotationLines =
            Regex("""^\s*@Singleton\s*$""", RegexOption.MULTILINE)
                .findAll(sourceContent)
                .count()
        // The class-level `@Singleton` is also on its own line (directly
        // above `abstract class AppComponent`). Subtract one for it.
        val hasClassLevelSingleton =
            Regex("""@Singleton\s+abstract\s+class\s+AppComponent\b""")
                .containsMatchIn(sourceContent)
        return standaloneAnnotationLines - (if (hasClassLevelSingleton) 1 else 0)
    }

    private fun countCachedEntries(generatedContent: String): Int = Regex("""_scoped\.get\s*\(""").findAll(generatedContent).count()
}
