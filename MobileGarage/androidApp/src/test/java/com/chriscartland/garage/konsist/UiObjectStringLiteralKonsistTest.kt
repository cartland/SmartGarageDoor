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

package com.chriscartland.garage.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User-visible copy must be a string resource, not a Kotlin literal
 * (ADR-035 and CLAUDE.md § "String style"). `checkNoLiteralStringsInCompose`
 * (buildSrc) sees `Text("…")` inside Composables and nothing else, so a
 * `String`-typed function or property that returns the words — the
 * check-in pill's `label(age)` and its `NO_DATA_LABEL` sentinel until
 * strategy 4.6, the button-health pill's labels until 2.6 — sailed past it.
 * This is the structural half: any `String`-typed declaration under `ui/`
 * whose source carries a multi-word literal is a violation.
 *
 * Heuristic, and a fence: shell-free PSI can see the type, but not whether
 * a literal is copy. Two words (letters separated by a space, after
 * dropping `$name` / `${…}` interpolations) is the line — test tags, keys,
 * format punctuation and single tokens are not copy; "No data yet" is.
 * Preview fixtures are excluded by path (`Preview`), as sample data.
 *
 * [theRuleCanActuallyFire] runs the same predicate over a fixture in the
 * TEST scope that deliberately violates it; without that, a Konsist scope
 * that quietly returned no `ui/` files would make this pass vacuously.
 */
class UiObjectStringLiteralKonsistTest {
    @Test
    fun `String-typed declarations under ui do not carry multi-word literals`() {
        val uiFiles =
            Konsist
                .scopeFromProduction()
                .files
                .filter { it.path.contains("/com/chriscartland/garage/ui/") }
                .filter { !it.path.contains("Preview") }
        require(uiFiles.isNotEmpty()) {
            "No ui/ files in Konsist scope — the scope filter is broken and this test " +
                "would pass vacuously. See CLAUDE.md 'Scope sanity pattern'."
        }
        val violations = uiFiles.flatMap { violationsIn(it) }
        if (violations.isNotEmpty()) {
            val message =
                buildString {
                    appendLine("String-typed declaration(s) under ui/ carry multi-word literals:")
                    appendLine()
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("User-visible copy belongs in strings.xml, resolved in the Composable")
                    appendLine("(stringResource / pluralStringResource; see RemoteOfflineText and")
                    appendLine("DeviceCheckInWords). If the value is genuinely not copy, make it a")
                    appendLine("typed decision instead of words, or move the constant out of ui/.")
                }
            throw AssertionError(message)
        }
    }

    @Test
    fun theRuleCanActuallyFire() {
        // Positive control: a fixture in the TEST scope that returns words.
        val fixture =
            Konsist
                .scopeFromTest()
                .files
                .filter { it.path.endsWith("LiteralWordsOffender.kt") }
        require(fixture.isNotEmpty()) { "LiteralWordsOffender.kt is not in the test scope" }
        val violations = fixture.flatMap { violationsIn(it) }
        assertTrue(
            "the rule flagged nothing in a fixture written to violate it — the predicate is blind",
            violations.any { it.contains("words()") } && violations.any { it.contains("SENTINEL") },
        )
        // And it must NOT flag the fixture's single-token constant (a test tag).
        assertTrue(violations.none { it.contains("TAG") })
    }

    private fun violationsIn(file: KoFileDeclaration): List<String> {
        if (EXEMPT_PATH_SUFFIXES.any { file.path.endsWith(it) }) return emptyList()
        val functions =
            file
                .functions(includeNested = true, includeLocal = false)
                .filter { fn -> fn.returnType?.name == "String" }
                .filter { fn -> !fn.hasAnnotation { it.name == "Preview" } }
                .filter { fn -> !isPreviewData(fn.containingDeclaration) }
                .mapNotNull { fn -> firstCopyLiteral(fn.text)?.let { "${file.path}: fun ${fn.name}() returns $it" } }
        val properties =
            file
                .properties(includeNested = true)
                // A declared `String`, or an inferred one — `const val X = "…"`
                // is how the old NO_DATA_LABEL sentinel was written.
                .filter { prop -> prop.type?.name == "String" || (prop.type == null && prop.text.contains("= \"")) }
                .filter { prop -> !isPreviewData(prop.containingDeclaration) }
                .mapNotNull { prop -> firstCopyLiteral(prop.text)?.let { "${file.path}: val ${prop.name} = $it" } }
        return functions + properties
    }

    /** Sample copy for previews lives in objects named for it (`HomePreviewData`); that is fixture, not production words. */
    private fun isPreviewData(container: KoBaseDeclaration?): Boolean {
        val name = (container as? KoNameProvider)?.name ?: return false
        return name.contains("Preview") || name.contains("Fixture")
    }

    private companion object {
        /**
         * Burn-down: files that still carry copy in Kotlin, each with the PR
         * that retires it. Empty since strategy 4.7 deleted the two it opened
         * with (`ui/DoorStatusCard.kt`, `ui/TimeFormats.kt`); the check does
         * not fail on a stale entry, so remove one the moment its file is
         * fixed, and keep this empty unless a real, time-boxed exemption is
         * added with the PR that will retire it.
         */
        val EXEMPT_PATH_SUFFIXES: List<String> = emptyList()

        val STRING_LITERAL = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val INTERPOLATION = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")
        val HAS_LETTERS = Regex("""[A-Za-z]{2,}""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /** A date/number pattern handed to a formatter is a format, not a sentence ("EEE, MMM d"). */
        val FORMAT_PATTERN_CALL = Regex("""ofPattern\(\s*"[^"]*"""")

        /**
         * The first literal in [source] that reads as copy — two or more
         * space-separated words — or null. Comments are dropped first: a
         * declaration's text carries its KDoc, whose examples ("Just now",
         * "Apr 28, 9:47 PM") are exactly the sentences a words object
         * explains and must not be mistaken for its return value. Words are
         * split on WHITESPACE, so a dotted key ("com.chriscartland.garage.X")
         * or a snake_case tag is one token and never copy.
         */
        fun firstCopyLiteral(source: String): String? {
            val code =
                source
                    .replace(BLOCK_COMMENT, " ")
                    .replace(LINE_COMMENT, " ")
                    .replace(FORMAT_PATTERN_CALL, "ofPattern(")
            return STRING_LITERAL
                .findAll(code)
                .map { it.groupValues[1] }
                .firstOrNull { literal ->
                    val spoken = literal.replace(INTERPOLATION, " ")
                    spoken.split(Regex("""\s+""")).count { HAS_LETTERS.containsMatchIn(it) } >= 2
                }?.let { "\"$it\"" }
        }
    }
}
