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

import com.chriscartland.garage.viewmodel.DefaultHomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The widget has no route to the garage button, and this is what says so.**
 *
 * The phone's counterpart to `GarageDoorTileSafetyTest`, and the argument is
 * stronger here than on the watch. A home screen is swiped across, pocketed and
 * handed to other people; a widget tap is a SINGLE tap, with no way to express
 * the two-tap confirm the app's own button uses or the press-and-hold the
 * watch's does. Arming the door from here would replace the strongest guard in
 * the app with the weakest gesture the platform offers.
 *
 * Asserted rather than trusted, because the next person to touch these files
 * will be looking at a surface that already shows the door and is one small
 * change away from acting on it.
 */
class GarageDoorWidgetSafetyTest {
    @Test
    fun theWidgetReaderCannotReachTheRealRemoteButton() {
        val violations = dependenciesOf(WidgetGlanceStatus::class.java) intersect FORBIDDEN
        assertTrue(
            "WidgetGlanceStatus must not depend on $violations. The widget shows the " +
                "door and opens the app; the button stays behind the app's own confirm.",
            violations.isEmpty(),
        )
    }

    @Test
    fun theWidgetAndItsReceiverHoldNothingThatPresses() {
        // The reader having no button is no guarantee if the widget or its
        // receiver reaches around it.
        val held = (
            GarageDoorWidget::class.java.declaredFields +
                GarageDoorWidgetReceiver::class.java.declaredFields
        ).map { it.type.simpleName }.toSet()
        val violations = held intersect FORBIDDEN
        assertTrue("The widget classes must not hold $violations.", violations.isEmpty())
    }

    @Test
    fun theManifestGivesTheWidgetNoActionBeyondBeingUpdated() {
        // A second, independent way in: a custom action on the receiver is how a
        // widget grows a button without any Kotlin dependency changing, and the
        // reflection checks above would not notice.
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("missing ${manifest.absolutePath}", manifest.exists())
        val text = manifest.readText()
        val receiver = text.substringAfter("GarageDoorWidgetReceiver", missingDelimiterValue = "")
        assertTrue(
            "GarageDoorWidgetReceiver is not declared in the manifest, so the widget " +
                "cannot appear at all — or it was renamed and this check went blind.",
            receiver.isNotEmpty(),
        )
        val block = receiver.substringBefore("</receiver>")
        val actions = ACTION_PATTERN.findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(
            "The widget receiver may only answer APPWIDGET_UPDATE. Any other action " +
                "is a way to make the home screen do something to the door: $actions",
            listOf("android.appwidget.action.APPWIDGET_UPDATE"),
            actions,
        )
    }

    @Test
    fun theseChecksCanActuallyFail() {
        // Positive control. Every assertion above is "this set is empty" or
        // "this list is exactly one", which a typo in FORBIDDEN or a reflection
        // call returning nothing would satisfy forever. DefaultHomeViewModel is
        // the phone surface that DOES press the button, so the same predicate
        // run against it must come back non-empty.
        val violations = dependenciesOf(DefaultHomeViewModel::class.java) intersect FORBIDDEN
        assertTrue(
            "the forbidden-dependency check matched nothing even against the ViewModel " +
                "that owns the button — the check is not measuring anything",
            violations.isNotEmpty(),
        )
    }

    private fun dependenciesOf(type: Class<*>): Set<String> =
        type.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()

    private companion object {
        val ACTION_PATTERN = Regex("""<action android:name="([^"]+)"""")

        val FORBIDDEN = setOf(
            "PushRemoteButtonUseCase",
            "RemoteButtonRepository",
            "NetworkButtonDataSource",
            "ButtonStateMachine",
            "CheckDoorCommandUseCase",
            "DefaultHomeViewModel",
            "HomeViewModel",
        )
    }
}
