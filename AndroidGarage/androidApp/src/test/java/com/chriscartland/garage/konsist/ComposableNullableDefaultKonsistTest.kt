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
 * Konsist: `*Content.kt` screen Composables must NOT declare UI-display
 * data parameters as `T? = null`.
 *
 * Motivation: PR #625 / android/195 era — `HomeContent.deviceCheckIn`
 * was previously `DeviceCheckInDisplay? = null`. Fixtures (previews,
 * screenshot tests) silently passed nothing, the screenshot rendered
 * with no device-check-in pill, the framed README PNG diverged from
 * production. Flipping the param to required (`DeviceCheckInDisplay`,
 * no default) surfaced 7 silent test gaps the same day.
 *
 * The rule that prevents the next instance of this bug class:
 * **UI-display data parameters on screen-level Composables must be
 * required** — no nullable type with `null` default. If a screen
 * legitimately should render with no value, model it explicitly (an
 * empty sealed type, a sentinel display state) rather than as `null`.
 *
 * Exclusions (intentional, NOT flagged):
 * - `*ViewModel? = null` — the route-entry Composable's hook for tests
 *   to inject a fake VM. The inner stateless body still has required
 *   data params; this nullable VM param does NOT cross into UI shape.
 * - `Boolean? = null` — tri-state feature flag pattern (CLAUDE.md
 *   "Feature flags" entry; canonical: `FunctionListContent.accessGranted`).
 *   Null distinct from `false` is intentional semantics.
 * - Function types (`(...) -> Unit?` or `((...) -> ...)? = null`) —
 *   optional callbacks. The Composable should still render when no
 *   handler is provided; this is independent of UI shape.
 *
 * Konsist param type names are taken from the source AST. `String?` is
 * the type's nullable form; `String` is non-nullable. Default values
 * are the literal source string (`"null"`, `"emptyList()"`, etc.) — the
 * check matches `"null"` exactly.
 *
 * Drift policy: if a future legitimate pattern needs to be allowlisted,
 * add a `withoutNameMatching` filter here AND document the legitimate
 * use case in the file's KDoc. Don't grow the allowlist silently.
 */
class ComposableNullableDefaultKonsistTest {
    @Test
    fun `Content kt Composables avoid nullable data params with null default`() {
        val contentFiles = Konsist
            .scopeFromProduction()
            .files
            // Konsist's `file.name` returns the simple name without
            // extension (e.g. `HomeContent`, not `HomeContent.kt`),
            // so we filter on `path` instead. Discovered while
            // wiring this test: the prior pattern `it.name.endsWith
            // ("Content.kt")` matched nothing and the test passed
            // vacuously. Mirror this fix in any future Konsist test.
            .filter { it.path.endsWith("Content.kt") }
        require(contentFiles.isNotEmpty()) {
            "No *Content.kt files in Konsist scope — the scope filter " +
                "is broken and this test would pass vacuously. See " +
                "CLAUDE.md 'Scope sanity pattern'."
        }

        val violations = contentFiles.flatMap { file ->
            file
                .functions(includeNested = true, includeLocal = false)
                .filter { fn -> fn.hasAnnotation { it.name == "Composable" } }
                // Restrict to the SCREEN-LEVEL Composable in each
                // file (matching the file's base name). Sub-component
                // helpers in the same file (e.g. `SettingsRow` inside
                // `SettingsContent.kt`) have their own design rules
                // for optional affordances and are NOT what PR #625
                // was about.
                .filter { fn -> fn.name == file.name }
                .flatMap { fn ->
                    // Outer route-entry Composables take a
                    // `<X>ViewModel? = null` for test injection AND
                    // their UI-data params are defaulted from the
                    // VM. The inner stateless body is where required
                    // UI params belong. Detect outer-entry shape by
                    // the presence of a ViewModel parameter and
                    // skip — the inner stateless body in the same
                    // file (often in a `home/` / `settings/` etc.
                    // sub-package) is the one that should be checked.
                    val hasViewModelParam = fn.parameters.any { p ->
                        p.type.name.endsWith("ViewModel")
                    }
                    if (hasViewModelParam) return@flatMap emptyList()
                    fn.parameters
                        .filter { param ->
                            // Konsist's type semantics:
                            //   - `param.type.name` is the base type
                            //     name *without* the nullable `?`
                            //     suffix (`Boolean`, not `Boolean?`).
                            //   - `param.type.isNullable` carries the
                            //     nullability bit separately.
                            //   - `param.defaultValue` returns the
                            //     null Kotlin value when there is no
                            //     declared default — distinct from
                            //     "the default literal is null" which
                            //     surfaces as a non-null String "null".
                            val baseTypeName = param.type.name
                            val defaultLiteral = param.defaultValue
                            if (!param.type.isNullable) return@filter false
                            if (defaultLiteral != "null") return@filter false
                            if (baseTypeName.endsWith("ViewModel")) return@filter false
                            if (baseTypeName == "Boolean") return@filter false
                            // Skip function-type parameters — `(...) -> T`
                            // serializes with a `->` in the AST type string.
                            if ("->" in baseTypeName) return@filter false
                            true
                        }.map { param -> Triple(file.path, fn.name, param) }
                }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("UI-display data parameter(s) on *Content.kt Composables use `T? = null`:")
                appendLine()
                violations.forEach { (path, fnName, param) ->
                    appendLine("  $path")
                    appendLine("    fun $fnName(... ${param.name}: ${param.type.name} = ${param.defaultValue} ...)")
                }
                appendLine()
                appendLine("Flip the parameter to required (drop the `?` and `= null` default).")
                appendLine("Fixtures and screenshot tests must now pass an explicit value, surfacing")
                appendLine("any silent omission that would diverge from production rendering. See")
                appendLine("the file's KDoc for the PR #625 motivation and the legitimate exclusions")
                appendLine("(`*ViewModel? = null` for test injection, `Boolean? = null` for tri-state).")
            }
            throw AssertionError(message)
        }
    }
}
