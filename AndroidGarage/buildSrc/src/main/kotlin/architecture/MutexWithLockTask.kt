package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces: `Mutex` usage must go through `withLock { ... }`, never bare
 * `.lock()` / `.unlock()` pairs.
 *
 * A bare `mutex.lock()` / `mutex.unlock()` is a correctness bug, not a
 * style preference: a throw between the pair strands the lock for the
 * rest of the process lifetime, deadlocking everything that ever tries
 * to acquire it. `withLock { ... }` is the only safe idiom — it uses
 * `try/finally` internally to guarantee release.
 *
 * This lint had zero violations when added (the one `Mutex` in the
 * codebase already uses `withLock`). It locks the current state.
 */
abstract class MutexWithLockTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @TaskAction
    fun check() {
        // Match `.lock()` or `.unlock()` with a name preceding the dot.
        // `withLock` has a `{ ... }` block and does not match.
        // `lockInstance.lock()` matches; `mutex.withLock { ... }` does not.
        val lockPattern = Regex("""\b(\w+)\.lock\s*\(\s*\)""")
        val unlockPattern = Regex("""\b(\w+)\.unlock\s*\(\s*\)""")

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
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                        lockPattern.findAll(line).forEach { match ->
                            violations.add("$relativePath:${index + 1}: ${match.value}  (use withLock { ... } instead)")
                        }
                        unlockPattern.findAll(line).forEach { match ->
                            violations.add("$relativePath:${index + 1}: ${match.value}  (use withLock { ... } instead)")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("MutexWithLock check FAILED: ")
                    append(violations.size)
                    append(" bare lock/unlock call(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Bare `mutex.lock()` / `mutex.unlock()` strands the lock for the\n")
                    append("rest of the process if anything between them throws. Use:\n")
                    append("  import kotlinx.coroutines.sync.withLock\n")
                    append("  mutex.withLock { /* critical section */ }\n\n")
                    append("`withLock` wraps in try/finally and guarantees release.\n")
                },
            )
        }

        logger.lifecycle("MutexWithLock check passed: no bare .lock()/.unlock() calls found.")
    }
}
