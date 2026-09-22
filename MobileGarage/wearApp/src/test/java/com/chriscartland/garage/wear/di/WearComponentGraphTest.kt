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

import com.chriscartland.garage.data.statuscache.StatusCacheStorage
import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.model.DoorUpdateStrategyId
import com.chriscartland.garage.testcommon.FakeAuthBridge
import com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment
import com.chriscartland.garage.wear.ui.WearLiveVoiceViewModel
import com.chriscartland.garage.wear.ui.WearSimulatedVoiceViewModel
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
                defaultDoorUpdateStrategy = DoorUpdateStrategyId.POLL,
            ),
            signInConfig = WearSignInConfig(googleServerClientId = "test-client-id"),
            appVersion = "wear-test",
            // In-memory, so the real graph can be constructed on the JVM with
            // no Android Context and no file touched. The DataStore-backed
            // implementation is deliberately outside this component — see
            // WearStatusCache.
            statusCacheStorage = InMemoryStatusCacheStorage(),
        )

    /** Map-backed [StatusCacheStorage]; no disk, no Context. */
    private class InMemoryStatusCacheStorage : StatusCacheStorage {
        private val entries = mutableMapOf<String, String>()

        override suspend fun get(key: String): String? = entries[key]

        override suspend fun put(
            key: String,
            value: String,
        ) {
            entries[key] = value
        }

        override suspend fun remove(keys: Set<String>) {
            keys.forEach { entries.remove(it) }
        }
    }

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
        // The settle window and the visibility sink it watches. These two
        // matter more than most: `WearHomeViewModel.onVisible()` WRITES to
        // `appVisibilityState` and READS freshness from `appSettleWindow`, so
        // if either were uncached the watch would report visibility into one
        // object and consult a different, never-started one — leaving
        // `isSettling` pinned at its seed `true` and the "No signal" headline
        // permanently unreachable. Silent, and invisible to every other test.
        assertSame(component.appVisibilityState, component.appVisibilityState)
        assertSame(component.appSettleWindow, component.appSettleWindow)
        // The door snapshot's store and the clock it is stamped with. An
        // uncached store would hand the hydrating data source and any later
        // reader their own instance, and the two would disagree about what
        // had been written.
        assertSame(component.statusSnapshotStore, component.statusSnapshotStore)
        assertSame(component.appClock, component.appClock)
    }

    @Test
    fun viewModelsAreNotSingleton() {
        // ViewModel construction touches viewModelScope, which needs a Main
        // dispatcher in JVM unit tests.
        Dispatchers.setMain(StandardTestDispatcher())
        val component = createComponent()
        assertNotSame(component.wearHomeViewModel, component.wearHomeViewModel)
        assertNotSame(component.wearLiveVoiceViewModel, component.wearLiveVoiceViewModel)
        assertNotSame(component.wearSimulatedVoiceViewModel, component.wearSimulatedVoiceViewModel)
    }

    /**
     * The watch has TWO voice surfaces, and the graph must keep them apart.
     *
     * Since 0.6.0 the one behind the door screen's mic presses the real garage
     * button; the one in settings rehearses against a pretend door. What
     * separates them is that they are different TYPES rather than one type
     * given different collaborators — so there is no `VoiceCommandEnvironment`
     * binding here that could be pointed at the wrong door, and mixing them up
     * would mean changing a declared type at a call site.
     *
     * This test pins the DI half. The other halves:
     * `WearSimulatedVoiceViewModelTest.cannotReachTheRealRemoteButton` (the
     * rehearsal's constructor cannot hold a real-door dependency),
     * `theLiveSurfaceDoesReachTheRealRemoteButton` (the live one does — so a
     * silently-dead feature fails too), and
     * `SimulatedVoiceCommandEnvironmentTest` in `:usecase` (the fake really is
     * inert).
     */
    @Test
    fun theTwoVoiceSurfacesAreDistinctTypes() {
        Dispatchers.setMain(StandardTestDispatcher())
        val component = createComponent()
        val live: WearLiveVoiceViewModel = component.wearLiveVoiceViewModel
        val simulated: WearSimulatedVoiceViewModel = component.wearSimulatedVoiceViewModel
        assertNotSame(
            "The live and simulated voice surfaces must be separate instances; " +
                "sharing one would mean one loop with one door.",
            live as Any,
            simulated as Any,
        )
        assertTrue(
            "The simulated surface must own a SimulatedVoiceCommandEnvironment — " +
                "it is the only door it is allowed to move. Found " +
                "${simulated.demoDoor::class.java.simpleName}.",
            simulated.demoDoor is SimulatedVoiceCommandEnvironment,
        )
    }
}
