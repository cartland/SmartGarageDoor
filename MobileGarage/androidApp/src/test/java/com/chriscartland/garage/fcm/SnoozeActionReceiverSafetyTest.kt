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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The warning's Snooze action runs in a receiver with the whole DI graph in
 * reach, so nothing structural stops it from pressing. These read its
 * sources and its manifest entry and refuse every route to the button.
 */
class SnoozeActionReceiverSafetyTest {
    @Test
    fun theHandlersHoldNoRouteToTheButton() {
        HANDLER_SOURCES.forEach { name ->
            val text = source(name)
            FORBIDDEN.forEach { symbol ->
                assertFalse("$name mentions $symbol", text.contains(symbol))
            }
        }
    }

    @Test
    fun theReceiverIsPrivateAndAnswersNoOutsideIntent() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("missing ${manifest.absolutePath}", manifest.exists())
        val element = RECEIVER_ELEMENT.find(manifest.readText())?.value
        assertTrue(
            "SnoozeActionReceiver is not declared in the manifest (or is no longer self-closing, " +
                "which is how an intent filter would get in)",
            element != null && element.endsWith("/>"),
        )
        assertTrue("SnoozeActionReceiver must be exported=\"false\"", element!!.contains("android:exported=\"false\""))
    }

    @Test
    fun theForbiddenListCanActuallyFire() {
        // Positive control: the DI graph names the button's use case, so the
        // same check against it must find something.
        val graph = File("src/main/java/com/chriscartland/garage/di/AppComponent.kt").readText()
        assertTrue("the forbidden list matched nothing even in AppComponent", FORBIDDEN.any { graph.contains(it) })
    }

    private fun source(name: String): String {
        val file = File("src/main/java/com/chriscartland/garage/fcm/$name")
        assertTrue("missing ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private companion object {
        val HANDLER_SOURCES = listOf("SnoozeActionReceiver.kt", "SnoozeFromNotification.kt", "SnoozeOutcomeWords.kt")
        val RECEIVER_ELEMENT = Regex("""<receiver\s+android:name="\.fcm\.SnoozeActionReceiver"[^>]*>""")
        val FORBIDDEN =
            setOf(
                "PushRemoteButton",
                "RemoteButtonRepository",
                "NetworkButtonDataSource",
                "ButtonStateMachine",
                "HomeViewModel",
                "DoorCommand",
            )
    }
}
