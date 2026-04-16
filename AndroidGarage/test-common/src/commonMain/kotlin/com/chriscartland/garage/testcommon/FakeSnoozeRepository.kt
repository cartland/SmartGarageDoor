package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [SnoozeRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks snooze calls via
 * `snoozeCalls` (ADR-017 Rule 5 — call-list pattern), so tests can assert on
 * the exact duration / token / timestamp passed. `fetchSnoozeStatus` takes no
 * args so it stays as a plain counter accessor.
 */
class FakeSnoozeRepository : SnoozeRepository {
    data class SnoozeCall(
        val snoozeDurationHours: String,
        val idToken: String,
        val snoozeEventTimestampSeconds: Long,
    )

    private val snoozeStateFlow = MutableStateFlow<SnoozeState>(SnoozeState.Loading)

    private val _snoozeCalls = mutableListOf<SnoozeCall>()
    val snoozeCalls: List<SnoozeCall> get() = _snoozeCalls
    val snoozeCount: Int get() = _snoozeCalls.size

    private var _fetchCount: Int = 0
    val fetchCount: Int get() = _fetchCount

    /** Set this to false to make snoozeNotifications() return false (network failure). */
    private var snoozeResult: Boolean = true

    fun setSnoozeState(state: SnoozeState) {
        snoozeStateFlow.value = state
    }

    fun setSnoozeResult(value: Boolean) {
        snoozeResult = value
    }

    override fun observeSnoozeState(): Flow<SnoozeState> = snoozeStateFlow

    override suspend fun fetchSnoozeStatus() {
        _fetchCount++
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean {
        _snoozeCalls.add(
            SnoozeCall(
                snoozeDurationHours = snoozeDurationHours,
                idToken = idToken,
                snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
            ),
        )
        return snoozeResult
    }
}
