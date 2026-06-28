/*
 * Copyright 2026 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.LoadingResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared contract for [HomeAlertMapper] (ADR-031). Moved from the Android-only
 * `HomeMapperTest.toHomeAlerts` region so the banner-selection logic is guarded
 * on every platform.
 */
class HomeAlertMapperTest {
    private fun event(position: DoorPosition): DoorEvent = DoorEvent(doorPosition = position)

    @Test
    fun cleanStateEmpty() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(emptyList(), alerts)
    }

    @Test
    fun staleOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = true,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(listOf(HomeAlert.Stale), alerts)
    }

    @Test
    fun permissionOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
            isCheckInStale = false,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
        )
        assertEquals(1, alerts.size)
        val pm = alerts[0] as HomeAlert.PermissionMissing
        assertEquals(0, pm.attemptCount)
    }

    @Test
    fun permissionAttemptCountPassesThrough() {
        // The mapper passes the count through verbatim; each UI's resolver
        // appends escalation lines at counts 3+, 4+, 5+.
        val firstAttempt = HomeAlertMapper
            .toHomeAlerts(
                currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 0,
            ).first() as HomeAlert.PermissionMissing
        val manyAttempts = HomeAlertMapper
            .toHomeAlerts(
                currentDoorEvent = LoadingResult.Complete(event(DoorPosition.OPEN)),
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 5,
            ).first() as HomeAlert.PermissionMissing
        assertEquals(0, firstAttempt.attemptCount)
        assertEquals(5, manyAttempts.attemptCount)
    }

    @Test
    fun fetchErrorOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException("boom")),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(1, alerts.size)
        val a = alerts[0] as HomeAlert.FetchError
        assertTrue(a.truncatedException.contains("boom"))
    }

    @Test
    fun errorMessageTruncatedTo500() {
        val long = "x".repeat(2_000)
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException(long)),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        val a = alerts[0] as HomeAlert.FetchError
        assertTrue(a.truncatedException.length <= 500, "Got len=${a.truncatedException.length}")
    }

    @Test
    fun allThreeInDocumentedOrder() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Error(RuntimeException("boom")),
            isCheckInStale = true,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
        )
        assertEquals(3, alerts.size)
        assertTrue(alerts[0] is HomeAlert.Stale, "[0] should be Stale")
        assertTrue(alerts[1] is HomeAlert.PermissionMissing, "[1] should be PermissionMissing")
        assertTrue(alerts[2] is HomeAlert.FetchError, "[2] should be FetchError")
    }

    @Test
    fun loadingStateDoesNotEmitFetchError() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            currentDoorEvent = LoadingResult.Loading(null),
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
        )
        assertEquals(emptyList(), alerts)
    }
}
