package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake [SnoozeRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. Tracks snooze calls via
 * `snoozeCalls` (ADR-017 Rule 5 — call-list pattern), so tests can assert on
 * the exact duration / token / timestamp passed. `fetchSnoozeStatus` takes no
 * args so it stays as a plain counter accessor.
 *
 * Mirrors production: on a configured successful result, writes the result
 * value to the observable flow AND returns it, so tests can assert on either
 * the return path or the observer path.
 */
class FakeSnoozeRepository : SnoozeRepository {
    data class SnoozeCall(
        val snoozeDurationHours: String,
        val idToken: String,
        val snoozeEventTimestampSeconds: Long,
    )

    private val _snoozeState = MutableStateFlow<SnoozeState>(SnoozeState.Loading)
    override val snoozeState: StateFlow<SnoozeState> = _snoozeState

    private val _snoozeCalls = mutableListOf<SnoozeCall>()
    val snoozeCalls: List<SnoozeCall> get() = _snoozeCalls
    val snoozeCount: Int get() = _snoozeCalls.size

    private var _fetchCount: Int = 0
    val fetchCount: Int get() = _fetchCount

    /** Controls what snoozeNotifications() returns. Default: success with NotSnoozing. */
    private var snoozeResult: AppResult<SnoozeState, ActionError> =
        AppResult.Success(SnoozeState.NotSnoozing)

    fun setSnoozeState(state: SnoozeState) {
        _snoozeState.value = state
    }

    fun setSnoozeResult(value: AppResult<SnoozeState, ActionError>) {
        snoozeResult = value
    }

    override suspend fun fetchSnoozeStatus() {
        _fetchCount++
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): AppResult<SnoozeState, ActionError> {
        _snoozeCalls.add(
            SnoozeCall(
                snoozeDurationHours = snoozeDurationHours,
                idToken = idToken,
                snoozeEventTimestampSeconds = snoozeEventTimestampSeconds,
            ),
        )
        // Mirror the production repo: on success, propagate the result value
        // to the observable flow so observers see it too.
        if (snoozeResult is AppResult.Success) {
            _snoozeState.value = (snoozeResult as AppResult.Success<SnoozeState>).data
        }
        return snoozeResult
    }
}
