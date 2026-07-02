package com.chriscartland.garage.domain.model

/**
 * Server-derived config the device fetches at startup.
 *
 * `remoteButtonPushKey` is the shared secret sent in the
 * `X-RemoteButtonPushKey` header on every door-control HTTP call —
 * combined with the user's Firebase ID token + email allowlist it gates
 * the relay-activation path. It must NOT appear in logs.
 *
 * Auto-generated `equals` / `hashCode` / `copy` / `componentN` keep the
 * full field set as Kotlin data classes always do — only `toString` is
 * overridden, because `toString` is the only path through which the
 * push key leaked (audit finding H1 / C1: `Logger.i { "config <- $config" }`
 * patterns rendered the auto-generated toString into logcat).
 */
data class ServerConfig(
    val buildTimestamp: String,
    val remoteButtonBuildTimestamp: String,
    val remoteButtonPushKey: String,
) {
    /**
     * Override the auto-generated toString to mask `remoteButtonPushKey`.
     *
     * Distinguishes empty from non-empty so a future "push key is empty
     * in prod" bug stays diagnosable from logs:
     *   - Empty   → `remoteButtonPushKey=<empty>`
     *   - Present → `remoteButtonPushKey=***`
     */
    override fun toString(): String {
        val maskedKey = if (remoteButtonPushKey.isEmpty()) "<empty>" else "***"
        return "ServerConfig(" +
            "buildTimestamp=$buildTimestamp, " +
            "remoteButtonBuildTimestamp=$remoteButtonBuildTimestamp, " +
            "remoteButtonPushKey=$maskedKey" +
            ")"
    }
}
