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

package com.chriscartland.garage.domain.model

/**
 * What a spoken utterance asks the door to do. [UNKNOWN] is the safe
 * default for anything a classifier cannot confidently place — per the
 * voice design principle (docs/VOICE_COMMANDS.md): it is okay to
 * incorrectly ignore a command; it is never okay to incorrectly execute
 * one.
 */
enum class VoiceIntent {
    OPEN,
    CLOSE,
    UNKNOWN,
}

/**
 * How sure the classifier is. A future action layer decides what tier
 * is actionable (expected: only [HIGH]); the experiment surfaces every
 * tier so engines can be compared.
 */
enum class VoiceIntentConfidence {
    /** Unambiguous imperative — e.g. an exact grammar match. */
    HIGH,

    /**
     * The intent is recognizable but the phrasing is loose — extra
     * words, a question form, a bare verb+object. Informative, not
     * actionable.
     */
    MEDIUM,

    /** No intent found. Always and only paired with [VoiceIntent.UNKNOWN]. */
    NONE,
}

/**
 * A classifier verdict. Invariant (pinned by tests): `intent == UNKNOWN`
 * if and only if `confidence == NONE`.
 */
data class VoiceIntentClassification(
    val intent: VoiceIntent,
    val confidence: VoiceIntentConfidence,
)

/**
 * Pluggable text-to-intent engine. Implementations must be pure and
 * fast (callable synchronously from a ViewModel): rules/regex today
 * (`RuleBasedVoiceIntentClassifier` in `:usecase`), potentially
 * on-device ML models later — all behind this same contract so the
 * playground and any future action layer are engine-agnostic.
 */
interface VoiceIntentClassifier {
    /** Short human-readable engine identifier, shown in the playground. */
    val name: String

    fun classify(text: String): VoiceIntentClassification
}
