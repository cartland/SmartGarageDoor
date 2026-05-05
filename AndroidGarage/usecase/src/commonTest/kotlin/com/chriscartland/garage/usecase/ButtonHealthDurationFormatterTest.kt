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

import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonHealthDurationFormatterTest {
    private val now = 1_700_000_000L

    @Test
    fun nullStateChangedAtSeconds_isUnknown() {
        assertEquals("unknown", ButtonHealthDurationFormatter.formatAgo(null, now))
    }

    @Test
    fun futureTimestamp_isJustNow() {
        assertEquals("just now", ButtonHealthDurationFormatter.formatAgo(now + 100, now))
    }

    @Test
    fun sameTimestamp_isJustNow() {
        assertEquals("just now", ButtonHealthDurationFormatter.formatAgo(now, now))
    }

    @Test
    fun secondsBucket() {
        assertEquals("30 sec ago", ButtonHealthDurationFormatter.formatAgo(now - 30, now))
        assertEquals("59 sec ago", ButtonHealthDurationFormatter.formatAgo(now - 59, now))
    }

    @Test
    fun minutesBucket() {
        assertEquals("1 min ago", ButtonHealthDurationFormatter.formatAgo(now - 60, now))
        assertEquals("11 min ago", ButtonHealthDurationFormatter.formatAgo(now - 11 * 60, now))
        assertEquals("59 min ago", ButtonHealthDurationFormatter.formatAgo(now - 59 * 60, now))
    }

    @Test
    fun hoursBucket() {
        assertEquals("1 hr ago", ButtonHealthDurationFormatter.formatAgo(now - 60 * 60, now))
        assertEquals("23 hr ago", ButtonHealthDurationFormatter.formatAgo(now - 23 * 60 * 60, now))
    }

    @Test
    fun daysBucket_singular() {
        assertEquals("1 day ago", ButtonHealthDurationFormatter.formatAgo(now - 24 * 60 * 60, now))
    }

    @Test
    fun daysBucket_plural() {
        assertEquals("3 days ago", ButtonHealthDurationFormatter.formatAgo(now - 3L * 24 * 60 * 60, now))
    }
}
