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
 * TOTALITY BY CONSTRUCTION: Konsist's PSI has no type inference, so a
 * bare `MutableStateFlow(expr)` has no inspectable type — the original
 * rule could only heuristically catch inferred seeds. Instead of a
 * better heuristic, the companion rule below REQUIRES every
 * `MutableStateFlow` in `:viewmodel` to spell its type argument
 * explicitly. With explicitness enforced, the mirror rule's extraction
 * covers every declaration — nothing can escape by inference. (The
 * language-construct fix: make the checkable form the only legal form.)
 *
 * The scan is whole-text with `\s*` between tokens, so a declaration
 * split across lines cannot escape either. Comments are stripped first
 * (the RoomSchemaTest precedent), so prose about the pattern neither
 * flags nor hides anything.
 *
 * Local-run caveat (observed empirically): Konsist reads SOURCES at
 * runtime, but Gradle's up-to-date check for this test task tracks the
 * compiled classpath. A bytecode-identical source edit (e.g. deleting
 * an explicit type argument that inference reproduces) does not
 * re-trigger the task locally — run with `--rerun-tasks` when probing
 * the rule. Real violations add declarations, which change bytecode
 * and re-trigger normally; CI always runs fresh.
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

    private fun vmFiles() =
        scope.files
            .filter { it.path.contains("/viewmodel/src/commonMain/") }
            .also { require(it.isNotEmpty()) { "Konsist scope found no viewmodel files — scope misconfigured" } }

    @Test
    fun `no viewmodel MutableStateFlow mirrors a bare domain type`() {
        val domainTypes = domainTypeNames()

        val violations = vmFiles()
            .flatMap { file ->
                explicitTypeArgs(stripComments(file.text))
                    .filter { it in domainTypes && !isAllowed(it) }
                    .map { "${file.name}.kt: $it" }
            }.toSet()

        val unexempted = violations - exemptions
        val stale = exemptions - violations
        assertEquals("Unexempted domain-type mirrors in :viewmodel", emptySet<String>(), unexempted)
        assertEquals("Stale exemptions (fixed — remove from the list)", emptySet<String>(), stale)
    }

    @Test
    fun `every viewmodel MutableStateFlow declares its type argument explicitly`() {
        // The companion rule that makes the mirror rule TOTAL: without
        // it, `MutableStateFlow(someDomainValue)` would carry a domain
        // type the text-level scan cannot see. Imports are dropped from
        // the scan (the import line legitimately ends in the bare name).
        val violations = vmFiles().flatMap { file ->
            val text = stripComments(file.text)
                .lines()
                .filterNot { it.trimStart().startsWith("import ") }
                .joinToString("\n")
            Regex("""MutableStateFlow(?!\s*<)""")
                .findAll(text)
                .map { "${file.name}.kt: inferred MutableStateFlow type at offset ${it.range.first}" }
                .toList()
        }
        assertEquals(emptyList<String>(), violations)
    }

    // ---- positive controls: the extraction can actually fire ----

    @Test
    fun `the explicit-type pattern can actually fire`() {
        assertEquals(
            listOf("DoorEvent"),
            explicitTypeArgs("    private val x = MutableStateFlow<DoorEvent?>(null)"),
        )
        // A declaration split across lines cannot escape the scan.
        assertEquals(
            listOf("DoorEvent"),
            explicitTypeArgs("MutableStateFlow<\n    DoorEvent?>(null)"),
        )
        // LoadingResult wrapper resolves to the WRAPPER, which is allowed.
        assertEquals(listOf("LoadingResult"), explicitTypeArgs("MutableStateFlow<LoadingResult<DoorEvent?>>(seed)"))
        assertTrue(isAllowed("LoadingResult"))
        assertTrue(isAllowed("SnoozeAction"))
        assertTrue(!isAllowed("DoorEvent"))
        // And DoorEvent really is a domain type — the set the rule
        // checks against is not empty-by-accident.
        assertTrue("DoorEvent" in domainTypeNames())
    }

    @Test
    fun `the explicitness rule can actually fire`() {
        val bare = Regex("""MutableStateFlow(?!\s*<)""")
        assertTrue(bare.containsMatchIn("val x = MutableStateFlow(someValue)"))
        assertTrue(!bare.containsMatchIn("val x = MutableStateFlow<Boolean>(false)"))
        assertTrue(!bare.containsMatchIn("val x = MutableStateFlow< Boolean >(false)"))
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
        assertEquals(listOf("DoorEvent"), explicitTypeArgs(stripComments(text)))
    }

    @Test
    fun `a url in a string does not truncate the scan`() {
        // stripComments must not treat :// as a line comment — a URL
        // earlier on a line would otherwise hide a mirror after it.
        val line = """val x = "https://example.com"; val y = MutableStateFlow<DoorEvent?>(null)"""
        assertEquals(listOf("DoorEvent"), explicitTypeArgs(stripComments(line)))
    }

    // ---- extraction (pure; exercised by the controls above) ----

    /** Remove block + line comments so prose about the pattern can't flag (or hide) anything. */
    private fun stripComments(text: String): String =
        text
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .lines()
            .joinToString("\n") { line ->
                // `(?<!:)//` so a URL's :// is not a comment start.
                val match = Regex("""(?<!:)//""").find(line)
                if (match != null) line.substring(0, match.range.first) else line
            }

    private fun isAllowed(base: String): Boolean = base == "LoadingResult" || base.endsWith("Action")

    /** Base type of every explicit `MutableStateFlow<X…>` in [text], whole-text and newline-tolerant. */
    private fun explicitTypeArgs(text: String): List<String> =
        Regex("""MutableStateFlow\s*<\s*([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
}
