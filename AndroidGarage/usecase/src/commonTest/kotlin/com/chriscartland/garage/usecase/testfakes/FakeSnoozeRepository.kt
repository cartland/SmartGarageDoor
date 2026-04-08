package com.chriscartland.garage.usecase.testfakes

import com.chriscartland.garage.domain.model.SnoozeRequestStatus
import com.chriscartland.garage.domain.repository.SnoozeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSnoozeRepository : SnoozeRepository {
    private val _snoozeRequestStatus = MutableStateFlow(SnoozeRequestStatus.IDLE)
    override val snoozeRequestStatus: StateFlow<SnoozeRequestStatus> = _snoozeRequestStatus

    private val _snoozeEndTimeSeconds = MutableStateFlow(0L)
    override val snoozeEndTimeSeconds: StateFlow<Long> = _snoozeEndTimeSeconds

    var snoozeCount = 0
        private set

    fun setSnoozeStatus(status: SnoozeRequestStatus) {
        _snoozeRequestStatus.value = status
    }

    fun setSnoozeEndTime(seconds: Long) {
        _snoozeEndTimeSeconds.value = seconds
    }

    override suspend fun fetchSnoozeEndTimeSeconds() {
        // No-op in fake
    }

    override suspend fun snoozeNotifications(
        snoozeDurationHours: String,
        idToken: String,
        snoozeEventTimestampSeconds: Long,
    ) {
        snoozeCount++
        _snoozeRequestStatus.value = SnoozeRequestStatus.SENDING
        _snoozeRequestStatus.value = SnoozeRequestStatus.IDLE
    }
}
