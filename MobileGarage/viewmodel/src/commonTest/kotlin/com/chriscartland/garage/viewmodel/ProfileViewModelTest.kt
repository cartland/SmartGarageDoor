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

package com.chriscartland.garage.viewmodel

import com.chriscartland.garage.domain.coroutines.AppClock
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FeatureAllowlist
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationUIOption
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallAction
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeFeatureAllowlistRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.FakeWearCompanionRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import com.chriscartland.garage.usecase.AppSettingsUseCase
import com.chriscartland.garage.usecase.ComputeEffectiveSnoozeStateUseCase
import com.chriscartland.garage.usecase.DefaultLiveClock
import com.chriscartland.garage.usecase.FetchSnoozeStatusUseCase
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.ObserveAuthStateUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.ObserveFeatureAccessUseCase
import com.chriscartland.garage.usecase.ObserveWatchAppStatusUseCase
import com.chriscartland.garage.usecase.RequestWatchAppInstallUseCase
import com.chriscartland.garage.usecase.RevalidateSnoozeStatusUseCase
import com.chriscartland.garage.usecase.SignInWithGoogleUseCase
import com.chriscartland.garage.usecase.SignOutUseCase
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var snoozeRepository: FakeSnoozeRepository
    private lateinit var featureAllowlistRepository: FakeFeatureAllowlistRepository
    private lateinit var appLoggerRepository: FakeAppLoggerRepository
    private lateinit var wearCompanionRepository: FakeWearCompanionRepository

    private val testUser = User(
        name = DisplayName("User"),
        email = Email("user@example.com"),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        doorRepository = FakeDoorRepository()
        snoozeRepository = FakeSnoozeRepository()
        featureAllowlistRepository = FakeFeatureAllowlistRepository()
        appLoggerRepository = FakeAppLoggerRepository()
        wearCompanionRepository = FakeWearCompanionRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(authState: AuthState = AuthState.Unauthenticated): DefaultProfileViewModel {
        authRepository.setAuthState(authState)
        return DefaultProfileViewModel(
            observeAuthState = ObserveAuthStateUseCase(authRepository),
            computeEffectiveSnoozeState = ComputeEffectiveSnoozeStateUseCase(
                snoozeRepository = snoozeRepository,
                liveClock = DefaultLiveClock(
                    clock = AppClock { 0L },
                    scope = CoroutineScope(SupervisorJob() + testDispatcher),
                    dispatcher = testDispatcher,
                ),
                applicationScope = CoroutineScope(SupervisorJob() + testDispatcher),
            ),
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            observeFeatureAccessUseCase = ObserveFeatureAccessUseCase(featureAllowlistRepository),
            observeWatchAppStatusUseCase = ObserveWatchAppStatusUseCase(wearCompanionRepository),
            requestWatchAppInstallUseCase = RequestWatchAppInstallUseCase(wearCompanionRepository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository),
            signOutUseCase = SignOutUseCase(authRepository),
            fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
            revalidateSnoozeStatusUseCase = RevalidateSnoozeStatusUseCase(snoozeRepository),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(
                authRepository,
                snoozeRepository,
            ),
            logAppEvent = LogAppEventUseCase(
                appLoggerRepository,
                FakeDiagnosticsCountersRepository(),
            ),
            appSettings = AppSettingsUseCase(FakeAppSettingsRepository()),
            dispatchers = TestDispatcherProvider(testDispatcher),
        ).also {
            testDispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun authStatePassesThroughFromRepository() =
        runTest {
            val viewModel = createViewModel(authState = AuthState.Authenticated(testUser))
            assertTrue(viewModel.authState.value is AuthState.Authenticated)
        }

    @Test
    fun snoozeStatePassesThroughFromRepository() =
        runTest {
            val viewModel = createViewModel()
            snoozeRepository.setSnoozeState(SnoozeState.NotSnoozing)
            advanceUntilIdle()

            assertEquals(SnoozeState.NotSnoozing, viewModel.snoozeState.value)
        }

    @Test
    fun fetchSnoozeStatusCallsRepository() =
        runTest {
            val viewModel = createViewModel()

            viewModel.fetchSnoozeStatus()
            advanceUntilIdle()

            assertEquals(1, snoozeRepository.fetchCount)
        }

    @Test
    fun signInForwardsToUseCase() =
        runTest {
            val viewModel = createViewModel()

            viewModel.signInWithGoogle(GoogleIdToken("test"))
            advanceUntilIdle()

            assertTrue(
                appLoggerRepository.loggedKeys.any {
                    it == AppLoggerKeys.BEGIN_GOOGLE_SIGN_IN
                },
                "BEGIN_GOOGLE_SIGN_IN should be logged on sign-in",
            )
        }

    @Test
    fun signOutForwardsToUseCase() =
        runTest {
            val viewModel = createViewModel(authState = AuthState.Authenticated(testUser))

            viewModel.signOut()
            advanceUntilIdle()

            // After signOut the fake should report Unauthenticated.
            assertEquals(AuthState.Unauthenticated, authRepository.authState.value)
        }

    @Test
    fun functionListAccessReflectsAllowlist() =
        runTest {
            featureAllowlistRepository.setAllowlist(
                FeatureAllowlist(functionList = true, developer = true),
            )
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            advanceUntilIdle()

            assertEquals(true, viewModel.functionListAccess.value)
        }

    @Test
    fun functionListAccessNullWhenAllowlistMissing() =
        runTest {
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            featureAllowlistRepository.setAllowlist(null)
            advanceUntilIdle()

            assertNull(viewModel.functionListAccess.value)
        }

    @Test
    fun developerAccessReflectsAllowlist() =
        runTest {
            featureAllowlistRepository.setAllowlist(
                FeatureAllowlist(functionList = false, developer = true),
            )
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            advanceUntilIdle()

            assertEquals(true, viewModel.developerAccess.value)
        }

    @Test
    fun developerAccessNullWhenAllowlistMissing() =
        runTest {
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            featureAllowlistRepository.setAllowlist(null)
            advanceUntilIdle()

            assertNull(viewModel.developerAccess.value)
        }

    @Test
    fun snoozeOpenDoorsTransitionsThroughSendingThenSucceeded() =
        runTest {
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            snoozeRepository.setSnoozeResult(AppResult.Success(SnoozeState.NotSnoozing))
            doorRepository.setCurrentDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPEN,
                    lastChangeTimeSeconds = 900L,
                ),
            )
            advanceUntilIdle()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            // Don't `advanceUntilIdle` — that fires the 10s reset delay too.
            testDispatcher.scheduler.runCurrent()

            assertTrue(
                viewModel.snoozeAction.value is SnoozeAction.Succeeded,
                "Expected Succeeded after the snooze call returns; got " +
                    "${viewModel.snoozeAction.value}",
            )
        }

    // Pins the ActionError.SnoozeEventChanged → SnoozeAction.Failed.EventChanged
    // mapping. The variant exists so the UI can dispatch a specific snackbar
    // for the "door state changed during sheet open" case instead of the
    // generic NetworkError copy. See docs/SNOOZE_BEHAVIOR.md.
    @Test
    fun snoozeFailureSnoozeEventChangedMapsToFailedEventChanged() =
        runTest {
            val viewModel = createViewModel(
                authState = AuthState.Authenticated(testUser),
            )
            snoozeRepository.setSnoozeResult(AppResult.Error(ActionError.SnoozeEventChanged))
            doorRepository.setCurrentDoorEvent(
                DoorEvent(
                    doorPosition = DoorPosition.OPENING,
                    lastChangeTimeSeconds = 900L,
                ),
            )
            advanceUntilIdle()

            viewModel.snoozeOpenDoorsNotifications(SnoozeDurationUIOption.OneHour)
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                SnoozeAction.Failed.EventChanged,
                viewModel.snoozeAction.value,
                "ActionError.SnoozeEventChanged must map to Failed.EventChanged",
            )
        }

    @Test
    fun watchAppStatusPassesThroughFromRepository() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(WatchAppStatus.Unknown, viewModel.watchAppStatus.value)

            wearCompanionRepository.setWatchAppStatus(WatchAppStatus.WatchNeedsApp)
            advanceUntilIdle()
            assertEquals(WatchAppStatus.WatchNeedsApp, viewModel.watchAppStatus.value)

            wearCompanionRepository.setWatchAppStatus(WatchAppStatus.InstalledOnWatch)
            advanceUntilIdle()
            assertEquals(WatchAppStatus.InstalledOnWatch, viewModel.watchAppStatus.value)
        }

    @Test
    fun installOnWatchSuccessSetsOpenedThenAutoResets() =
        runTest {
            val viewModel = createViewModel()
            wearCompanionRepository.setInstallResult(WatchInstallResult.OpenedOnWatch)

            viewModel.installOnWatch()
            // Don't `advanceUntilIdle` — that fires the 10s reset delay too.
            testDispatcher.scheduler.runCurrent()

            assertEquals(WatchInstallAction.OpenedOnWatch, viewModel.watchInstallAction.value)
            assertEquals(1, wearCompanionRepository.installRequestCount)

            advanceUntilIdle()
            assertEquals(
                WatchInstallAction.Idle,
                viewModel.watchInstallAction.value,
                "Action must auto-reset to Idle after the reset delay",
            )
        }

    @Test
    fun installOnWatchFailureSetsFailed() =
        runTest {
            val viewModel = createViewModel()
            wearCompanionRepository.setInstallResult(WatchInstallResult.Failed)

            viewModel.installOnWatch()
            testDispatcher.scheduler.runCurrent()

            assertEquals(WatchInstallAction.Failed, viewModel.watchInstallAction.value)
        }

    @Test
    fun installOnWatchNoWatchReachableMapsToFailed() =
        runTest {
            val viewModel = createViewModel()
            wearCompanionRepository.setInstallResult(WatchInstallResult.NoWatchReachable)

            viewModel.installOnWatch()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                WatchInstallAction.Failed,
                viewModel.watchInstallAction.value,
                "NoWatchReachable surfaces as Failed so the UI falls back to the phone store",
            )
        }
}
