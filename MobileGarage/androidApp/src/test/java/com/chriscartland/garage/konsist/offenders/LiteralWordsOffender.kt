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

package com.chriscartland.garage.konsist.offenders

/**
 * Deliberately violates `UiObjectStringLiteralKonsistTest`'s rule, so the
 * rule can be shown to fire (its positive control). Never referenced by
 * production code; it lives in the TEST source set and is only ever read by
 * Konsist. Named "offender", not "fixture": the rule skips containers named
 * for preview data (`*Preview*`, `*Fixture*`), and this must not be skipped.
 * `TAG` is the shape the rule must NOT flag — a single token, the kind of
 * thing a test tag or a key is. `words()` returns a constant on purpose
 * (detekt's `FunctionOnlyReturningConstant` is suppressed for exactly that):
 * a String-typed FUNCTION carrying copy is the shape the rule exists for.
 */
@Suppress("unused", "FunctionOnlyReturningConstant")
object LiteralWordsOffender {
    const val SENTINEL = "No data yet"
    const val TAG = "fixture_tag"

    fun words(): String = "Two words"
}
