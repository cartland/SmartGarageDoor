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

package com.chriscartland.garage.widget

import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.presentation.DataFreshness
import com.chriscartland.garage.presentation.DoorHeadline
import com.chriscartland.garage.presentation.GlanceStatusMapper
import com.chriscartland.garage.presentation.GlanceSubline
import com.chriscartland.garage.presentation.StatusHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker's reading: a good day, decided by the same mapper as a real one. */
class GarageWidgetPreviewTest {
    private val now = 1_700_000_000L

    @Test
    fun thePickerShowsAConfirmedClosedDoorWithASpan() {
        val status = GarageWidgetPreview.status(nowEpochSeconds = now)
        assertEquals(StatusHeadline.Door(DoorHeadline.CLOSED), status.headline)
        assertEquals(DataFreshness.FRESH, status.freshness)
        assertNull(status.warning)
        val subline = status.subline
        assertTrue("a good day has a since-line, was $subline", subline is GlanceSubline.Duration)
        assertEquals(now - GarageWidgetPreview.CLOSED_FOR_SECONDS, (subline as GlanceSubline.Duration).sinceEpochSeconds)
    }

    @Test
    fun theSameMapperMutesAReadingItCannotVouchFor() {
        // Positive control: FRESH above is the mapper's verdict on the canned
        // ages, not a constant — the same call with a stale check-in is muted.
        val stale = GlanceStatusMapper.forGlance(
            doorPosition = DoorPosition.CLOSED,
            lastCheckInEpochSeconds = now - 6L * 3_600L,
            lastChangeEpochSeconds = now - GarageWidgetPreview.CLOSED_FOR_SECONDS,
            nowEpochSeconds = now,
            isFetchError = false,
        )
        assertEquals(DataFreshness.STALE, stale.freshness)
    }
}
