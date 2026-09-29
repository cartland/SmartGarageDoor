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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTargetTest {
    @Test
    fun theTargetsAreExactlyThePlacesToLook() {
        // A closed list, on purpose: adding a target means editing this test,
        // which is the moment to ask whether the new one can act on the door.
        assertEquals(
            setOf("home", "history", "snooze"),
            LaunchTarget.entries.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyStackIsRootedAtHome() {
        // TabNavigation's model is "back from a tab reveals Home"; a stack
        // rooted anywhere else would exit the app on the first back press.
        LaunchTarget.entries.forEach { target ->
            assertEquals("$target", Screen.Home, target.stack.first())
        }
    }

    @Test
    fun historyLandsOnHistoryAndSnoozeOnSettingsWithTheSheet() {
        assertEquals(listOf(Screen.Home), LaunchTarget.HOME.stack)
        assertEquals(listOf(Screen.Home, Screen.History), LaunchTarget.HISTORY.stack)
        assertEquals(listOf(Screen.Home, Screen.Profile), LaunchTarget.SNOOZE.stack)
        assertTrue(LaunchTarget.SNOOZE.opensSnoozeSheet)
        assertFalse(LaunchTarget.HISTORY.opensSnoozeSheet)
        assertFalse(LaunchTarget.HOME.opensSnoozeSheet)
    }

    @Test
    fun idsRoundTrip() {
        LaunchTarget.entries.forEach { target ->
            assertEquals(target, LaunchTarget.from(target.id))
        }
    }

    @Test
    fun anUnknownOrMissingIdIsAPlainLaunch() {
        // A pinned shortcut outlives the build that made it, and a plain
        // launch carries no extra at all. Neither is an error.
        assertEquals(LaunchTarget.HOME, LaunchTarget.from(null))
        assertEquals(LaunchTarget.HOME, LaunchTarget.from(""))
        assertEquals(LaunchTarget.HOME, LaunchTarget.from("press"))
        assertEquals(LaunchTarget.HOME, LaunchTarget.from("History"))
    }
}
