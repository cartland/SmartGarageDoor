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

package com.chriscartland.garage.wear.di

import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.testcommon.FakeAuthBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Runtime identity tests for [WearComponent]'s `@WearSingleton` caching —
 * the Wear analog of the phone's `ComponentGraphTest`, guarding the
 * android/170 class of bug (a provider silently losing its singleton cache).
 *
 * The component is constructed with a [FakeAuthBridge] and an unroutable
 * loopback base URL, so no test can reach the real server. The eager
 * server-config fetch hits connection-refused and is swallowed by
 * `CachedServerConfigRepository`'s error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearComponentGraphTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createComponent(): WearComponent =
        WearComponent::class.create(
            authBridge = FakeAuthBridge(),
            appConfig = AppConfig(
                baseUrl = "http://127.0.0.1:9/",
                recentEventCount = 10,
                serverConfigKey = "test-key",
                snoozeNotificationsOption = false,
                remoteButtonPushEnabled = false,
            ),
            signInConfig = WearSignInConfig(googleServerClientId = "test-client-id"),
            appVersion = "wear-test",
        )

    @Test
    fun singletonProvidersReturnSameInstance() {
        val component = createComponent()
        assertSame(component.applicationScope, component.applicationScope)
        assertSame(component.dispatcherProvider, component.dispatcherProvider)
        assertSame(component.httpClient, component.httpClient)
        assertSame(component.appLoggerRepository, component.appLoggerRepository)
        assertSame(component.authRepository, component.authRepository)
        assertSame(component.serverConfigRepository, component.serverConfigRepository)
        assertSame(component.doorRepository, component.doorRepository)
        assertSame(component.remoteButtonRepository, component.remoteButtonRepository)
        assertSame(component.localDoorDataSource, component.localDoorDataSource)
        assertSame(component.networkDoorDataSource, component.networkDoorDataSource)
        assertSame(component.networkConfigDataSource, component.networkConfigDataSource)
        assertSame(component.networkButtonDataSource, component.networkButtonDataSource)
    }

    @Test
    fun viewModelIsNotSingleton() {
        // ViewModel construction touches viewModelScope, which needs a Main
        // dispatcher in JVM unit tests.
        Dispatchers.setMain(StandardTestDispatcher())
        val component = createComponent()
        assertNotSame(component.wearHomeViewModel, component.wearHomeViewModel)
    }
}
