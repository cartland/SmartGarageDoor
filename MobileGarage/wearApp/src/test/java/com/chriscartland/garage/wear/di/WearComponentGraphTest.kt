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
import com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
        // Singleton so the demo door keeps its state across visits to the
        // voice screen, and so a fake transit is not orphaned by a new instance.
        assertSame(component.voiceCommandEnvironment, component.voiceCommandEnvironment)
    }

    @Test
    fun viewModelsAreNotSingleton() {
        // ViewModel construction touches viewModelScope, which needs a Main
        // dispatcher in JVM unit tests.
        Dispatchers.setMain(StandardTestDispatcher())
        val component = createComponent()
        assertNotSame(component.wearHomeViewModel, component.wearHomeViewModel)
        assertNotSame(component.wearVoiceViewModel, component.wearVoiceViewModel)
    }

    /**
     * The watch's voice surface is an experiment and must never reach the real
     * garage door. That is guaranteed structurally rather than by a runtime
     * check, and this is the DI half of it: the only [VoiceCommandEnvironment]
     * the graph can hand to [WearVoiceViewModel] is the simulated one, whose
     * `pressButton` touches nothing but its own in-memory StateFlow.
     *
     * The other halves: `WearVoiceViewModelTest.cannotReachTheRealRemoteButton`
     * (the ViewModel has no remote-button dependency at all) and
     * `SimulatedVoiceCommandEnvironmentTest` in `:usecase` (the fake really is
     * inert). Swapping this binding for a real environment fails here, loudly,
     * with the reason attached.
     */
    @Test
    fun theOnlyVoiceEnvironmentIsSimulated() {
        val component = createComponent()
        val environment = component.voiceCommandEnvironment
        assertTrue(
            "The Wear voice demo must be wired to SimulatedVoiceCommandEnvironment, " +
                "but the graph provided ${environment::class.java.simpleName}. The watch " +
                "voice surface is a simulation; the remote button stays reachable only " +
                "by holding the door on the hero screen.",
            environment is SimulatedVoiceCommandEnvironment,
        )
    }
}
