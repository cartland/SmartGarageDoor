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

import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.model.VoiceIntentConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Table-driven accept/reject/confidence matrix for the rules engine —
 * the executable version of the table in docs/VOICE_COMMANDS.md. The
 * safety-critical rows are the UNKNOWN ones: negation, questions about
 * state, wrong objects, and conflicting verbs must never classify as a
 * direction.
 */
class RuleBasedVoiceIntentClassifierTest {
    private val classifier = RuleBasedVoiceIntentClassifier()

    private data class Case(
        val utterance: String,
        val intent: VoiceIntent,
        val confidence: VoiceIntentConfidence,
    )

    private val table = listOf(
        // HIGH — exact imperatives (punctuation and case are normalized away).
        Case("open the door", VoiceIntent.OPEN, VoiceIntentConfidence.HIGH),
        Case("Open the garage door!", VoiceIntent.OPEN, VoiceIntentConfidence.HIGH),
        Case("please open the door", VoiceIntent.OPEN, VoiceIntentConfidence.HIGH),
        Case("open my garage door", VoiceIntent.OPEN, VoiceIntentConfidence.HIGH),
        Case("close the garage", VoiceIntent.CLOSE, VoiceIntentConfidence.HIGH),
        Case("close the door please", VoiceIntent.CLOSE, VoiceIntentConfidence.HIGH),
        Case("shut the door", VoiceIntent.CLOSE, VoiceIntentConfidence.HIGH),
        Case("PLEASE CLOSE THE GARAGE DOOR", VoiceIntent.CLOSE, VoiceIntentConfidence.HIGH),
        // MEDIUM — recognizable but loose: extra words or question forms.
        Case("can you open the door", VoiceIntent.OPEN, VoiceIntentConfidence.MEDIUM),
        Case("open up the garage door for me", VoiceIntent.OPEN, VoiceIntentConfidence.MEDIUM),
        Case("would you please close the garage door", VoiceIntent.CLOSE, VoiceIntentConfidence.MEDIUM),
        Case("close the garage door now", VoiceIntent.CLOSE, VoiceIntentConfidence.MEDIUM),
        Case("i want to open the door", VoiceIntent.OPEN, VoiceIntentConfidence.MEDIUM),
        // UNKNOWN — negation must never classify as a direction.
        Case("don't open the door", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("do not close the garage", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("never open the door", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("i didnt say open the door", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("the door wont close", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — memory/reminder utterances are not commands (v2).
        Case("remind me to close the garage tonight", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("i forgot to close the garage", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — leading state/past question words ask about the
        // door, not for movement (v2). Polite "can you..." stays MEDIUM.
        Case("is it possible to open the garage from here", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("did you remember to close the door when you left", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — conflicting directions.
        Case("open and close the door", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — state descriptions (verb after object) are not commands.
        Case("the door is open", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("is the garage closed", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — wrong or missing object.
        Case("open the window", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("close the deal", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("open", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("shut up", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        // UNKNOWN — no intent at all.
        Case("hello world", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
        Case("   ", VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE),
    )

    @Test
    fun classificationTable() {
        table.forEach { case ->
            val result = classifier.classify(case.utterance)
            assertEquals(
                case.intent,
                result.intent,
                "intent for \"${case.utterance}\"",
            )
            assertEquals(
                case.confidence,
                result.confidence,
                "confidence for \"${case.utterance}\"",
            )
        }
    }

    @Test
    fun unknownIfAndOnlyIfNoneConfidence() {
        table.forEach { case ->
            val result = classifier.classify(case.utterance)
            assertEquals(
                result.intent == VoiceIntent.UNKNOWN,
                result.confidence == VoiceIntentConfidence.NONE,
                "invariant UNKNOWN <=> NONE for \"${case.utterance}\"",
            )
        }
    }

    @Test
    fun engineNameIsStable() {
        assertTrue(classifier.name.isNotBlank())
        assertEquals("Rules v2", classifier.name)
    }
}
