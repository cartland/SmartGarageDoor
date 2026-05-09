package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-027 from the *repository interface* side: no domain
 * repository interface may declare an `idToken: String` or
 * `idToken: FirebaseIdToken` parameter on its public methods.
 *
 * Why this matters: ADR-027 puts the ID token entirely inside
 * `AuthRepository`. The Network*Repository implementations call
 * `authRepository.getIdToken(forceRefresh = true)` themselves before
 * delegating to a data source. If a new repository interface threads
 * `idToken` as a parameter, a downstream UseCase or ViewModel ends up
 * pulling the token through the call chain — exactly the shape Option C
 * eliminated.
 *
 * The complementary check, [NoTokenInUseCaseTask], catches the same
 * regression at the UseCase layer. This one catches it earlier, at the
 * interface declaration in `domain/`.
 *
 * Detection: scans every Kotlin file under `domain/.../repository/`
 * for `idToken` parameters of type `String` or `FirebaseIdToken`. Exempts
 * `AuthRepository.kt` (whose `getIdToken(forceRefresh)` doesn't have
 * `idToken:` as a parameter — the param name is `forceRefresh` — but
 * the file is exempted explicitly for clarity).
 */
abstract class RepositoryInterfaceNoTokenCheckTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @get:Input
    var exemptFileNames: List<String> = listOf("AuthRepository.kt")

    @get:Input
    var bannedParameterPatterns: List<String> = listOf(
        """\bidToken\s*:\s*String\b""",
        """\bidToken\s*:\s*FirebaseIdToken\b""",
    )

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val patterns = bannedParameterPatterns.map { Regex(it) }

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name !in exemptFileNames }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    file.readLines().forEachIndexed { index, line ->
                        patterns.forEach { pattern ->
                            if (pattern.containsMatchIn(line)) {
                                violations.add("$relativePath:${index + 1}: ${line.trim()}")
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("RepositoryInterfaceNoToken check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ADR-027: domain repository interfaces must NOT declare an\n")
                    append("`idToken: String` (or `idToken: FirebaseIdToken`) parameter.\n")
                    append("Token state is private to AuthRepository; implementations\n")
                    append("call `authRepository.getIdToken(forceRefresh = true)`\n")
                    append("themselves before delegating to a data source.\n\n")
                    append("Fix:\n")
                    append("  - Drop the `idToken:` parameter from the interface method.\n")
                    append("  - In the implementation, inject `AuthRepository` and\n")
                    append("    fetch the token internally.\n\n")
                    append("Canonical examples (post-ADR-027):\n")
                    append("  - ButtonHealthRepository.fetchButtonHealth() — no params\n")
                    append("  - RemoteButtonRepository.pushButton(buttonAckToken) — no token\n")
                    append("  - SnoozeRepository.snoozeNotifications(...) — no token\n\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-027).\n")
                },
            )
        }

        logger.lifecycle("RepositoryInterfaceNoToken check passed: no repo interface exposes an idToken parameter.")
    }
}
