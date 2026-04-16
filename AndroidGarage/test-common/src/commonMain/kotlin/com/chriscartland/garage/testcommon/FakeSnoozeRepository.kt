package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [SnoozeRepository] for unit testing.
 *
 * Configure responses with `setX()` methods. ADR-017 Rule 5.
 */
class FakeSnoozeRepository : SnoozeRepository {
    private val snoozeStateFlow = MutableStateFlow<SnoozeState>(SnoozeState.Loading)

    var snoozeCount = 0
        private set

    var fetchCount = 0
        private set

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
        fetchCount++
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ): Boolean {
        snoozeCount++
        return snoozeResult
    }
}
