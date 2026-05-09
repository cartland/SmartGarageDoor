package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces ADR-027: ID token state is private to `AuthRepository` and the
 * network repositories that legitimately need it. **UseCases never touch
 * a token.**
 *
 * Detection: scans every `*UseCase.kt` file in the configured source dirs
 * for parameter declarations of the form `idToken: String` or
 * `idToken: FirebaseIdToken`. Any match is a violation — the UseCase is
 * threading a token through its public API surface, which is exactly what
 * Option C eliminated.
 *
 * The token-handling responsibility moved into `AuthRepository.getIdToken`
 * and the per-repo `getIdToken(forceRefresh = true)` calls in
 * `Network*Repository`. UseCases call `AuthRepository.authState` to gate
 * on `is AuthState.Authenticated` and delegate to a repo method that takes
 * no token argument.
 *
 * **Exemptions:** `SignInWithGoogleUseCase` (and any future UseCase that
 * passes a *Google* sign-in token to begin the auth flow — distinct from
 * the Firebase ID token) is exempt because the credential it carries is
 * the *input* to authentication, not a token *issued by* it. The check
 * scopes to `idToken: ` (Firebase ID token convention) and ignores
 * `googleIdToken: ` and other inbound credentials.
 */
abstract class NoTokenInUseCaseTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    /**
     * Token parameter shapes that indicate the ADR-027 violation. Matches
     * the parameter name `idToken` followed by a type the repository
     * boundary uses for Firebase ID tokens. Other token shapes (Google
     * sign-in credential, FCM topic key, button-ack token) are not
     * covered by ADR-027 and stay legal.
     */
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
                .filter { it.name.endsWith("UseCase.kt") }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
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
                    append("NoTokenInUseCase check FAILED: ")
                    append(violations.size)
                    append(" violation(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("ADR-027: ID tokens must NOT appear in UseCase signatures.\n")
                    append("Token state is private to AuthRepository and the network\n")
                    append("repositories that need it. UseCases gate on is-authenticated\n")
                    append("and delegate to a repo method that takes no token argument.\n\n")
                    append("Fix:\n")
                    append("  - Drop the `idToken: String` (or `idToken: FirebaseIdToken`)\n")
                    append("    parameter from the UseCase's `invoke(...)` signature.\n")
                    append("  - Move the token fetch into the repository implementation\n")
                    append("    via `authRepository.getIdToken(forceRefresh = true)`.\n")
                    append("  - The UseCase keeps its `is AuthState.Authenticated` gate.\n\n")
                    append("Canonical examples (post-ADR-027):\n")
                    append("  - FetchButtonHealthUseCase, PushRemoteButtonUseCase,\n")
                    append("    SnoozeNotificationsUseCase\n")
                    append("  - NetworkButtonHealthRepository, NetworkRemoteButtonRepository,\n")
                    append("    NetworkSnoozeRepository, CachedFeatureAllowlistRepository\n\n")
                    append("See AndroidGarage/docs/DECISIONS.md (ADR-027).\n")
                },
            )
        }

        logger.lifecycle("NoTokenInUseCase check passed: no UseCase exposes an idToken parameter.")
    }
}
