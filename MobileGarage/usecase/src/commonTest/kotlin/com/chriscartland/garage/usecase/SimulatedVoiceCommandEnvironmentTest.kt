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

import com.chriscartland.garage.domain.model.VoiceIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedVoiceCommandEnvironmentTest {
    @Test
    fun pressMovesThenSettlesOnCommandedSide() =
        runTest {
            val env = SimulatedVoiceCommandEnvironment(scope = backgroundScope)
            assertEquals(VoiceDoorState.CLOSED, env.doorState.value)

            var pressed = false
            backgroundScope.launch { pressed = env.pressButton(VoiceIntent.OPEN) }

            advanceTimeBy(SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS)
            runCurrent()
            assertTrue(pressed)
            assertEquals(VoiceDoorState.MOVING, env.doorState.value)

            advanceTimeBy(SimulatedVoiceCommandEnvironment.TRANSIT_MS)
            runCurrent()
            assertEquals(VoiceDoorState.OPEN, env.doorState.value)
        }

    @Test
    fun manualSetCancelsTransit() =
        runTest {
            val env = SimulatedVoiceCommandEnvironment(scope = backgroundScope)
            backgroundScope.launch { env.pressButton(VoiceIntent.OPEN) }
            advanceTimeBy(SimulatedVoiceCommandEnvironment.PRESS_DELAY_MS)
            runCurrent()
            assertEquals(VoiceDoorState.MOVING, env.doorState.value)

            // The user places the door by hand mid-transit; the pending
            // settle must not fire later and overwrite it.
            env.setDoorState(VoiceDoorState.CLOSED)
            advanceTimeBy(SimulatedVoiceCommandEnvironment.TRANSIT_MS * 2)
            runCurrent()
            assertEquals(VoiceDoorState.CLOSED, env.doorState.value)
        }
}
