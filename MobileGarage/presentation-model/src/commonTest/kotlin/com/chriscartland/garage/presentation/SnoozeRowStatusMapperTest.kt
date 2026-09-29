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

package com.chriscartland.garage.presentation

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.domain.model.SnoozeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnoozeRowStatusMapperTest {
    private val allSnoozeStates = listOf(
        SnoozeState.Loading,
        SnoozeState.NotSnoozing,
        SnoozeState.Snoozing(untilEpochSeconds = 1_700_000_000L),
    )

    @Test
    fun deniedPermissionOutranksEverySnoozeState() {
        // The precedence rule, stated as a property rather than three examples:
        // no snooze state can produce anything but PermissionDenied when the OS
        // is withholding notifications. Loading is the interesting case — the
        // one a naive `when (snoozeState)` written first would get wrong.
        allSnoozeStates.forEach { state ->
            assertEquals(
                SnoozeRowStatus.PermissionDenied,
                SnoozeRowStatusMapper.forState(state, notificationsGranted = false, doorPosition = DoorPosition.CLOSED),
                "snooze state $state should not survive a denied permission",
            )
        }
    }

    @Test
    fun grantedPermissionNeverReportsPermissionDenied() {
        allSnoozeStates.forEach { state ->
            assertTrue(
                SnoozeRowStatusMapper.forState(state, notificationsGranted = true, doorPosition = DoorPosition.CLOSED)
                    !is SnoozeRowStatus.PermissionDenied,
                "granted permission should never render as denied (state $state)",
            )
        }
    }

    @Test
    fun snoozeStateMapsThroughWhenPermissionIsGranted() {
        assertEquals(
            SnoozeRowStatus.Loading,
            SnoozeRowStatusMapper.forState(SnoozeState.Loading, notificationsGranted = true, doorPosition = DoorPosition.CLOSED),
        )
        assertEquals(
            SnoozeRowStatus.Off,
            SnoozeRowStatusMapper.forState(SnoozeState.NotSnoozing, notificationsGranted = true, doorPosition = DoorPosition.CLOSED),
        )
    }

    @Test
    fun snoozingCarriesTheInstantUnchanged() {
        // The row must be able to render the real time, so the instant has to
        // survive the mapping exactly — no rounding, no re-basing on "now".
        val until = 1_700_003_600L
        assertEquals(
            SnoozeRowStatus.SnoozingUntil(until),
            SnoozeRowStatusMapper.forState(
                SnoozeState.Snoozing(untilEpochSeconds = until),
                notificationsGranted = true,
                doorPosition = DoorPosition.CLOSED,
            ),
        )
    }

    @Test
    fun snoozingAndOffAreDistinguishable() {
        // The bug this type exists to prevent: "snoozed by you" collapsing into
        // the same rendering as "notifications unavailable". Assert the three
        // notification-relevant answers are three distinct values.
        val snoozing = SnoozeRowStatusMapper.forState(
            SnoozeState.Snoozing(untilEpochSeconds = 1L),
            notificationsGranted = true,
            doorPosition = DoorPosition.CLOSED,
        )
        val off = SnoozeRowStatusMapper.forState(SnoozeState.NotSnoozing, notificationsGranted = true, doorPosition = DoorPosition.CLOSED)
        val denied = SnoozeRowStatusMapper.forState(
            SnoozeState.NotSnoozing,
            notificationsGranted = false,
            doorPosition = DoorPosition.CLOSED,
        )
        assertEquals(3, setOf(snoozing, off, denied).size, "these three must not collapse")
    }

    // ---- the settled-door gate ----

    private val movingPositions =
        listOf(DoorPosition.OPENING, DoorPosition.CLOSING, DoorPosition.OPENING_TOO_LONG, DoorPosition.CLOSING_TOO_LONG)

    @Test
    fun aMovingDoorWithholdsTheSheetWhateverTheSnoozeState() {
        movingPositions.forEach { position ->
            allSnoozeStates.forEach { state ->
                assertEquals(
                    SnoozeRowStatus.DoorMoving,
                    SnoozeRowStatusMapper.forState(state, notificationsGranted = true, doorPosition = position),
                    "$position / $state",
                )
            }
        }
    }

    @Test
    fun aSettledOrUnreadDoorDoesNotWithholdIt() {
        // Positive control for the test above, and the rule's edge: no reading
        // at all is not "moving" — that save fails on its own terms.
        listOf(
            DoorPosition.OPEN,
            DoorPosition.CLOSED,
            DoorPosition.OPEN_MISALIGNED,
            DoorPosition.UNKNOWN,
            DoorPosition.ERROR_SENSOR_CONFLICT,
            null,
        ).forEach { position ->
            assertTrue(
                SnoozeRowStatusMapper.forState(SnoozeState.NotSnoozing, notificationsGranted = true, doorPosition = position)
                    !is SnoozeRowStatus.DoorMoving,
                "$position must not read as moving",
            )
        }
    }

    @Test
    fun aDeniedPermissionStillOutranksAMovingDoor() {
        assertEquals(
            SnoozeRowStatus.PermissionDenied,
            SnoozeRowStatusMapper.forState(SnoozeState.NotSnoozing, notificationsGranted = false, doorPosition = DoorPosition.OPENING),
        )
    }

    // ---- Home's way to the sheet ----

    @Test
    fun homeOffersSnoozeBesideAnOpenDoorWhenSignedIn() {
        assertTrue(SnoozeRowStatusMapper.offersHomeEntryPoint(DoorPosition.OPEN, signedIn = true))
        assertTrue(SnoozeRowStatusMapper.offersHomeEntryPoint(DoorPosition.OPEN_MISALIGNED, signedIn = true))
    }

    @Test
    fun homeDoesNotOfferSnoozeWhereASaveCouldNotSucceed() {
        // Positive control for the test above, and the two refusals: no account,
        // and a door the sheet itself would refuse.
        assertTrue(!SnoozeRowStatusMapper.offersHomeEntryPoint(DoorPosition.OPEN, signedIn = false), "signed out")
        listOf(
            DoorPosition.CLOSED,
            DoorPosition.OPENING,
            DoorPosition.CLOSING,
            DoorPosition.OPENING_TOO_LONG,
            DoorPosition.UNKNOWN,
            null,
        ).forEach { position ->
            assertTrue(!SnoozeRowStatusMapper.offersHomeEntryPoint(position, signedIn = true), "$position")
        }
    }
}
