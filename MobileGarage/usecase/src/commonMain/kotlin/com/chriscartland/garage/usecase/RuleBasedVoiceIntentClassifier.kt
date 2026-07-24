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
import com.chriscartland.garage.domain.model.VoiceIntentClassification
import com.chriscartland.garage.domain.model.VoiceIntentClassifier
import com.chriscartland.garage.domain.model.VoiceIntentConfidence

/**
 * Deterministic rules engine — the first [VoiceIntentClassifier]
 * (docs/VOICE_COMMANDS.md decision 2: an allowlist grammar is the most
 * conservative confidence gate). Offline, pure, exhaustively
 * table-tested.
 *
 * Tiers, applied to the normalized utterance (lowercased, punctuation
 * stripped, whitespace collapsed):
 *
 * 1. **Deny-first**: empty text, a negation word ("don't", "never",
 *    "stop"), or both an open verb and a close verb present — UNKNOWN.
 *    Negation checks run before anything else so "don't open the door"
 *    can never classify as OPEN.
 * 2. **HIGH** — the whole utterance is an exact imperative:
 *    `(please) open|close|shut (the|my|our) garage door|garage|door (please)`.
 *    Question forms ("can you open the door") never match because the
 *    leading words fail the whole-utterance grammar.
 * 3. **MEDIUM** — a single direction verb appears somewhere before a
 *    door object ("open up the garage for me", "can you close the
 *    door"). Recognizable but loose; a future action layer is expected
 *    to require HIGH.
 * 4. **UNKNOWN/NONE** — everything else, including verb-after-object
 *    ("the door is open" describes state, not a command) and
 *    verb-without-object ("open the window").
 */
class RuleBasedVoiceIntentClassifier : VoiceIntentClassifier {
    override val name: String = "Rules v1"

    override fun classify(text: String): VoiceIntentClassification {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return UNKNOWN_RESULT

        val tokens = normalized.split(' ')
        if (tokens.any { it in NEGATION_TOKENS }) return UNKNOWN_RESULT

        val openIndex = tokens.indexOfFirst { it in OPEN_VERBS }
        val closeIndex = tokens.indexOfFirst { it in CLOSE_VERBS }
        val intent = when {
            openIndex >= 0 && closeIndex >= 0 -> return UNKNOWN_RESULT
            openIndex >= 0 -> VoiceIntent.OPEN
            closeIndex >= 0 -> VoiceIntent.CLOSE
            else -> return UNKNOWN_RESULT
        }

        if (HIGH_GRAMMAR.matches(normalized)) {
            return VoiceIntentClassification(intent, VoiceIntentConfidence.HIGH)
        }

        val verbIndex = if (intent == VoiceIntent.OPEN) openIndex else closeIndex
        val objectIndex = tokens.indexOfFirst { it in DOOR_OBJECTS }
        if (objectIndex > verbIndex) {
            return VoiceIntentClassification(intent, VoiceIntentConfidence.MEDIUM)
        }

        return UNKNOWN_RESULT
    }

    private fun normalize(text: String): String =
        text
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private companion object {
        val UNKNOWN_RESULT =
            VoiceIntentClassification(VoiceIntent.UNKNOWN, VoiceIntentConfidence.NONE)

        val OPEN_VERBS = setOf("open")
        val CLOSE_VERBS = setOf("close", "shut")
        val DOOR_OBJECTS = setOf("door", "doors", "garage")

        // Post-normalization tokens ("don't" normalizes to "don" + "t";
        // both "don" and "not" are covered).
        val NEGATION_TOKENS = setOf("don", "dont", "not", "never", "stop")

        val HIGH_GRAMMAR =
            Regex("^(please )?(open|close|shut) ((the|my|our) )?(garage door|garage|door)( please)?$")
    }
}
