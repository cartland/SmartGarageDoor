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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * In-app speech capture: [SpeechRecognizer] driven directly, with no system
 * screen at all.
 *
 * ## Why this exists (the extra taps)
 *
 * `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` behaves differently on a watch
 * than on a phone. On Wear the intent resolves to **Gboard's**
 * `WearRemoteInputActivity` — the general text-ENTRY surface, not a one-shot
 * recognizer. Its job is to produce text for an input field, so it transcribes
 * and then asks you to review/edit/accept before returning. That confirmation
 * step is the whole point of that activity, so no `EXTRA_*` can switch it off.
 * (Confirmed with `pm query-activities -a android.speech.action.RECOGNIZE_SPEECH`
 * on a Wear image.) On the phone the same action goes to Google's recognizer,
 * which returns straight away — hence the difference in feel.
 *
 * [SpeechRecognizer] is the headless API underneath all of that: results
 * arrive on [RecognitionListener] callbacks and the app draws everything. One
 * tap, speak, done. The price is a `RECORD_AUDIO` runtime permission, which is
 * why the screen falls back to the intent when it is not granted rather than
 * refusing to work.
 *
 * ## Availability is not guaranteed
 *
 * [SpeechRecognizer] needs a `RecognitionService` on the device. Real watches
 * ship one with Google's speech services; bare emulator images generally do
 * NOT (the `wear_capture` AVD has none, which is why this path cannot be
 * exercised locally). [isAvailable] is therefore checked before use and the
 * caller falls back to the system flow, so the demo works either way.
 */
internal class WearSpeechCapture(
    private val recognizer: SpeechRecognizer,
) {
    /** Begin listening. Results arrive on the callbacks passed to [rememberWearSpeechCapture]. */
    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Live text while speaking. Without it the in-app listening state
            // would be a static label, which is a downgrade from the system
            // UI it replaces.
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Only the top-1 result is ever parsed: acting on alternative N is
            // acting on something the recognizer thinks you probably did not say.
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        recognizer.startListening(intent)
    }

    fun cancel() = recognizer.cancel()

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }
}

/**
 * Creates a [WearSpeechCapture] bound to the composition, or null when the
 * device has no `RecognitionService` (callers fall back to the system flow).
 *
 * Callbacks are wrapped in [rememberUpdatedState] so the long-lived
 * [RecognitionListener] always calls the current lambdas rather than the ones
 * captured when the recognizer was created — the same trap that produced the
 * iOS `onChange` bug noted in the root CLAUDE.md.
 */
@Composable
internal fun rememberWearSpeechCapture(
    onTranscript: (String?) -> Unit,
    onPartial: (String) -> Unit,
): WearSpeechCapture? {
    val context = LocalContext.current
    // Availability is resolved into a value rather than an early return: an
    // early return here would put the remember/DisposableEffect calls below
    // behind a condition, which breaks the Rules of Composition.
    val available = remember(context) { WearSpeechCapture.isAvailable(context) }

    val currentOnTranscript by rememberUpdatedState(onTranscript)
    val currentOnPartial by rememberUpdatedState(onPartial)

    val recognizer = remember(context, available) {
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    currentOnTranscript(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { currentOnPartial(it) }
                }

                // Every error ends the attempt. They are reported as "no
                // usable speech" rather than surfaced individually: the
                // recognizer's error codes (timeout, no match, busy, network)
                // are not things a wrist can act on differently.
                override fun onError(error: Int) = currentOnTranscript(null)

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        onDispose { recognizer?.destroy() }
    }
    return remember(recognizer) { recognizer?.let { WearSpeechCapture(it) } }
}
