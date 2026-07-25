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
 * table-tested, and scored against the adversarial eval corpus
 * ([VoiceEvalCorpus] + [VoiceIntentEval]).
 *
 * Tiers, applied to the normalized utterance (lowercased, punctuation
 * stripped, whitespace collapsed):
 *
 * 1. **Deny-first** (checked before anything else, so "don't open the
 *    door" can never classify as OPEN): empty text; a negation/
 *    prohibition token ("dont", "didnt", "no", "nobody", "cancel",
 *    "quit", "hold"); a memory/reminder token ("remind", "forgot");
 *    a reported-speech token ("said", "told", "wants" — third-person
 *    forms only, "i want you to..." stays recognizable); a self-plan
 *    token ("ill", "gonna", "about", "need" — plans are not requests);
 *    a leading state/past question word ("is", "did" — but NOT
 *    "can"/"could"/"would", which stay polite requests at MEDIUM); or
 *    both an open verb and a close verb present.
 * 2. **HIGH** — the whole utterance is an exact imperative for the
 *    GARAGE door: possessives are allowed only with garage objects
 *    ("close my garage" yes; "open my door" NO — v3, red-team round 2
 *    found the "my door" safety hole), and bare "door"/"doors" only
 *    with "the" or nothing ("open the door", "open door").
 * 3. **MEDIUM** — a single direction verb appears before a door object
 *    AND the phrasing stays garage-plausible: the verb is followed by
 *    a command-continuation token (kills "close call by the garage",
 *    "shut down the computer in the garage"); "door"/"doors" is
 *    qualified by "the"/"a"/"garage"/the verb itself (kills "close the
 *    car door", "shut my bedroom door" — other doors are not this
 *    system's door); "garage" is followed by a command continuation or
 *    nothing (kills "close the garage store", "shut the garage or").
 * 4. **UNKNOWN/NONE** — everything else, including verb-after-object
 *    state descriptions ("the door is open").
 */
class RuleBasedVoiceIntentClassifier : VoiceIntentClassifier {
    override val name: String = "Rules v3"

    override fun classify(text: String): VoiceIntentClassification {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return UNKNOWN_RESULT

        val tokens = normalized.split(' ')
        if (tokens.any { it in NEGATION_TOKENS }) return UNKNOWN_RESULT
        if (tokens.any { it in NON_COMMAND_TOKENS }) return UNKNOWN_RESULT
        if (tokens.first() in QUESTION_LEAD_TOKENS) return UNKNOWN_RESULT

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

        // v3: the verb must start a plausible command phrase ("close
        // call", "shut eye", "open house" are idioms, not commands).
        val afterVerb = tokens.getOrNull(verbIndex + 1) ?: return UNKNOWN_RESULT
        if (afterVerb !in VERB_FOLLOWERS) return UNKNOWN_RESULT

        val objectIndex = tokens.indexOfFirst { it in DOOR_OBJECTS }
        if (objectIndex <= verbIndex) return UNKNOWN_RESULT

        if (tokens[objectIndex] == "garage") {
            // v3: "the garage <unknown noun>" is about a garage-thing,
            // not the garage ("garage store", "garage or...").
            val afterObject = tokens.getOrNull(objectIndex + 1)
            if (afterObject != null && afterObject !in GARAGE_FOLLOWERS) return UNKNOWN_RESULT
        } else {
            // v3: an unqualified modifier before "door" means some OTHER
            // door ("car door", "bedroom door", "grudge door").
            val beforeObject = tokens.getOrNull(objectIndex - 1)
            val qualified = beforeObject != null &&
                (beforeObject in DOOR_QUALIFIERS || beforeObject in OPEN_VERBS || beforeObject in CLOSE_VERBS)
            if (!qualified) return UNKNOWN_RESULT
        }

        return VoiceIntentClassification(intent, VoiceIntentConfidence.MEDIUM)
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

        // Negation/prohibition/cancellation. ASR usually drops
        // apostrophes ("dont"); when it keeps them, normalization splits
        // the token ("don't" -> "don" + "t"), so whole contractions and
        // the unambiguous n-less stems are both listed. "won"/"can"
        // stems are deliberately absent (real words; "can you open"
        // must stay recognizable).
        val NEGATION_TOKENS = setOf(
            "don",
            "dont",
            "not",
            "no",
            "never",
            "nobody",
            "stop",
            "cancel",
            "cancels",
            "cancelled",
            "quit",
            "hold",
            "avoid",
            "refrain",
            "mistake",
            "didnt",
            "doesnt",
            "wont",
            "cant",
            "couldnt",
            "shouldnt",
            "wouldnt",
            "isnt",
            "wasnt",
            "arent",
            "werent",
            "havent",
            "hasnt",
            "didn",
            "doesn",
            "couldn",
            "shouldn",
            "wouldn",
            "isn",
            "wasn",
            "aren",
            "weren",
            "haven",
            "hasn",
        )

        // Utterances that are ABOUT acting rather than requests to act:
        // memory/reminders, reported/quoted speech (third-person forms
        // only — "wants" but not "want", so "i want you to close the
        // door" stays a recognizable request), and the speaker's own
        // future plans.
        val NON_COMMAND_TOKENS = setOf(
            // memory / reminders
            "remind",
            "reminds",
            "reminder",
            "remember",
            "remembered",
            "forgot",
            "forget",
            "forgetting",
            // reported / quoted speech
            "said",
            "says",
            "saying",
            "told",
            "tells",
            "telling",
            "wants",
            "wanted",
            "heard",
            "hear",
            "yelled",
            "yell",
            "shouted",
            "shouting",
            "texted",
            "asked",
            "asking",
            "reminded",
            // self-plans and hedged futures
            "ill",
            "gonna",
            "going",
            "plan",
            "planning",
            "plans",
            "intend",
            "intending",
            "intends",
            "might",
            "probably",
            "about",
            "need",
            "needs",
            "needed",
        )

        // A leading state/past/info question word asks ABOUT the door,
        // not for movement. "can"/"could"/"would"/"will"/"why" are
        // deliberately NOT here: polite-request forms stay MEDIUM.
        val QUESTION_LEAD_TOKENS = setOf(
            "is",
            "was",
            "are",
            "were",
            "did",
            "does",
            "who",
            "what",
            "where",
            "when",
            "how",
        )

        // What may directly follow the direction verb in a command.
        val VERB_FOLLOWERS = setOf(
            "the",
            "a",
            "an",
            "my",
            "our",
            "your",
            "garage",
            "door",
            "doors",
            "up",
            "it",
            "that",
            "this",
            "please",
            "uh",
            "um",
        )

        // What may qualify "door"/"doors" for it to mean THE garage
        // door. Possessives are deliberately absent ("my door", "my
        // bedroom door" are other doors; possessive garage forms go
        // through the "garage" object or the HIGH grammar).
        val DOOR_QUALIFIERS = setOf("the", "a", "garage")

        // What may follow the bare object "garage" in a command
        // (function words, continuations, adverbs). An unknown noun
        // after "garage" means a garage-thing, not the garage.
        val GARAGE_FOLLOWERS = setOf(
            "door",
            "doors",
            "please",
            "now",
            "right",
            "all",
            "fully",
            "a",
            "an",
            "and",
            "then",
            "so",
            "for",
            "when",
            "while",
            "before",
            "after",
            "unless",
            "until",
            "in",
            "on",
            "at",
            "im",
            "i",
            "its",
            "it",
            "back",
            "again",
            "up",
            "tonight",
            "today",
            "tomorrow",
            "myself",
            "behind",
            "gently",
            "slowly",
        )

        // Possessives only with garage objects; bare "door"/"doors"
        // only with "the" or nothing ("open my door" is NOT the garage
        // door — found by red-team round 2 as a HIGH safety hole).
        val HIGH_GRAMMAR =
            Regex(
                "^(please )?(open|close|shut) " +
                    "((((the|my|our) )?(garage door|garage doors|garage))|((the )?(door|doors)))" +
                    "( please)?$",
            )
    }
}
