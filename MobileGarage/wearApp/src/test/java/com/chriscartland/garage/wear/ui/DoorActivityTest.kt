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

package com.chriscartland.garage.wear.ui

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.RemoteButtonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one rule behind the screen wake and the watch face's chip. */
class DoorActivityTest {
    @Test
    fun aHeldPressIsReportedLegByLeg() {
        assertEquals(DoorActivity.PressSending, DoorActivity.current(RemoteButtonState.SendingToServer, DoorPosition.CLOSED, false))
        assertEquals(DoorActivity.PressAwaitingDoor, DoorActivity.current(RemoteButtonState.SendingToDoor, DoorPosition.CLOSED, false))
    }

    @Test
    fun aMovingDoorIsReportedWithItsDirectionWhoeverMovedIt() {
        assertEquals(
            DoorActivity.DoorMoving(DoorTravel.OPENING),
            DoorActivity.current(RemoteButtonState.Ready, DoorPosition.OPENING, false),
        )
        assertEquals(
            DoorActivity.DoorMoving(DoorTravel.CLOSING),
            DoorActivity.current(RemoteButtonState.Ready, DoorPosition.CLOSING, false),
        )
    }

    @Test
    fun aSpokenPressIsAPress() {
        assertEquals(DoorActivity.PressAwaitingDoor, DoorActivity.current(RemoteButtonState.Ready, DoorPosition.CLOSED, true))
    }

    @Test
    fun nothingIsReportedForAHoldARestingDoorOrAStuckOne() {
        // The finger is on the screen; the door is where it was; the door has
        // STOPPED (and the hero says so) — none of these is something the
        // wrist needs telling about.
        assertNull(DoorActivity.current(RemoteButtonState.AwaitingConfirmation, DoorPosition.CLOSED, false))
        assertNull(DoorActivity.current(RemoteButtonState.Ready, DoorPosition.OPEN, false))
        assertNull(DoorActivity.current(RemoteButtonState.Ready, DoorPosition.OPENING_TOO_LONG, false))
        assertNull(DoorActivity.current(RemoteButtonState.Ready, DoorPosition.CLOSING_TOO_LONG, false))
        assertNull(DoorActivity.current(RemoteButtonState.Ready, null, false))
    }
}
