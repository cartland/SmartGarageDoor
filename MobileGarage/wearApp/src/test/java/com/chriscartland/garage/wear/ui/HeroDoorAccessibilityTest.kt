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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The door is reachable by a screen reader through two custom actions and
 * NEVER through a single activation.
 *
 * The hold is the strongest guard on the watch; `clickable` would be the
 * weakest gesture available to it, and it is also the most natural thing to
 * reach for when making a `pointerInput`-only target accessible. So this
 * reads the source of `GarageDoorTarget` and refuses it, the way
 * [GarageDoorTileSafetyTest] refuses a button in the tile.
 */
class HeroDoorAccessibilityTest {
    private val source = File("src/main/java/com/chriscartland/garage/wear/ui/HeroScreen.kt")

    @Test
    fun theDoorOffersArmAndConfirmToScreenReaders() {
        val body = doorTarget()
        assertTrue("no customActions on the door", body.contains("customActions"))
        assertTrue("arm action missing", body.contains("a11y_arm_remote"))
        assertTrue("confirm action missing", body.contains("a11y_confirm_remote"))
    }

    @Test
    fun theDoorIsNeverASingleActivation() {
        val body = doorTarget()
        val found = FORBIDDEN.filter { body.contains(it) }
        assertTrue("GarageDoorTarget must not use $found: a single activation would bypass the hold.", found.isEmpty())
    }

    @Test
    fun theCheckCanActuallyFail() {
        // Positive control: an "is empty" assertion passes forever if the list
        // is typo'd. The thing the list forbids must trip it.
        val singleActivation = "Modifier.clickable(onClick = onHoldStart)"
        assertTrue(FORBIDDEN.any { singleActivation.contains(it) })
    }

    private fun doorTarget(): String {
        assertTrue("Missing " + source.absolutePath, source.exists())
        val text = source.readText()
        val start = text.indexOf("private fun GarageDoorTarget(")
        assertTrue("GarageDoorTarget not found", start >= 0)
        val end =
            listOf("\n@Composable", "\ninternal ", "\nprivate ", "\n/**")
                .map { text.indexOf(it, start + 1) }
                .filter { it >= 0 }
                .minOrNull() ?: text.length
        return text.substring(start, end)
    }

    private companion object {
        val FORBIDDEN = listOf(".clickable(", "onClick =", "Role.Button", "Role.Switch")
    }
}
