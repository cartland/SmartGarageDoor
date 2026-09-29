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

package com.chriscartland.garage.fcm

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.InMemoryLocalDoorDataSource
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeFromNotificationTest {
    private val localDoor = InMemoryLocalDoorDataSource()
    private val auth = FakeAuthRepository()
    private val snoozeRepository = FakeSnoozeRepository()
    private val snoozeNotifications = SnoozeNotificationsUseCase(auth, snoozeRepository)

    @Test
    fun theDoorOnDiskIsSnoozedForOneHour() =
        runTest {
            auth.setAuthState(signedIn())
            localDoor.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 1_700_000_000))
            snoozeRepository.setSnoozeResult(AppResult.Success(SnoozeState.Snoozing(1_700_003_600)))

            val action = SnoozeFromNotification.perform(localDoor, snoozeNotifications)

            assertEquals(SnoozeAction.Succeeded.Set(1_700_003_600), action)
            assertEquals(1, snoozeRepository.snoozeCount)
            assertEquals("1h", snoozeRepository.snoozeCalls.single().snoozeDurationHours)
            // Bound to the event on disk: the one the server will judge it against.
            assertEquals(1_700_000_000, snoozeRepository.snoozeCalls.single().snoozeEventTimestampSeconds)
        }

    @Test
    fun anEmptyStoreSnoozesNothingAndSaysSo() =
        runTest {
            auth.setAuthState(signedIn())
            // Nothing on disk: a cold process with no door yet. The use case
            // refuses before any network call, and the card says why.
            val action = SnoozeFromNotification.perform(localDoor, snoozeNotifications)

            assertEquals(SnoozeAction.Failed.MissingData, action)
            assertEquals(0, snoozeRepository.snoozeCount)
        }

    @Test
    fun signedOutSnoozesNothing() =
        runTest {
            auth.setAuthState(AuthState.Unauthenticated)
            localDoor.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 1_700_000_000))

            val action = SnoozeFromNotification.perform(localDoor, snoozeNotifications)

            assertEquals(SnoozeAction.Failed.NotAuthenticated, action)
            assertEquals(0, snoozeRepository.snoozeCount)
        }

    @Test
    fun aServerRefusalIsWordedAsTheSheetWordsIt() =
        runTest {
            auth.setAuthState(signedIn())
            localDoor.insertDoorEvent(DoorEvent(doorPosition = DoorPosition.OPEN, lastChangeTimeSeconds = 1_700_000_000))
            snoozeRepository.setSnoozeResult(AppResult.Error(ActionError.SnoozeEventChanged))

            assertEquals(SnoozeAction.Failed.EventChanged, SnoozeFromNotification.perform(localDoor, snoozeNotifications))
        }

    private fun signedIn(): AuthState = AuthState.Authenticated(User(DisplayName("Chris"), Email("chris@example.com")))
}
