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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G3 structural rule (docs/DATA_GRAPH_PLAN.md): a `MutableStateFlow`
 * in `:viewmodel` whose type argument is a `:domain`-declared type is a
 * mirror of repository-owned state — a second copy that drifts
 * (android/164-168). INTENTIONALLY ADDITIVE to the buildSrc
 * `checkViewModelStateFlow` task, which bans four named types; this
 * inverts the denylist into "any `:domain` type", per
 * DATA_CACHING_STRATEGY P1's 'Better'.
 *
 * Allowed, per the same P1 text:
 *  - `LoadingResult<…>` wrappers — VM-owned presentation phase
 *    (ADR-023), seeded from `upstream.value` (that seeding is P2's
 *    concern, not this test's).
 *  - `*Action` types — VM-owned action overlays (`SnoozeAction`,
 *    `WatchInstallAction`); domain declares the vocabulary, the VM owns
 *    the transient overlay state (decision-rule step 5).
 *
 * Text-level extraction, deliberately: Konsist's PSI has no type
 * inference, so an inferred `MutableStateFlow(SomeEnum.VALUE)` has no
 * declared type to inspect. Two patterns cover the shapes in the tree —
 * an explicit type argument, and an enum-constant seed. Both feed
 * [baseTypeOf]/[enumSeedOf], which the positive controls exercise
 * directly (the vacuous-pass rule).
 */
class ViewModelDomainMirrorKonsistTest {
    /** Burn-down list — goal: empty. A stale entry (no longer violating) fails the test. */
    private val exemptions = setOf(
        // Nav-rail settings mirrors of DataStore-backed prefs; tracked
        // for burn-down (seed-from-upstream or a settings pass-through).
        "ProfileViewModel.kt: NavigationRailItemPosition",
    )

    private val scope = Konsist.scopeFromProduction()

    private fun domainTypeNames(): Set<String> {
        val domainFiles = scope.files.filter { it.path.contains("/domain/src/commonMain/") }
        require(domainFiles.isNotEmpty()) { "Konsist scope found no domain files — scope misconfigured" }
        return (
            domainFiles.flatMap { it.classes(includeNested = true) } +
                domainFiles.flatMap { it.interfaces(includeNested = true) }
        ).map { it.name }.toSet()
    }

    private fun domainEnumNames(): Set<String> {
        val domainFiles = scope.files.filter { it.path.contains("/domain/src/commonMain/") }
        return domainFiles
            .flatMap { it.classes(includeNested = true) }
            .filter { it.hasEnumModifier }
            .map { it.name }
            .toSet()
    }

    @Test
    fun `no viewmodel MutableStateFlow mirrors a bare domain type`() {
        val domainTypes = domainTypeNames()
        val domainEnums = domainEnumNames()
        val vmFiles = scope.files.filter { it.path.contains("/viewmodel/src/commonMain/") }
        require(vmFiles.isNotEmpty()) { "Konsist scope found no viewmodel files — scope misconfigured" }

        val violations = vmFiles
            .flatMap { file ->
                stripComments(file.text).lines().mapNotNull { line ->
                    val base = baseTypeOf(line) ?: enumSeedOf(line, domainEnums)
                    base
                        ?.takeIf { it in domainTypes && !isAllowed(it) }
                        ?.let { "${file.name}.kt: $it" }
                }
            }.toSet()

        val unexempted = violations - exemptions
        val stale = exemptions - violations
        assertEquals("Unexempted domain-type mirrors in :viewmodel", emptySet<String>(), unexempted)
        assertEquals("Stale exemptions (fixed — remove from the list)", emptySet<String>(), stale)
    }

    // ---- positive controls: the extraction can actually fire ----

    @Test
    fun `the explicit-type pattern can actually fire`() {
        assertEquals("DoorEvent", baseTypeOf("    private val x = MutableStateFlow<DoorEvent?>(null)"))
        // LoadingResult wrapper resolves to the WRAPPER, which is allowed.
        val wrapped = baseTypeOf("MutableStateFlow<LoadingResult<DoorEvent?>>(seed)")
        assertEquals("LoadingResult", wrapped)
        assertTrue(isAllowed(wrapped!!))
        assertTrue(isAllowed("SnoozeAction"))
        assertTrue(!isAllowed("DoorEvent"))
        // And DoorEvent really is a domain type — the set the rule
        // checks against is not empty-by-accident.
        assertTrue("DoorEvent" in domainTypeNames())
    }

    @Test
    fun `the enum-seed pattern can actually fire`() {
        val enums = setOf("NavigationRailItemPosition")
        assertEquals(
            "NavigationRailItemPosition",
            enumSeedOf("MutableStateFlow(NavigationRailItemPosition.TopAligned)", enums),
        )
        // An object-member seed (Int constant) must NOT match: the arg
        // type is the member's, not the object's.
        assertEquals(null, enumSeedOf("MutableStateFlow(NavigationRailLayout.DEFAULT_TOP_PADDING_DP)", enums))
    }

    @Test
    fun `a mirror mentioned only in a comment does not flag`() {
        // The RoomSchemaTest precedent: strip comments before scanning,
        // so a comment DESCRIBING the banned shape (e.g. explaining why
        // a pass-through replaced a mirror) is not a violation — and a
        // commented-out mirror cannot silently satisfy anything either.
        val text =
            """
            // was: MutableStateFlow<DoorEvent?>(null)
            /* MutableStateFlow<DoorEvent?>(null) */
            val real = MutableStateFlow<DoorEvent?>(null)
            """.trimIndent()
        val hits = stripComments(text).lines().mapNotNull(::baseTypeOf)
        assertEquals(listOf("DoorEvent"), hits)
    }

    // ---- extraction (pure; exercised by the controls above) ----

    /** Remove block + line comments so prose about the pattern can't flag (or hide) anything. */
    private fun stripComments(text: String): String =
        text
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun isAllowed(base: String): Boolean = base == "LoadingResult" || base.endsWith("Action")

    /** Base type of an explicit `MutableStateFlow<X…>` declaration, e.g. `LoadingResult` or `DoorEvent`. */
    private fun baseTypeOf(line: String): String? = Regex("""MutableStateFlow<\s*([A-Za-z][A-Za-z0-9_]*)""").find(line)?.groupValues?.get(1)

    /** Enum-constant seed `MutableStateFlow(SomeEnum.VALUE)` where the receiver is a domain enum. */
    private fun enumSeedOf(
        line: String,
        domainEnums: Set<String>,
    ): String? {
        if (line.contains("MutableStateFlow<")) return null // explicit form handles it
        return Regex("""MutableStateFlow\(\s*([A-Za-z][A-Za-z0-9_]*)\.""")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.takeIf { it in domainEnums }
    }
}
