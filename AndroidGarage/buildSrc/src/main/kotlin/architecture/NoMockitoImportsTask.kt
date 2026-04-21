package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-003 (testing philosophy): no Mockito imports in test files.
 *
 * The codebase uses fakes over mocks — shared `Fake*` / `InMemory*`
 * implementations in `test-common/` that every test depends on. Mockito
 * was fully removed in PR #215; this lint prevents accidental
 * re-introduction via auto-import.
 *
 * Zero violations today. Scope is all test source directories.
 */
abstract class NoMockitoImportsTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @TaskAction
    fun check() {
        val importPattern = Regex("""^\s*import\s+org\.mockito(\.|$)""")
        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        if (importPattern.containsMatchIn(line)) {
                            violations.add("$relativePath:${index + 1}: ${line.trim()}")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("NoMockitoImports check FAILED: ")
                    append(violations.size)
                    append(" Mockito import(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ADR-003: fakes over mocks. Mockito was fully removed in PR #215.\n")
                    append("Fix: replace the Mockito usage with a fake from `test-common/`, or\n")
                    append("create a new fake that implements the same interface.\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-003) and `test-common/src/`.\n")
                },
            )
        }

        logger.lifecycle("NoMockitoImports check passed: no org.mockito.* imports found.")
    }
}
