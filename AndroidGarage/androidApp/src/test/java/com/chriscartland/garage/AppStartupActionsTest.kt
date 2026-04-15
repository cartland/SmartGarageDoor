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

package com.chriscartland.garage

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.DoorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupActionsTest {
    private class FakeDoorViewModel : DoorViewModel {
        var registerFcmCalled = false

        override val fcmRegistrationStatus =
            MutableStateFlow(FcmRegistrationStatus.UNKNOWN)
        override val currentDoorEvent: StateFlow<LoadingResult<DoorEvent?>> =
            MutableStateFlow(LoadingResult.Loading(null))
        override val recentDoorEvents: StateFlow<LoadingResult<List<DoorEvent>>> =
            MutableStateFlow(LoadingResult.Loading(listOf()))

        override fun fetchFcmRegistrationStatus() = Unit

        override fun registerFcm() {
            registerFcmCalled = true
        }

        override fun deregisterFcm() = Unit

        override fun fetchCurrentDoorEvent() = Unit

        override fun fetchRecentDoorEvents() = Unit
    }

    private class FakeAppLoggerViewModel : AppLoggerViewModel {
        val loggedKeys = mutableListOf<String>()

        override fun log(key: String) {
            loggedKeys.add(key)
        }

        override val initCurrentDoorCount = MutableStateFlow(0L)
        override val initRecentDoorCount = MutableStateFlow(0L)
        override val userFetchCurrentDoorCount = MutableStateFlow(0L)
        override val userFetchRecentDoorCount = MutableStateFlow(0L)
        override val fcmReceivedDoorCount = MutableStateFlow(0L)
        override val fcmSubscribeTopicCount = MutableStateFlow(0L)
        override val exceededExpectedTimeWithoutFcmCount = MutableStateFlow(0L)
        override val timeWithoutFcmInExpectedRangeCount = MutableStateFlow(0L)
    }

    @Test
    fun onActivityCreated_registersFcm() {
        val doorViewModel = FakeDoorViewModel()
        val appLoggerViewModel = FakeAppLoggerViewModel()
        val actions = AppStartupActions(doorViewModel, appLoggerViewModel)

        actions.onActivityCreated()

        assertTrue("registerFcm should be called", doorViewModel.registerFcmCalled)
    }

    @Test
    fun onActivityCreated_logsFcmSubscribe() {
        val doorViewModel = FakeDoorViewModel()
        val appLoggerViewModel = FakeAppLoggerViewModel()
        val actions = AppStartupActions(doorViewModel, appLoggerViewModel)

        actions.onActivityCreated()

        assertEquals(
            listOf(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC),
            appLoggerViewModel.loggedKeys,
        )
    }

    @Test
    fun onActivityCreated_returnsAllActions() {
        val doorViewModel = FakeDoorViewModel()
        val appLoggerViewModel = FakeAppLoggerViewModel()
        val actions = AppStartupActions(doorViewModel, appLoggerViewModel)

        val result = actions.onActivityCreated()

        assertEquals(listOf("registerFcm", "logFcmSubscribe"), result)
    }
}
