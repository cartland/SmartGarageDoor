package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSnoozeRepository : SnoozeRepository {
    private val _snoozeState = MutableStateFlow<SnoozeState>(SnoozeState.Loading)
    override val snoozeState: StateFlow<SnoozeState> = _snoozeState

    var snoozeCount = 0
        private set

    var fetchCount = 0
        private set

    fun setSnoozeState(state: SnoozeState) {
        _snoozeState.value = state
    }

    override suspend fun fetchSnoozeStatus() {
        fetchCount++
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ) {
        snoozeCount++
    }
}
