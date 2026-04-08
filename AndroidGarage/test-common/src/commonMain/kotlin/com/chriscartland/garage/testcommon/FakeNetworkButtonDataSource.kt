package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.NetworkButtonDataSource
import com.chriscartland.garage.data.NetworkResult

class FakeNetworkButtonDataSource : NetworkButtonDataSource {
    var pushResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
    var snoozeResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
    var fetchSnoozeResult: NetworkResult<Long> = NetworkResult.Success(0L)

    var pushCount = 0
        private set
    var snoozeCount = 0
        private set
    var fetchSnoozeCount = 0
        private set

    override suspend fun pushButton(
        remoteButtonBuildTimestamp: String,
        buttonAckToken: String,
        remoteButtonPushKey: String,
        idToken: String,
    ): NetworkResult<Unit> {
        pushCount++
        return pushResult
    }

    override suspend fun snoozeNotifications(
        buildTimestamp: String,
        remoteButtonPushKey: String,
        idToken: String,
        snoozeDurationHours: String,
        snoozeEventTimestampSeconds: Long,
    ): NetworkResult<Unit> {
        snoozeCount++
        return snoozeResult
    }

    override suspend fun fetchSnoozeEndTimeSeconds(buildTimestamp: String): NetworkResult<Long> {
        fetchSnoozeCount++
        return fetchSnoozeResult
    }
}
