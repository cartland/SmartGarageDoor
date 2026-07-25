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
 * A gold-labeled utterance for evaluating [VoiceIntentClassifier]
 * engines. Labels are adjudicated by the policy in
 * docs/VOICE_COMMANDS.md: [goldIntent] is the direction THIS TEXT
 * clearly requests (UNKNOWN otherwise), and [goldActionable] is true
 * only when a safety-first system should move the physical door from
 * this single utterance with no confirmation.
 */
data class VoiceEvalCase(
    val utterance: String,
    val goldIntent: VoiceIntent,
    val goldActionable: Boolean,
    /** Corpus lens the case came from (imperative, question, negation, asrnoise, smalltalk, indirect). */
    val category: String,
    val note: String,
)

/**
 * How the prediction's commitment compares to what the gold label
 * expects. The quality ordering is asymmetric by design:
 * [EXACT] is best, [STRICTER] is acceptable (costs recall, never
 * safety), [LESS_STRICT] is bad (the system over-committed — claimed a
 * direction without grounds, upgraded a non-actionable utterance, or
 * got the direction wrong).
 */
enum class Strictness {
    EXACT,
    STRICTER,
    LESS_STRICT,
}

data class VoiceEvalCaseResult(
    val case: VoiceEvalCase,
    val predicted: VoiceIntentClassification,
    val strictness: Strictness,
    /**
     * The prediction would move the physical door when it must not:
     * HIGH confidence with the wrong direction or on a non-actionable
     * utterance. The hard gate — must always be zero.
     */
    val safetyViolation: Boolean,
)

data class VoiceEvalReport(
    val engineName: String,
    val results: List<VoiceEvalCaseResult>,
) {
    val total: Int get() = results.size
    val exact: Int get() = results.count { it.strictness == Strictness.EXACT }
    val stricter: Int get() = results.count { it.strictness == Strictness.STRICTER }
    val lessStrict: Int get() = results.count { it.strictness == Strictness.LESS_STRICT }
    val safetyViolations: Int get() = results.count { it.safetyViolation }

    /** Of predictions that would act (HIGH), the fraction that should act with that direction. */
    val actionPrecision: Double get() {
        val acting = results.filter { it.predicted.confidence == VoiceIntentConfidence.HIGH }
        if (acting.isEmpty()) return 1.0
        return acting.count { actTruePositive(it) }.toDouble() / acting.size
    }

    /** Of gold-actionable utterances, the fraction acted on with the right direction. */
    val actionRecall: Double get() {
        val shouldAct = results.filter { it.case.goldActionable }
        if (shouldAct.isEmpty()) return 1.0
        return shouldAct.count { actTruePositive(it) }.toDouble() / shouldAct.size
    }

    private fun actTruePositive(r: VoiceEvalCaseResult): Boolean =
        r.predicted.confidence == VoiceIntentConfidence.HIGH &&
            r.case.goldActionable &&
            r.predicted.intent == r.case.goldIntent

    fun textReport(): String =
        buildString {
            appendLine("=== Voice intent eval: $engineName ===")
            appendLine("total: $total")
            appendLine("exact (best):        $exact  (${percent(exact)})")
            appendLine("stricter (okay):     $stricter  (${percent(stricter)})")
            appendLine("less strict (bad):   $lessStrict  (${percent(lessStrict)})")
            appendLine("safety violations:   $safetyViolations  (hard gate: must be 0)")
            appendLine("action precision:    ${format(actionPrecision)}")
            appendLine("action recall:       ${format(actionRecall)}")
            appendLine()
            appendLine("--- by corpus lens (exact/stricter/lessStrict) ---")
            results.groupBy { it.case.category }.toList().sortedBy { it.first }.forEach { (cat, rs) ->
                val e = rs.count { it.strictness == Strictness.EXACT }
                val s = rs.count { it.strictness == Strictness.STRICTER }
                val l = rs.count { it.strictness == Strictness.LESS_STRICT }
                appendLine("$cat: $e/$s/$l of ${rs.size}")
            }
            val bad = results.filter { it.strictness == Strictness.LESS_STRICT }
            if (bad.isNotEmpty()) {
                appendLine()
                appendLine("--- LESS_STRICT cases (bad) ---")
                bad.forEach { appendLine(describe(it)) }
            }
            val missed = results.filter { it.strictness == Strictness.STRICTER }
            if (missed.isNotEmpty()) {
                appendLine()
                appendLine("--- STRICTER cases (recall cost) ---")
                missed.forEach { appendLine(describe(it)) }
            }
        }

    private fun describe(r: VoiceEvalCaseResult): String =
        "  \"${r.case.utterance}\" gold=${r.case.goldIntent}/actionable=${r.case.goldActionable} " +
            "predicted=${r.predicted.intent}/${r.predicted.confidence}" +
            (if (r.safetyViolation) " [SAFETY VIOLATION]" else "")

    private fun percent(n: Int): String = if (total == 0) "0%" else "${n * 100 / total}%"

    private fun format(d: Double): String = "${(d * 1000).toInt() / 10.0}%"
}

/**
 * Engine-agnostic evaluation harness. Run any [VoiceIntentClassifier]
 * against a gold corpus; the report scores every case on the strictness
 * axis (exact is best, stricter is okay, less strict is bad) plus
 * action precision/recall and the zero-tolerance safety gate.
 */
object VoiceIntentEval {
    fun evaluate(
        classifier: VoiceIntentClassifier,
        corpus: List<VoiceEvalCase>,
    ): VoiceEvalReport =
        VoiceEvalReport(
            engineName = classifier.name,
            results = corpus.map { case ->
                val predicted = classifier.classify(case.utterance)
                VoiceEvalCaseResult(
                    case = case,
                    predicted = predicted,
                    strictness = strictnessOf(case, predicted),
                    safetyViolation = predicted.confidence == VoiceIntentConfidence.HIGH &&
                        (predicted.intent != case.goldIntent || !case.goldActionable),
                )
            },
        )

    /**
     * Commitment level the gold label expects: 0 = ignore, 1 = recognize
     * the direction without acting (MEDIUM), 2 = actionable (HIGH).
     */
    private fun expectedLevel(case: VoiceEvalCase): Int =
        when {
            case.goldIntent == VoiceIntent.UNKNOWN -> 0
            case.goldActionable -> 2
            else -> 1
        }

    private fun strictnessOf(
        case: VoiceEvalCase,
        predicted: VoiceIntentClassification,
    ): Strictness {
        // A direction claim that doesn't match the gold direction is an
        // over-commitment regardless of tier: the system asserted
        // something the text doesn't ground.
        val wrongDirection = predicted.intent != VoiceIntent.UNKNOWN &&
            predicted.intent != case.goldIntent
        if (wrongDirection) return Strictness.LESS_STRICT

        val predictedLevel = when {
            predicted.intent == VoiceIntent.UNKNOWN -> 0
            predicted.confidence == VoiceIntentConfidence.HIGH -> 2
            else -> 1
        }
        val expected = expectedLevel(case)
        return when {
            predictedLevel == expected -> Strictness.EXACT
            predictedLevel < expected -> Strictness.STRICTER
            else -> Strictness.LESS_STRICT
        }
    }
}
