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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.SnoozeState
import com.chriscartland.garage.testcommon.FakeSnoozeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComputeEffectiveSnoozeStateUseCaseTest {
    @Test
    fun activeSnoozePassesThrough() =
        runTest {
            val repo = FakeSnoozeRepository().apply { setSnoozeState(SnoozeState.Snoozing(2_000L)) }
            val clock = ExpiryTestLiveClock(MutableStateFlow(1_000L))

            val useCase = ComputeEffectiveSnoozeStateUseCase(repo, clock, backgroundScope)

            assertEquals(SnoozeState.Snoozing(2_000L), useCase().first { it !is SnoozeState.Loading })
        }

    @Test
    fun clockPassingEndTimeFlipsToNotSnoozing() =
        runTest {
            // The pre-existing iOS bug this pins: an on-screen "Snoozing
            // until 3 PM" must flip at 3 PM with no fetch and no poll.
            val repo = FakeSnoozeRepository().apply { setSnoozeState(SnoozeState.Snoozing(2_000L)) }
            val clockFlow = MutableStateFlow(1_000L)
            val useCase = ComputeEffectiveSnoozeStateUseCase(repo, ExpiryTestLiveClock(clockFlow), backgroundScope)

            assertEquals(SnoozeState.Snoozing(2_000L), useCase().first { it !is SnoozeState.Loading })

            clockFlow.value = 2_000L

            assertEquals(SnoozeState.NotSnoozing, useCase().first { it == SnoozeState.NotSnoozing })
        }

    @Test
    fun initialValueAppliesTheFlipSynchronously() =
        runTest {
            // A fresh subscriber's synchronous `.value` read must never show
            // an already-expired snooze — no wrong first frame.
            val repo = FakeSnoozeRepository().apply { setSnoozeState(SnoozeState.Snoozing(500L)) }
            val clock = ExpiryTestLiveClock(MutableStateFlow(1_000L))

            val useCase = ComputeEffectiveSnoozeStateUseCase(repo, clock, backgroundScope)

            assertEquals(SnoozeState.NotSnoozing, useCase().value)
        }

    @Test
    fun loadingAndNotSnoozingPassThroughUnchanged() =
        runTest {
            val repo = FakeSnoozeRepository()
            val clock = ExpiryTestLiveClock(MutableStateFlow(1_000L))

            val useCase = ComputeEffectiveSnoozeStateUseCase(repo, clock, backgroundScope)
            assertEquals(SnoozeState.Loading, useCase().value)

            repo.setSnoozeState(SnoozeState.NotSnoozing)
            assertEquals(SnoozeState.NotSnoozing, useCase().first { it == SnoozeState.NotSnoozing })
        }
}

private class ExpiryTestLiveClock(
    private val flow: StateFlow<Long>,
) : LiveClock {
    override val nowEpochSeconds: StateFlow<Long> = flow

    override fun start() {}
}
