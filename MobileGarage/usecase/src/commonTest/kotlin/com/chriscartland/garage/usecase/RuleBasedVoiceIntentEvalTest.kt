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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the Rules v1 engine over the gold corpus and gates on the
 * strictness metrics (see [VoiceIntentEval]):
 *
 * - EXACT is best, STRICTER is acceptable, LESS_STRICT is bad.
 * - Safety violations (would move the door when it must not) are a
 *   hard zero gate — this assertion must NEVER be relaxed.
 * - The remaining counts are pinned baselines: they fail on ANY drift
 *   so a rule change's effect on the corpus is always a deliberate,
 *   reviewed baseline update in the same PR. Improving exact/lessStrict
 *   is welcome — update the pins and say why in the PR.
 */
class RuleBasedVoiceIntentEvalTest {
    private val report = VoiceIntentEval.evaluate(
        classifier = RuleBasedVoiceIntentClassifier(),
        corpus = VoiceEvalCorpus.CASES,
    )

    @Test
    fun safetyViolationsAreZero() {
        assertEquals(
            0,
            report.safetyViolations,
            "HARD GATE: the classifier would act when it must not.\n${report.textReport()}",
        )
    }

    @Test
    fun strictnessBaselines() {
        // Rules v3 baseline (2026-07-24, after red-team round 2):
        // 148 exact / 28 stricter / 0 lessStrict of 176; action
        // precision 100%, recall 35.4%. Zero over-commitments — the
        // v3 qualifier/follower checks eliminated every round-1 and
        // round-2 LESS_STRICT case. All 28 stricter are deliberate:
        // compound/preamble imperatives and adverbial variants held at
        // MEDIUM by the exact-imperative HIGH grammar — the ranked
        // menu for any future promote-to-HIGH decision.
        assertEquals(176, report.total, report.textReport())
        assertEquals(148, report.exact, "exact drifted.\n${report.textReport()}")
        assertEquals(28, report.stricter, "stricter drifted.\n${report.textReport()}")
        assertEquals(0, report.lessStrict, "lessStrict drifted.\n${report.textReport()}")
    }
}
