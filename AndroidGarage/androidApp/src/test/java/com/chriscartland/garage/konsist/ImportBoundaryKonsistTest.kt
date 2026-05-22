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
 * Konsist redundant import-boundary checks.
 *
 * Mirrors the per-module `checkImportBoundary` Gradle tasks
 * (`buildSrc/src/main/kotlin/importboundary/ImportBoundaryCheckTask.kt`)
 * with the same forbidden-prefix + allowed-prefix model. INTENTIONALLY
 * ADDITIVE — both the Gradle tasks and these tests run on every PR.
 *
 * Why redundant: two enforcement points catch different failure modes.
 * The Gradle task fails the build at `validate.sh` time with a Gradle-
 * task message. These tests fail at `:androidApp:testDebugUnitTest`
 * time with a JUnit assertion. CI surfaces both; a single regression
 * fires both, which makes the rule's existence obvious to anyone
 * reading either the build log or the test report.
 *
 * Per-module rules mirror the build.gradle.kts configurations:
 *   - `:domain`             — no forbidden imports
 *   - `:data`               — no forbidden imports (Ktor is OK)
 *   - `:data-local`         — `androidx.room.`, `androidx.sqlite.`, `androidx.datastore.` allowed
 *   - `:usecase`            — no forbidden imports (pure Kotlin)
 *   - `:viewmodel`          — `androidx.lifecycle.` allowed
 *   - `:presentation-model` — no forbidden imports
 *
 * Plus a project-wide rule: no `org.mockito.*` imports anywhere in
 * production code (the existing `checkNoMockitoImports` lint task).
 *
 * Drift policy: when ImportBoundaryCheckTask's defaults change or a
 * module's allowedPrefixes is edited, this file's per-test config
 * must be updated too. The two enforcement points are kept in sync
 * by hand; if either drifts the other catches the gap. No automated
 * "rules in sync" check (yet) — explicit follow-up if a future PR
 * touches more than one module's allowed-prefix list.
 */
class ImportBoundaryKonsistTest {
    private val defaultForbidden = listOf(
        "android.",
        "androidx.",
        "com.google.firebase.",
        "com.google.android.",
        "java.time.",
        "java.text.",
        "java.util.Date",
        "java.util.Locale",
    )

    @Test
    fun `domain commonMain has no forbidden imports`() {
        assertNoForbiddenImports(
            pathSubstring = "/domain/src/commonMain/kotlin/",
            allowedPrefixes = emptyList(),
        )
    }

    @Test
    fun `data commonMain has no forbidden imports`() {
        assertNoForbiddenImports(
            pathSubstring = "/data/src/commonMain/kotlin/",
            allowedPrefixes = emptyList(),
        )
    }

    @Test
    fun `data-local commonMain only allows room sqlite datastore from androidx`() {
        assertNoForbiddenImports(
            pathSubstring = "/data-local/src/commonMain/kotlin/",
            allowedPrefixes = listOf(
                "androidx.room.",
                "androidx.sqlite.",
                "androidx.datastore.",
            ),
        )
    }

    @Test
    fun `usecase commonMain has no forbidden imports`() {
        assertNoForbiddenImports(
            pathSubstring = "/usecase/src/commonMain/kotlin/",
            allowedPrefixes = emptyList(),
        )
    }

    @Test
    fun `viewmodel commonMain only allows androidx lifecycle`() {
        assertNoForbiddenImports(
            pathSubstring = "/viewmodel/src/commonMain/kotlin/",
            allowedPrefixes = listOf("androidx.lifecycle."),
        )
    }

    @Test
    fun `presentation-model commonMain has no forbidden imports`() {
        assertNoForbiddenImports(
            pathSubstring = "/presentation-model/src/commonMain/kotlin/",
            allowedPrefixes = emptyList(),
        )
    }

    @Test
    fun `no production code imports org mockito`() {
        val violations = Konsist
            .scopeFromProduction()
            .files
            .flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it.startsWith("org.mockito.") }
                    .map { file.path to it }
            }
        if (violations.isNotEmpty()) {
            failWith(
                rule = "no `org.mockito.*` imports in production code",
                violations = violations,
            )
        }
    }

    private fun assertNoForbiddenImports(
        pathSubstring: String,
        allowedPrefixes: List<String>,
    ) {
        val filesInScope = Konsist
            .scopeFromProduction()
            .files
            .filter { pathSubstring in it.path }
        // Sanity check: if Konsist's scope can't see this module's source,
        // the test would pass vacuously. Every module under audit has at
        // least one production file. Failing loudly here makes a scope
        // misconfiguration obvious instead of silent-green.
        require(filesInScope.isNotEmpty()) {
            "Konsist scope contains no files under '$pathSubstring' — " +
                "test would pass vacuously. Likely cause: scopeFromProduction() " +
                "doesn't include this module from the :androidApp test classpath. " +
                "If a module was renamed or removed, update this test's pathSubstring."
        }
        val violations = filesInScope.flatMap { file ->
            file.imports
                .map { it.name }
                .filter { importName ->
                    allowedPrefixes.none { importName.startsWith(it) } &&
                        defaultForbidden.any { importName.startsWith(it) }
                }.map { file.path to it }
        }
        if (violations.isNotEmpty()) {
            failWith(
                rule = "no forbidden-prefix imports under $pathSubstring " +
                    "(allowed exceptions: ${allowedPrefixes.ifEmpty { listOf("none") }})",
                violations = violations,
            )
        }
    }

    private fun failWith(
        rule: String,
        violations: List<Pair<String, String>>,
    ): Nothing {
        val message = buildString {
            appendLine("Import boundary violation (${violations.size}): $rule")
            appendLine()
            violations.forEach { (path, importName) ->
                appendLine("  $path → import $importName")
            }
            appendLine()
            appendLine(
                "These prefixes are forbidden in shared KMP modules to keep them " +
                    "free of Android / Firebase / JVM-only types.",
            )
            appendLine(
                "The same rule is enforced by the per-module `checkImportBoundary` " +
                    "Gradle task in `buildSrc/` — this Konsist test is the redundant " +
                    "second enforcement point.",
            )
        }
        throw AssertionError(message)
    }
}
