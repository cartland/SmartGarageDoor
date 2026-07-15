package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkResult

/**
 * Fake [NetworkButtonDataSource] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks each call via `*Calls`
 * lists (ADR-017 Rule 5 — call-list pattern), so tests can assert on the
 * exact arguments passed (idTokens, ack tokens, snooze durations) without
 * relying on call ordering. The `*Count` accessors are convenience reads
 * backed by the lists.
 */
class FakeNetworkButtonDataSource : NetworkButtonDataSource {
    data class PushCall(
        val remoteButtonBuildTimestamp: String,
        val buttonAckToken: String,
        val remoteButtonPushKey: String,
        val idToken: String,
    )

    data class SnoozeCall(
        val buildTimestamp: String,
        val remoteButtonPushKey: String,
        val idToken: String,
        val snoozeDurationHours: String,
        val snoozeEventTimestampSeconds: Long,
    )

    private var pushResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
    private var snoozeResult: NetworkResult<Long> = NetworkResult.Success(0L)
    private var fetchSnoozeResult: NetworkResult<Long> = NetworkResult.Success(0L)

    private val _pushCalls = mutableListOf<PushCall>()
    val pushCalls: List<PushCall> get() = _pushCalls
    val pushCount: Int get() = _pushCalls.size

    private val _snoozeCalls = mutableListOf<SnoozeCall>()
    val snoozeCalls: List<SnoozeCall> get() = _snoozeCalls
    val snoozeCount: Int get() = _snoozeCalls.size

    private val _fetchSnoozeBuildTimestamps = mutableListOf<String>()
    val fetchSnoozeBuildTimestamps: List<String> get() = _fetchSnoozeBuildTimestamps
    val fetchSnoozeCount: Int get() = _fetchSnoozeBuildTimestamps.size

    fun setPushResult(value: NetworkResult<Unit>) {
        pushResult = value
    }

    fun setSnoozeResult(value: NetworkResult<Long>) {
        snoozeResult = value
    }

    fun setFetchSnoozeResult(value: NetworkResult<Long>) {
        fetchSnoozeResult = value
    }

    override suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<Unit> {
        _pushCalls.add(
            PushCall(
                remoteButtonBuildTimestamp = remoteButtonBuildTimestamp,
                buttonAckToken = buttonAckToken,
                remoteButtonPushKey = remoteButtonPushKey,
                idToken = idToken,
            ),
        )
        return pushResult
    }

    override suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): NetworkResult<Long> {
        _snoozeCalls.add(
            SnoozeCall(
                buildTimestamp = buildTimestamp,
                remoteButtonPushKey = remoteButtonPushKey,
                idToken = idToken,
                snoozeDurationHours = snoozeDurationHours,
                snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
            ),
        )
        return snoozeResult
    }

    private var fetchSnoozeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /**
     * When set, [fetchSnoozeEndTimeSeconds] suspends until the deferred
     * completes — lets tests hold a GET in flight while other operations
     * (e.g. a snooze submit) run to completion, to exercise write-ordering
     * races the ordinary synchronous fake cannot reach. Pass null to
     * disarm for subsequent fetches.
     */
    fun setFetchSnoozeGate(gate: kotlinx.coroutines.CompletableDeferred<Unit>?) {
        fetchSnoozeGate = gate
    }

    override suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): NetworkResult<Long> {
        _fetchSnoozeBuildTimestamps.add(buildTimestamp)
        // Capture at request time (the server computes the response from
        // then-current state); the gate only delays DELIVERY, so a gated
        // response stays stale even if the test changes the result field.
        val result = fetchSnoozeResult
        fetchSnoozeGate?.await()
        return result
    }
}
