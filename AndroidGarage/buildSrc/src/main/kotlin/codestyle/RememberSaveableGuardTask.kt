package codestyle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Detects unsafe `rememberSaveable` usage with types that aren't Bundle-saveable.
 *
 * `rememberSaveable` serializes state to a Bundle on process death. If the
 * state contains types that aren't Parcelable, java.io.Serializable, or a
 * primitive, the app crashes on save or restore. This happened with Nav3's
 * Screen objects (kotlinx `@Serializable` ≠ `java.io.Serializable`).
 *
 * The check has three pass conditions for a `rememberSaveable` call line:
 *   1. The line contains the substring `saver` — caller has supplied
 *      `saver = ...` or `stateSaver = ...` explicitly.
 *   2. The line uses a Compose-built-in primitive state factory whose Saver
 *      is provided by Compose itself (`mutableIntStateOf`, `mutableLongStateOf`,
 *      `mutableFloatStateOf`, `mutableDoubleStateOf`).
 *   3. The line passes a primitive literal to `mutableStateOf(...)`
 *      (`true`, `false`, an integer literal, a string literal, or a
 *      hex/float literal) — `autoSaver()` handles these safely.
 *
 * Anything else fails the build. The motivating bug was Nav3 Screen objects
 * being passed to `rememberSaveable` with no explicit Saver: `@Serializable`
 * is `kotlinx.serialization`, not `java.io.Serializable`, and the Bundle
 * write blew up at runtime. The check makes that class of mistake impossible
 * to add silently.
 *
 * Use `remember` instead for types that don't need to survive process death.
 */
abstract class RememberSaveableGuardTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Patterns recognized as safe initializers without an explicit `saver`.
     * Each is a Compose primitive whose serialization is built in.
     */
    private val safeInitializerPatterns: List<Regex> = listOf(
        // Compose primitive state factories with built-in savers.
        Regex("""mutableIntStateOf\s*\("""),
        Regex("""mutableLongStateOf\s*\("""),
        Regex("""mutableFloatStateOf\s*\("""),
        Regex("""mutableDoubleStateOf\s*\("""),
        // mutableStateOf(<primitive literal>) — autoSaver() handles these.
        Regex("""mutableStateOf\s*\(\s*(true|false)\s*\)"""),
        Regex("""mutableStateOf\s*\(\s*-?\d+(\.\d+)?[fFLd]?\s*\)"""),
        Regex("""mutableStateOf\s*\(\s*0[xX][0-9a-fA-F]+[Ll]?\s*\)"""),
        Regex("""mutableStateOf\s*\(\s*"[^"]*"\s*\)"""),
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        for (dirPath in sourceDirs) {
            val dir = File(dirPath)
            if (!dir.exists()) continue

            dir
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    val content = file.readText()
                    if ("rememberSaveable" !in content) return@forEach

                    val lines = file.readLines()
                    val relativePath = file.relativeTo(dir).path

                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        // Match actual rememberSaveable function calls, not
                        // functions that contain "rememberSaveable" as a
                        // substring (e.g., rememberSaveableStateHolderNavEntryDecorator).
                        val hasCall = Regex("""\brememberSaveable\s*[\({]""").containsMatchIn(trimmed)
                        if (!hasCall ||
                            trimmed.startsWith("//") ||
                            trimmed.startsWith("*") ||
                            trimmed.startsWith("import ")
                        ) {
                            return@forEachIndexed
                        }
                        // Match both `saver = ...` and `stateSaver = ...`,
                        // and bare `autoSaver()` calls (capital S in
                        // `Saver`).
                        if (trimmed.contains("saver", ignoreCase = true)) {
                            return@forEachIndexed
                        }
                        if (safeInitializerPatterns.any { it.containsMatchIn(trimmed) }) {
                            return@forEachIndexed
                        }
                        violations.add(
                            "$relativePath:${index + 1}: $trimmed\n" +
                                "    rememberSaveable without an explicit `saver` parameter is unsafe\n" +
                                "    for custom types. Use `remember` or provide a Saver, or use a\n" +
                                "    primitive initializer (mutable{Int,Long,Float,Double}StateOf, or\n" +
                                "    mutableStateOf with a primitive literal).",
                        )
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "RememberSaveable Guard Failed (${violations.size} violation(s)):\n\n" +
                    violations.joinToString("\n\n") { "  $it" },
            )
        }

        val fileCount = sourceDirs.sumOf { dirPath ->
            val dir = File(dirPath)
            if (dir.exists()) dir.walkTopDown().filter { it.extension == "kt" }.count() else 0
        }
        logger.lifecycle("RememberSaveable guard passed: $fileCount files scanned.")
    }
}
