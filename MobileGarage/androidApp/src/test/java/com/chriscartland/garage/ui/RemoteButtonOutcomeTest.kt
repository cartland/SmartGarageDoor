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

package com.chriscartland.garage.ui

import com.chriscartland.garage.domain.model.RemoteButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteButtonOutcomeTest {
    /** Every state the sealed interface has; `when` exhaustiveness keeps the rule honest, this keeps the test so. */
    private val allStates =
        listOf(
            RemoteButtonState.Ready,
            RemoteButtonState.Preparing,
            RemoteButtonState.AwaitingConfirmation,
            RemoteButtonState.Cancelled,
            RemoteButtonState.SendingToServer,
            RemoteButtonState.SendingToDoor,
            RemoteButtonState.Succeeded,
            RemoteButtonState.ServerFailed,
            RemoteButtonState.Forbidden,
            RemoteButtonState.DoorFailed,
        )

    @Test
    fun doneIsSuccessAndEveryFailureIsFailure() {
        assertEquals(OutcomeTone.SUCCESS, RemoteButtonOutcome.toneFor(RemoteButtonState.Succeeded))
        assertEquals(OutcomeTone.FAILURE, RemoteButtonOutcome.toneFor(RemoteButtonState.ServerFailed))
        // A refusal is a failed press too, just worded differently.
        assertEquals(OutcomeTone.FAILURE, RemoteButtonOutcome.toneFor(RemoteButtonState.Forbidden))
        assertEquals(OutcomeTone.FAILURE, RemoteButtonOutcome.toneFor(RemoteButtonState.DoorFailed))
    }

    @Test
    fun inFlightStatesStayNeutral() {
        // Positive control for the test above: a rule that coloured every
        // resting state would pass it.
        assertEquals(OutcomeTone.NEUTRAL, RemoteButtonOutcome.toneFor(RemoteButtonState.SendingToServer))
        assertEquals(OutcomeTone.NEUTRAL, RemoteButtonOutcome.toneFor(RemoteButtonState.SendingToDoor))
        assertEquals(OutcomeTone.NEUTRAL, RemoteButtonOutcome.toneFor(RemoteButtonState.Cancelled))
    }

    @Test
    fun allThreeTonesAreReachable() {
        // A tone no state reaches is dead colour.
        assertEquals(OutcomeTone.entries.toSet(), allStates.map { RemoteButtonOutcome.toneFor(it) }.toSet())
    }
}
