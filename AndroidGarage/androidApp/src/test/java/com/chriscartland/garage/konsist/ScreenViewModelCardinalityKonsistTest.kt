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
 */

package com.chriscartland.garage.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test

/**
 * Konsist pilot: ADR-026 "one ViewModel per screen" — Konsist version.
 *
 * Same rule as the existing `checkScreenViewModelCardinality` Gradle task
 * (in `buildSrc/src/main/kotlin/architecture/ScreenViewModelCheckTask.kt`).
 * Intentionally additive — both checks run; this test exists to evaluate
 * Konsist as a complement to the bespoke `buildSrc/` tasks.
 *
 * Rule: each `*Content.kt` screen Composable file under
 * `androidApp/src/main/.../ui/` may import at most one type from
 * `com.chriscartland.garage.viewmodel.*` whose name ends in `ViewModel`.
 * The `Default` prefix is stripped so `AuthViewModel` (interface) +
 * `DefaultAuthViewModel` (impl) collapse to one logical VM.
 *
 * Tradeoff observations (left for future-PR judgment):
 *  - Konsist's `file.imports` is typed/structural — no regex false-positive
 *    surface (the legacy task could be fooled by `import ...ViewModelKt`
 *    or `import ...ViewModelStore` if the project ever pulled those names
 *    into scope; this check ignores them naturally because they don't end
 *    in `ViewModel` literally — but the same regex defense in the legacy
 *    task is reactive, not structural).
 *  - The Konsist scope's per-test parse cost is ~5-15s; subsequent
 *    assertions in the same test class amortize it. The legacy task
 *    scans files at task-execution time with no parser — faster cold,
 *    no amortization across other checks.
 *  - Exemptions: the legacy task supports per-file exemption via
 *    `screen-viewmodel-exemptions.txt` (currently empty as of android/213
 *    / 2.15.3). The Konsist version below does NOT honor that file —
 *    intentional, since the file has been empty for the entire pilot
 *    window. If a future legacy multi-VM screen lands, add it to BOTH
 *    enforcement points (legacy file + a Konsist `withoutNameMatching`
 *    filter) or replace this test entirely.
 */
class ScreenViewModelCardinalityKonsistTest {
    @Test
    fun `each Content kt file imports at most one ViewModel`() {
        val violations = Konsist
            .scopeFromProduction()
            .files
            .filter { it.name.endsWith("Content.kt") }
            .mapNotNull { file ->
                val viewModels = file.imports
                    .map { it.name }
                    .filter { it.startsWith("com.chriscartland.garage.viewmodel.") }
                    .map { it.substringAfterLast('.') }
                    .filter { it.endsWith("ViewModel") }
                    .map { it.removePrefix("Default") }
                    .distinct()
                if (viewModels.size > 1) {
                    file.path to viewModels
                } else {
                    null
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("ADR-026 violation: screen Composable file(s) import more than one ViewModel.")
                appendLine()
                violations.forEach { (path, vms) ->
                    appendLine("  $path → ${vms.size} VMs: ${vms.sorted().joinToString(", ")}")
                }
                appendLine()
                appendLine("Aggregate the UseCases this screen needs inside a single ViewModel.")
                appendLine("Detected by Konsist; the legacy buildSrc check enforces the same rule.")
            }
            throw AssertionError(message)
        }
    }
}
