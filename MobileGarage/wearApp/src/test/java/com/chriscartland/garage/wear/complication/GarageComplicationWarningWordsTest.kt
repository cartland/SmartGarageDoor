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

package com.chriscartland.garage.wear.complication

import com.chriscartland.garage.presentation.GlanceWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The complication's word for each shared [GlanceWarning]. Length is pinned by [GarageComplicationLengthTest]. */
class GarageComplicationWarningWordsTest {
    @Test
    fun everyWarningHasItsOwnShortWord() {
        val ids = GlanceWarning.entries.map { GarageComplicationWords.warningWord(it) }
        assertEquals("two warnings resolve to one string: $ids", ids.size, ids.toSet().size)
        assertTrue("a warning with no string resource", ids.none { it == 0 })
    }
}
