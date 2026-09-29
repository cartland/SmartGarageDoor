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

package com.chriscartland.garage.fcm

import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.SnoozeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeOutcomeWordsTest {
    @Test
    fun nothingInFlightIsWordless() {
        assertNull(SnoozeOutcomeWords.title(SnoozeAction.Idle))
        assertNull(SnoozeOutcomeWords.body(SnoozeAction.Idle))
        assertNull(SnoozeOutcomeWords.title(SnoozeAction.Sending))
        assertNull(SnoozeOutcomeWords.body(SnoozeAction.Sending))
    }

    @Test
    fun eachFailureKeepsTheSettingsSheetsWords() {
        assertEquals(R.string.snooze_failed_not_authenticated, SnoozeOutcomeWords.body(SnoozeAction.Failed.NotAuthenticated))
        assertEquals(R.string.snooze_failed_missing_data, SnoozeOutcomeWords.body(SnoozeAction.Failed.MissingData))
        assertEquals(R.string.snooze_failed_network, SnoozeOutcomeWords.body(SnoozeAction.Failed.NetworkError))
        assertEquals(R.string.snooze_failed_event_changed, SnoozeOutcomeWords.body(SnoozeAction.Failed.EventChanged))
        assertEquals(R.string.notification_snooze_failed_title, SnoozeOutcomeWords.title(SnoozeAction.Failed.NetworkError))
    }

    @Test
    fun aSnoozeThatTookSaysUntilWhen() {
        val set = SnoozeAction.Succeeded.Set(1_700_003_600)
        assertEquals(R.string.notification_snoozed_title, SnoozeOutcomeWords.title(set))
        assertEquals(R.string.settings_notifications_subtitle_snoozing_until, SnoozeOutcomeWords.body(set))
        assertEquals(R.string.notification_snooze_cleared, SnoozeOutcomeWords.body(SnoozeAction.Succeeded.Cleared))
    }

    @Test
    fun successReplacesTheWarningAndFailureSitsBesideIt() {
        assertTrue(SnoozeOutcomeWords.replacesWarning(SnoozeAction.Succeeded.Set(1)))
        assertTrue(SnoozeOutcomeWords.replacesWarning(SnoozeAction.Succeeded.Cleared))
        assertFalse(SnoozeOutcomeWords.replacesWarning(SnoozeAction.Failed.NetworkError))
        assertFalse(SnoozeOutcomeWords.replacesWarning(SnoozeAction.Failed.NotAuthenticated))
    }

    @Test
    fun theOutcomesDoNotAllReadTheSame() {
        // Positive control: a words object that returned one resource for
        // everything would pass the equalities above by accident of choice.
        assertNotEquals(
            SnoozeOutcomeWords.body(SnoozeAction.Failed.NotAuthenticated),
            SnoozeOutcomeWords.body(SnoozeAction.Failed.EventChanged),
        )
        assertNotEquals(
            SnoozeOutcomeWords.title(SnoozeAction.Succeeded.Set(1)),
            SnoozeOutcomeWords.title(SnoozeAction.Failed.NetworkError),
        )
    }
}
