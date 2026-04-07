package codestyle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Detects unsafe `rememberSaveable` usage with types that aren't Bundle-saveable.
 *
 * `rememberSaveable` serializes state to a Bundle on process death. If the state
 * contains types that aren't Parcelable, java.io.Serializable, or a primitive,
 * the app crashes on save or restore. This happened with Nav3's Screen objects
 * (kotlinx @Serializable ≠ java.io.Serializable).
 *
 * This task scans for `rememberSaveable` calls and flags any that don't use a
 * known-safe type (String, Int, Boolean, etc.) or an explicit `saver` parameter.
 *
 * Use `remember` instead for types that don't need to survive process death.
 */
abstract class RememberSaveableGuardTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

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
                        // Match actual rememberSaveable function calls, not functions
                        // that contain "rememberSaveable" as a substring (e.g.,
                        // rememberSaveableStateHolderNavEntryDecorator).
                        val hasCall = Regex("""\brememberSaveable\s*[\({]""").containsMatchIn(trimmed)
                        if (hasCall &&
                            "saver" !in trimmed &&
                            !trimmed.startsWith("//") &&
                            !trimmed.startsWith("*") &&
                            !trimmed.startsWith("import ")
                        ) {
                            violations.add(
                                "$relativePath:${index + 1}: $trimmed\n" +
                                    "    rememberSaveable without an explicit `saver` parameter is unsafe\n" +
                                    "    for custom types. Use `remember` or provide a Saver.",
                            )
                        }
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
