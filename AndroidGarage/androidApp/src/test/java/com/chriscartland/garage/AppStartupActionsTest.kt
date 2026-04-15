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

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.testcommon.FakeDoorFcmRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.RegisterFcmUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupActionsTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createManager(scope: TestScope): FcmRegistrationManager {
        val useCase = object : RegisterFcmUseCase(
            FakeDoorRepository(),
            FakeDoorFcmRepository(),
        ) {
            override suspend operator fun invoke(): AppResult<Unit, ActionError> = AppResult.Success(Unit)
        }
        return FcmRegistrationManager(
            registerFcmUseCase = useCase,
            scope = scope.backgroundScope,
            dispatcher = testDispatcher,
        )
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
    fun onActivityCreated_logsFcmSubscribe() {
        val scope = TestScope(testDispatcher)
        val manager = createManager(scope)
        val appLoggerViewModel = FakeAppLoggerViewModel()
        val actions = AppStartupActions(manager, appLoggerViewModel)

        actions.onActivityCreated()

        assertEquals(
            listOf(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC),
            appLoggerViewModel.loggedKeys,
        )
    }

    @Test
    fun onActivityCreated_returnsAllActions() {
        val scope = TestScope(testDispatcher)
        val manager = createManager(scope)
        val appLoggerViewModel = FakeAppLoggerViewModel()
        val actions = AppStartupActions(manager, appLoggerViewModel)

        val result = actions.onActivityCreated()

        assertEquals(listOf("startFcmRegistration", "logFcmSubscribe"), result)
    }
}
