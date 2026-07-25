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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fake world for the voice-command playground: an in-memory door and a
 * pretend button. [pressButton] waits a moment (fake network), flips
 * the door to [VoiceDoorState.MOVING], then settles it on the commanded
 * side after a fake transit. The real door is never touched.
 *
 * [setDoorState] lets the playground UI place the door directly so the
 * user can exercise every gate path (already-open, moving, etc.); a
 * manual set cancels any in-flight transit.
 */
class SimulatedVoiceCommandEnvironment(
    private val scope: CoroutineScope,
    private val pressDelayMs: Long = PRESS_DELAY_MS,
    private val transitMs: Long = TRANSIT_MS,
) : VoiceCommandEnvironment {
    private val _doorState = MutableStateFlow(VoiceDoorState.CLOSED)
    override val doorState: StateFlow<VoiceDoorState> = _doorState

    private var transitJob: Job? = null

    fun setDoorState(state: VoiceDoorState) {
        transitJob?.cancel()
        transitJob = null
        _doorState.value = state
    }

    override suspend fun pressButton(intent: VoiceIntent): Boolean {
        delay(pressDelayMs)
        transitJob?.cancel()
        _doorState.value = VoiceDoorState.MOVING
        transitJob = scope.launch {
            delay(transitMs)
            _doorState.value = when (intent) {
                VoiceIntent.OPEN -> VoiceDoorState.OPEN
                VoiceIntent.CLOSE -> VoiceDoorState.CLOSED
                // The controller never sends UNKNOWN (only HIGH commands
                // commit, and HIGH implies a direction) — settle to
                // UNKNOWN so a misuse is visible instead of a guess.
                VoiceIntent.UNKNOWN -> VoiceDoorState.UNKNOWN
            }
        }
        return true
    }

    companion object {
        /** Fake button-press round-trip. */
        const val PRESS_DELAY_MS = 600L

        /**
         * Fake door travel time from press to settled state. Roughly a
         * real garage door's travel, and deliberately long enough to
         * speak a command AT the moving door mid-transit and watch the
         * gate refuse it (2.5s was over before the recognizer round-trip
         * finished).
         */
        const val TRANSIT_MS = 10_000L
    }
}
