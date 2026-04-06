/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.DoorFcmTopic
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.fcm.DoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeDoorFcmRepository : DoorFcmRepository {
    var fetchStatusResult: DoorFcmState = DoorFcmState.Unknown
    var registerResult: DoorFcmState = DoorFcmState.NotRegistered
    var deregisterResult: DoorFcmState = DoorFcmState.NotRegistered
    var registerCount = 0
    var lastRegisteredTopic: DoorFcmTopic? = null

    override suspend fun fetchStatus(): DoorFcmState = fetchStatusResult

    override suspend fun registerDoor(fcmTopic: DoorFcmTopic): DoorFcmState {
        registerCount++
        lastRegisteredTopic = fcmTopic
        return registerResult
    }

    override suspend fun deregisterDoor(): DoorFcmState = deregisterResult
}

class DoorFcmStateToRegistrationStatusTest {
    @Test
    fun registeredMapsToRegistered() {
        val state = DoorFcmState.Registered(DoorFcmTopic("test"))
        assertEquals(FcmRegistrationStatus.REGISTERED, state.toRegistrationStatus())
    }

    @Test
    fun notRegisteredMapsToNotRegistered() {
        assertEquals(
            FcmRegistrationStatus.NOT_REGISTERED,
            DoorFcmState.NotRegistered.toRegistrationStatus(),
        )
    }

    @Test
    fun unknownMapsToUnknown() {
        assertEquals(FcmRegistrationStatus.UNKNOWN, DoorFcmState.Unknown.toRegistrationStatus())
    }
}

class RegisterFcmUseCaseTest {
    private lateinit var fakeDoor: FakeDoorRepository
    private lateinit var fakeFcm: FakeDoorFcmRepository
    private lateinit var useCase: RegisterFcmUseCase

    @Before
    fun setup() {
        fakeDoor = FakeDoorRepository()
        fakeFcm = FakeDoorFcmRepository()
        useCase = RegisterFcmUseCase(fakeDoor, fakeFcm)
    }

    @Test
    fun registerSucceedsWithBuildTimestamp() =
        runTest {
            fakeDoor.buildTimestamp = "Sat Mar 13 14:45:00 2021"
            fakeFcm.registerResult =
                DoorFcmState.Registered(DoorFcmTopic("door_open-Sat.Mar.13.14.45.00.2021"))

            val result = useCase()

            assertEquals(FcmRegistrationStatus.REGISTERED, result)
            assertEquals(1, fakeFcm.registerCount)
        }

    @Test
    fun registerFailsWithNullBuildTimestamp() =
        runTest {
            fakeDoor.buildTimestamp = null
            val result = useCase()

            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, result)
            assertEquals(0, fakeFcm.registerCount)
        }

    @Test
    fun registerConvertsTimestampToTopic() =
        runTest {
            fakeDoor.buildTimestamp = "Sat Mar 13 14:45:00 2021"
            fakeFcm.registerResult = DoorFcmState.Registered(DoorFcmTopic("test"))

            useCase()

            assertEquals("door_open-Sat.Mar.13.14.45.00.2021", fakeFcm.lastRegisteredTopic?.string)
        }

    @Test
    fun registerReturnsNotRegisteredOnFailure() =
        runTest {
            fakeDoor.buildTimestamp = "timestamp"
            fakeFcm.registerResult = DoorFcmState.NotRegistered

            val result = useCase()

            assertEquals(FcmRegistrationStatus.NOT_REGISTERED, result)
        }
}
