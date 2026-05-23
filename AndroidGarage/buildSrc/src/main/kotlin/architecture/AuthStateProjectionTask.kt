package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Enforces: any `combine(authRepository.authState, ...)` callsite must
 * either project to a boolean (`.map { it is AuthState.Authenticated }
 * .distinctUntilChanged()`) BEFORE entering the combine, OR carry an
 * inline `authState-passthrough-ok:` annotation explaining why the raw
 * `AuthState` is needed.
 *
 * Why this matters: `AuthRepository.refreshIdToken()` writes a new
 * `Authenticated(idToken = ...)` instance into the state on every
 * token refresh. Any `combine(authState, ...) { ... fetchX() ... }`
 * where `fetchX()` transitively calls `EnsureFreshIdTokenUseCase`
 * creates a feedback loop: emission → fetch → token refresh →
 * authState change → emission → ... The 2.12.0 → 2.12.1 button-health
 * pill flicker (PR #672) was exactly this loop.
 *
 * The fix shipped in `ButtonHealthFcmSubscriptionManager`:
 * project to `Boolean` (signed in / not) before combining, so token
 * refreshes don't re-emit downstream. The boolean changes only on
 * actual auth transitions, not token rotation.
 *
 * Why an inline annotation rather than a file allowlist: legitimate
 * raw-authState consumers exist (e.g. computing a display value that
 * uses the user details, where the lambda does NOT trigger token
 * refresh). A file allowlist would silently let those files grow
 * code paths that DO trigger refresh and start looping. The
 * annotation forces the author to think about it explicitly, at the
 * callsite, and document why a refresh-triggering operation can't
 * happen.
 *
 * Annotation format (anywhere in the 10 lines above the
 * `combine(` opening):
 *
 *   // authState-passthrough-ok: <reason>
 *
 * Example legitimate usage:
 *
 *   // authState-passthrough-ok: pure display computation in
 *   // ButtonHealthDisplayLogic.compute; lambda does not refresh tokens.
 *   combine(
 *       authRepository.authState,
 *       buttonHealthRepository.buttonHealth,
 *       liveClock.nowEpochSeconds,
 *   ) { auth, health, now -> ... }
 */
abstract class AuthStateProjectionTask : DefaultTask() {
    @get:Input
    var sourceDirs: List<String> = emptyList()

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()

        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach

            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootFile).path
                    val source = file.readText()
                    val lines = source.lines()

                    // Find lines matching the raw-authState-in-combine
                    // shape. A "raw passthrough" is:
                    //   1. A line containing `authRepository.authState`,
                    //   2. that is preceded shortly by a `combine(`,
                    //   3. and is NOT chained with a Flow operator
                    //      (`.map`, `.filter`, etc) on the same line OR
                    //      the next non-blank line.
                    //
                    // Chained patterns (projected — allowed) look like:
                    //
                    //     authRepository.authState.map { ... }
                    // or
                    //     authRepository.authState
                    //         .map { ... }
                    //
                    // Raw patterns (require annotation) look like:
                    //
                    //     authRepository.authState,
                    // or
                    //     authRepository.authState
                    //     )           // (closing the combine arg list)
                    val combineRegex = Regex("""\bcombine\s*\(""")
                    val authLineRegex = Regex(
                        """^\s*authRepository\.authState(?:\s*,)?\s*$""",
                    )

                    lines.forEachIndexed { index, line ->
                        if (!combineRegex.containsMatchIn(line)) return@forEachIndexed
                        // Look at the next several lines for an
                        // authState arg. Bound the lookahead to avoid
                        // spanning unrelated code.
                        val lookaheadStart = index + 1
                        val lookaheadEnd = minOf(index + 8, lines.size)
                        for (i in lookaheadStart until lookaheadEnd) {
                            val candidate = lines[i]
                            if (!authLineRegex.matches(candidate)) continue

                            // Check if the next non-blank line chains
                            // a Flow operator like `.map` /
                            // `.filter` / `.distinctUntilChanged` —
                            // that's projection, not raw passthrough.
                            val nextNonBlank = (i + 1 until lines.size)
                                .firstOrNull { lines[it].isNotBlank() }
                                ?.let { lines[it] }
                            val isProjected =
                                nextNonBlank != null &&
                                    nextNonBlank.trimStart().startsWith(".") &&
                                    // Exclude `.value` / `.collect` which
                                    // are terminal, not projection (and
                                    // wouldn't even be valid inside a
                                    // combine arg list).
                                    !nextNonBlank.trimStart().startsWith(".value") &&
                                    !nextNonBlank.trimStart().startsWith(".collect")
                            if (isProjected) continue

                            // Raw passthrough — check for annotation in
                            // the 10 lines preceding the combine.
                            // Multi-line `//` comment blocks explaining
                            // *why* the raw form is safe routinely run
                            // 3-6 lines, plus the combine may be the
                            // RHS of a property assignment that takes
                            // its own line.
                            val preamble = lines.subList(
                                maxOf(0, index - 10),
                                index,
                            )
                            val annotated = preamble.any {
                                it.contains("authState-passthrough-ok:")
                            }
                            if (annotated) {
                                break
                            }

                            violations.add(
                                "$relativePath:${index + 1}: combine(authRepository.authState, ...) " +
                                    "without projection or annotation",
                            )
                            break
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("AuthStateProjection check FAILED: ${violations.size} violation(s).")
                    appendLine()
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Fix one of:")
                    appendLine()
                    appendLine("  1. Project authState to a boolean BEFORE combining:")
                    appendLine()
                    appendLine("       combine(")
                    appendLine("           authRepository.authState")
                    appendLine("               .map { it is AuthState.Authenticated }")
                    appendLine("               .distinctUntilChanged(),")
                    appendLine("           ...")
                    appendLine("       ) { isSignedIn, ... -> ... }")
                    appendLine()
                    appendLine("     Use this when the combine triggers any fetch or")
                    appendLine("     other side-effecting UseCase. Token refresh writes a")
                    appendLine("     new Authenticated instance, which would re-emit and")
                    appendLine("     create a feedback loop.")
                    appendLine()
                    appendLine("  2. Add an `authState-passthrough-ok:` comment annotation")
                    appendLine("     in the 10 lines above the `combine(` opening:")
                    appendLine()
                    appendLine("       // authState-passthrough-ok: pure display computation,")
                    appendLine("       // lambda does not trigger token refresh.")
                    appendLine("       combine(")
                    appendLine("           authRepository.authState,")
                    appendLine("           ...")
                    appendLine("       ) { ... }")
                    appendLine()
                    appendLine("     Use this when the combine's lambda is purely")
                    appendLine("     computational (no fetch, no UseCase that wraps auth).")
                    appendLine()
                    appendLine(
                        "Motivation: PR #672 (2.12.1) — button-health pill flickered " +
                            "because ButtonHealthFcmSubscriptionManager combined raw " +
                            "authState with serverConfig and called fetchButtonHealth " +
                            "in the lambda, which refreshed the token, which mutated " +
                            "authState, which re-emitted, which refetched, ... ",
                    )
                },
            )
        }

        logger.lifecycle("AuthStateProjection check passed.")
    }
}
