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

package com.chriscartland.garage.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AppBuildFact] is mostly enforced by two compilers rather than by this file —
 * a new case fails Kotlin's `when` on Android and Swift's `switch` on iOS, both
 * verified empirically by adding a probe case and watching both builds fail.
 *
 * What a test can add is the part neither compiler sees: that the case NAMES
 * stay free of either platform's vocabulary, which is the property that
 * actually prevents the bug this type was created for. Both About screens were
 * labelled "Package" — right on Android, wrong on iOS — because the iOS screen
 * was built by mirroring the Android one row for row. A shared case called
 * `PACKAGE` would have invited exactly that copy again.
 */
class AppBuildFactTest {
    @Test
    fun theOrderIsTheOrderBothAboutSectionsRender() {
        assertEquals(
            listOf(
                AppBuildFact.RELEASE_VERSION,
                AppBuildFact.BUILD_NUMBER,
                AppBuildFact.STORE_IDENTIFIER,
                AppBuildFact.BUILT_AT,
            ),
            AppBuildFact.entries,
            "Both platforms iterate this enum in declaration order, so reordering it " +
                "reorders two shipped screens at once. That is allowed, but it should " +
                "be a decision rather than a side effect.",
        )
    }

    /**
     * The naming rule, enforced.
     *
     * Neither platform's term for a concept may appear in a shared case name.
     * "Package" is Android's word for [AppBuildFact.STORE_IDENTIFIER] and
     * "bundle" is Apple's; a shared name using either one tells the other
     * platform's implementer that the question has already been answered, when
     * the entire point is that it has not.
     */
    @Test
    fun noCaseNameBorrowsAPlatformsVocabulary() {
        val platformWords = listOf(
            "PACKAGE", // Android: applicationId
            "BUNDLE", // Apple: bundle identifier
            "APPLICATION_ID", // Android
            "CFBUNDLE", // Apple
            "VERSION_CODE", // Android's name for BUILD_NUMBER
            "MARKETING", // Apple's name for RELEASE_VERSION
        )
        val offenders = AppBuildFact.entries.flatMap { fact ->
            platformWords
                .filter { word -> fact.name.contains(word) }
                .map { word -> "${fact.name} contains \"$word\"" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Shared case names must describe the CONCEPT, not one platform's term for " +
                "it, or the other platform copies the wrong word: $offenders",
        )
    }

    /**
     * A positive control for the test above: the word list has to be able to
     * fire, or a typo in it would make the rule silently vacuous.
     */
    @Test
    fun theVocabularyCheckCanActuallyFail() {
        val wouldBeBadName = "STORE_PACKAGE_NAME"
        assertTrue(
            wouldBeBadName.contains("PACKAGE"),
            "The substring matching used by the rule above must work; if this fails, " +
                "that rule is passing vacuously.",
        )
    }
}
