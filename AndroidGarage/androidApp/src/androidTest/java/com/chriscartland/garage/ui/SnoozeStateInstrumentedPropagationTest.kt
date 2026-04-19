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

package com.chriscartland.garage.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.data.repository.CachedServerConfigRepository
import com.chriscartland.garage.data.repository.NetworkSnoozeRepository
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.usecase.DefaultRemoteButtonViewModel
import com.chriscartland.garage.usecase.EnsureFreshIdTokenUseCase
import com.chriscartland.garage.usecase.FetchSnoozeStatusUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveSnoozeStateUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented test reproducing (or ruling out) the android/167 snooze card
 * title bug.
 *
 * Bug symptoms on production android/167:
 *   1. Profile card shows "Door notifications enabled".
 *   2. User taps snooze → 1 hour → Save.
 *   3. Small overlay text shows "Saved! Snoozing until 8:30pm" (optimistic).
 *   4. Main card title stays "Door notifications enabled" — should have
 *      flipped to "Door notifications snoozed until 8:30pm".
 *   5. Force-close + relaunch → card shows "snoozed until 8:30pm". Server
 *      persisted correctly; only in-memory propagation failed on device.
 *
 * This test wires the REAL [DefaultRemoteButtonViewModel] with the REAL
 * [NetworkSnoozeRepository] (backed by fake network data sources) into a
 * minimal Composable that invokes [SnoozeNotificationCard] directly.
 *
 * If the test passes: android/167 is not reproducible in a pure-Android
 * instrumented environment, pointing to a device-specific effect (renderer,
 * recompose scheduler, process state, or some Compose/Nav lifecycle
 * interaction we haven't yet exercised).
 *
 * If the test fails: we've reproduced the bug on-device under test control.
 *
 * Sibling JVM-only proof: `RealNetworkSnoozeRepositoryPropagationTest` in the
 * `:usecase` module verifies the same transition without Compose.
 */
class SnoozeStateInstrumentedPropagationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val validConfig = NetworkResult.Success(
        ServerConfig(
            buildTimestamp = "test-build",
            remoteButtonBuildTimestamp = "test-build",
            remoteButtonPushKey = "test-key",
        ),
    )

    private val authenticatedUser = User(
        name = DisplayName("Test"),
        email = Email("test@test.com"),
        idToken = FirebaseIdToken(idToken = "tok", exp = Long.MAX_VALUE),
    )

    /**
     * Real DispatcherProvider using Main for all dispatchers so the Compose
     * recomposition happens on the test's main thread and stays in sync with
     * the VM's updates.
     */
    private val mainDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.Main
        override val default: CoroutineDispatcher get() = Dispatchers.Main
    }

    private fun buildVm(
        buttonDs: FakeNetworkButtonDataSource,
        configDs: FakeNetworkConfigDataSource,
        externalScope: CoroutineScope,
        currentTimeSeconds: Long,
    ): DefaultRemoteButtonViewModel {
        val authRepository = FakeAuthRepository().apply {
            setAuthState(AuthState.Authenticated(user = authenticatedUser))
        }
        val doorRepository = FakeDoorRepository().apply {
            // UseCase reads lastChangeTimeSeconds; null → MissingData.
            setCurrentDoorEvent(DoorEvent(lastChangeTimeSeconds = 1000L))
        }
        val remoteButtonRepository = FakeRemoteButtonRepository()
        val snoozeRepository = NetworkSnoozeRepository(
            networkButtonDataSource = buttonDs,
            serverConfigRepository = CachedServerConfigRepository(configDs, "test-key"),
            snoozeNotificationsOption = true,
            currentTimeSeconds = { currentTimeSeconds },
            externalScope = externalScope,
        )
        val ensureFreshIdToken = EnsureFreshIdTokenUseCase(authRepository)
        return DefaultRemoteButtonViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            dispatchers = mainDispatchers,
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(
                ensureFreshIdToken,
                authRepository,
                remoteButtonRepository,
            ),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                ensureFreshIdToken,
                authRepository,
                snoozeRepository,
            ),
            fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
            observeSnoozeStateUseCase = ObserveSnoozeStateUseCase(snoozeRepository),
            appVersion = "test",
        )
    }

    /**
     * Main card title must flip from "Door notifications enabled" to
     * "Door notifications snoozed until …" after a successful snooze POST.
     */
    @Test
    fun snoozeSuccessFlipsCardTitleFromEnabledToSnoozedUntil() {
        val buttonDs = FakeNetworkButtonDataSource().apply {
            setFetchSnoozeResult(NetworkResult.Success(0L))
            // far future so time formatting yields a deterministic-ish string.
            setSnoozeResult(NetworkResult.Success(9_999_999_999L))
        }
        val configDs = FakeNetworkConfigDataSource().apply {
            setServerConfigResult(validConfig)
        }
        val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val vm = buildVm(
            buttonDs = buttonDs,
            configDs = configDs,
            externalScope = externalScope,
            currentTimeSeconds = 1_000L,
        )

        composeTestRule.setContent {
            val snoozeState by vm.snoozeState.collectAsState()
            val snoozeAction by vm.snoozeAction.collectAsState()
            SnoozeNotificationCard(
                snoozeState = snoozeState,
                snoozeAction = snoozeAction,
            )
        }

        // Let repo init + VM observer settle.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Door notifications enabled")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Door notifications enabled").assertIsDisplayed()

        // Trigger the snooze. The VM uses viewModelScope.launch internally;
        // UnconfinedTestDispatcher is not applicable here so we just drive the
        // coroutine and wait for UI.
        runBlocking(Dispatchers.Main) {
            vm.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Door notifications snoozed until", substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Door notifications snoozed until", substring = true)
            .assertIsDisplayed()
        // The "enabled" title should be gone — this is the assertion that
        // would fail if the android/167 bug reproduced here.
        composeTestRule
            .onNodeWithText("Door notifications enabled")
            .assertDoesNotExist()

        externalScope.cancel()
    }
}
