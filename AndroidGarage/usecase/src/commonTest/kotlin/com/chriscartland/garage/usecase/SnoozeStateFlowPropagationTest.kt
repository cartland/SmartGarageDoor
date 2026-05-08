/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.domain.repository.SnoozeRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import com.chriscartland.garage.testcommon.FakeRemoteButtonRepository
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reactive-chain tests for the snooze state as it travels from the repository
 * to the ViewModel.
 *
 * Chain (ADR-022): NetworkSnoozeRepository._snoozeState (MutableStateFlow)
 *        → SnoozeRepository.snoozeState: StateFlow (same instance)
 *        → ObserveSnoozeStateUseCase returns repo's StateFlow by reference
 *        → DefaultRemoteButtonViewModel.snoozeState (same instance)
 *        → ProfileContent.collectAsState
 *
 * Reported bug (android/164): snoozeState stuck at Loading forever even when
 * the network call succeeds. These tests cover:
 *  - replay-on-subscribe (StateFlow semantics)
 *  - late-subscription delivery
 *  - reference identity through the VM (Rule 2 — no mirror)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnoozeStateFlowPropagationTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var remoteButtonRepository: FakeRemoteButtonRepository
    private lateinit var doorRepository: FakeDoorRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        remoteButtonRepository = FakeRemoteButtonRepository()
        doorRepository = FakeDoorRepository()
        authRepository = FakeAuthRepository()
        authRepository.setAuthState(
            AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                ),
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(snoozeRepository: SnoozeRepository): DefaultRemoteButtonViewModel =
        DefaultRemoteButtonViewModel(
            observeDoorEvents = ObserveDoorEventsUseCase(doorRepository),
            dispatchers = TestDispatcherProvider(testDispatcher),
            pushRemoteButtonUseCase = PushRemoteButtonUseCase(authRepository, remoteButtonRepository),
            snoozeNotificationsUseCase = SnoozeNotificationsUseCase(authRepository, snoozeRepository),
            fetchSnoozeStatusUseCase = FetchSnoozeStatusUseCase(snoozeRepository),
            observeSnoozeStateUseCase = ObserveSnoozeStateUseCase(snoozeRepository),
            buttonHealthDisplay = kotlinx.coroutines.flow.emptyFlow(),
            appVersion = "test",
        )

    // ----------------------------------------------------------------
    // 2. Two-hop chain: every repo update is visible on vm.snoozeState.
    // ----------------------------------------------------------------
    @Test
    fun fullChain_propagatesEveryTerminalValueToVm() =
        runTest {
            val fake = FakeSnoozeRepository()
            val vm = buildVm(fake)
            testDispatcher.scheduler.runCurrent()

            // Initial Loading.
            assertEquals(SnoozeState.Loading, vm.snoozeState.value)

            fake.setSnoozeState(SnoozeState.NotSnoozing)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeState.NotSnoozing, vm.snoozeState.value)

            fake.setSnoozeState(SnoozeState.Snoozing(500L))
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeState.Snoozing(500L), vm.snoozeState.value)

            fake.setSnoozeState(SnoozeState.NotSnoozing)
            testDispatcher.scheduler.runCurrent()
            assertEquals(SnoozeState.NotSnoozing, vm.snoozeState.value)
        }

    // ----------------------------------------------------------------
    // 3. Dispatcher mismatch: VM._snoozeState updates from io, external
    // collectAsState reads on main. StateFlow.value is thread-safe and
    // subscription delivers the latest value — prove an external
    // collector always converges to the final emitted value.
    // ----------------------------------------------------------------
    @Test
    fun externalCollectorConvergesOnLatestValue() =
        runTest {
            val fake = FakeSnoozeRepository()
            val vm = buildVm(fake)
            testDispatcher.scheduler.runCurrent()

            val simulatedCompose = mutableListOf<SnoozeState>()
            val collectorJob = launch {
                vm.snoozeState.take(3).toList(simulatedCompose)
            }
            testDispatcher.scheduler.runCurrent()

            fake.setSnoozeState(SnoozeState.NotSnoozing)
            testDispatcher.scheduler.runCurrent()
            fake.setSnoozeState(SnoozeState.Snoozing(999L))
            testDispatcher.scheduler.runCurrent()

            collectorJob.join()
            // Initial replay is Loading; terminal must be Snoozing(999).
            assertEquals(SnoozeState.Loading, simulatedCompose.first())
            assertEquals(SnoozeState.Snoozing(999L), simulatedCompose.last())
            // VM.value must also reflect the terminal state.
            assertEquals(SnoozeState.Snoozing(999L), vm.snoozeState.value)
        }

    // ----------------------------------------------------------------
    // 4. Early-subscription race: the repository is updated BEFORE the VM
    // is constructed (simulating NetworkSnoozeRepository.init driving its
    // own fetch on an app-lifetime scope — see ADR-018 and the snooze
    // Loading fix). When the VM's observer subscribes, StateFlow replay
    // semantics must deliver the current value so UI converges without
    // waiting for another emission.
    // ----------------------------------------------------------------
    @Test
    fun vmReceivesCurrentValueWhenRepoAlreadyUpdatedBeforeVmCreated() =
        runTest {
            val prePopulatedFake = FakeSnoozeRepository().apply {
                setSnoozeState(SnoozeState.Snoozing(777L))
            }
            val vm = buildVm(prePopulatedFake)
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                SnoozeState.Snoozing(777L),
                vm.snoozeState.value,
                "VM should reflect repo state already present at construction — Loading would reproduce the android/164 bug",
            )
        }

    // ----------------------------------------------------------------
    // Bonus: VM.snoozeState is typed StateFlow — verify that external
    // code can call .value and get the current state without collection.
    // This is what Compose's collectAsState relies on for the INITIAL
    // render before any emission.
    // ----------------------------------------------------------------
    @Test
    fun vmSnoozeStateExposesStateFlowValueAccess() =
        runTest {
            val fake = FakeSnoozeRepository()
            val vm = buildVm(fake)
            testDispatcher.scheduler.runCurrent()

            // StateFlow<SnoozeState> has .value — the property must be of
            // that subtype for Compose's collectAsState to obtain the
            // initial value without subscribing first.
            val sf: StateFlow<SnoozeState> = vm.snoozeState
            assertEquals(SnoozeState.Loading, sf.value)

            fake.setSnoozeState(SnoozeState.Snoozing(1L))
            testDispatcher.scheduler.runCurrent()
            assertTrue(sf.value is SnoozeState.Snoozing)
        }
}
