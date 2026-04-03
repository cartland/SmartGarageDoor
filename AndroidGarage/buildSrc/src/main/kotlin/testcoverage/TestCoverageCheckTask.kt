package testcoverage

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that verifies every ViewModel and Repository class has a corresponding test file.
 *
 * Supports exemptions via:
 * - Inline: `// @NoTestRequired: reason` above the class declaration
 * - Central: test-coverage-exemptions.txt with one class name per line
 */
abstract class TestCoverageCheckTask : DefaultTask() {
    @get:Input
    var sourceDir: String = ""

    @get:Input
    var testDir: String = ""

    @get:Input
    var exemptionsFile: String = ""

    @get:Input
    var patterns: List<String> = listOf("ViewModel", "Repository")

    @TaskAction
    fun check() {
        val srcRoot = File(sourceDir)
        val testRoot = File(testDir)

        if (!srcRoot.exists()) {
            logger.warn("Source directory does not exist: $sourceDir")
            return
        }

        // Load central exemptions
        val exemptions = loadExemptions()

        val missing = mutableListOf<String>()
        val covered = mutableListOf<String>()
        val exempt = mutableListOf<String>()

        srcRoot
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                val classes = findMatchingClasses(content, patterns)
                classes.forEach { className ->
                    when {
                        exemptions.contains(className) -> {
                            exempt.add(className)
                        }
                        content.contains("// @NoTestRequired") -> {
                            exempt.add("$className (inline)")
                        }
                        hasTestFile(testRoot, className) -> {
                            covered.add(className)
                        }
                        else -> {
                            missing.add(className)
                        }
                    }
                }
            }

        // Report
        logger.lifecycle("=== Test Coverage Check ===")
        logger.lifecycle("Covered: ${covered.size}")
        covered.forEach { logger.lifecycle("  ✓ $it") }
        if (exempt.isNotEmpty()) {
            logger.lifecycle("Exempt: ${exempt.size}")
            exempt.forEach { logger.lifecycle("  ○ $it") }
        }
        if (missing.isNotEmpty()) {
            logger.lifecycle("MISSING: ${missing.size}")
            missing.forEach { logger.error("  ✗ $it") }
            throw GradleException(
                "Missing test files for: ${missing.joinToString(", ")}. " +
                    "Add a *Test.kt file or exempt with // @NoTestRequired: reason",
            )
        }
        logger.lifecycle("All classes have test coverage.")
    }

    private fun loadExemptions(): Set<String> {
        val file = File(exemptionsFile)
        if (!file.exists()) return emptySet()
        return file
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    private fun findMatchingClasses(
        content: String,
        patterns: List<String>,
    ): List<String> {
        val regex = Regex("""(?:class|object)\s+(\w+)\s""")
        return regex
            .findAll(content)
            .map { it.groupValues[1] }
            .filter { name -> patterns.any { pattern -> name.contains(pattern) } }
            .filter { name ->
                // Skip interfaces, modules, enums, and abstract classes
                !content.contains("interface $name") &&
                    !name.endsWith("Module") &&
                    !content.contains("enum class $name") &&
                    !content.contains("abstract class $name")
            }.toList()
    }

    private fun hasTestFile(
        testRoot: File,
        className: String,
    ): Boolean {
        if (!testRoot.exists()) return false
        val testFileName = "${className}Test.kt"
        // Also check for base name without Impl suffix
        val baseTestFileName = if (className.endsWith("Impl")) {
            "${className.removeSuffix("Impl")}Test.kt"
        } else {
            null
        }
        return testRoot.walkTopDown().any {
            it.name == testFileName || (baseTestFileName != null && it.name == baseTestFileName)
        }
    }
}
