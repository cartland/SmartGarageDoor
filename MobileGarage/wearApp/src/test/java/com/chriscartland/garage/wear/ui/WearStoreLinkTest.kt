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

package com.chriscartland.garage.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings link out to Play, and how a build names itself beside it.
 *
 * Both rules here are ones the compiler is happy to let you break, and neither
 * fails loudly at runtime: the wrong URI scheme opens the wrong app on the
 * wrong device, and the wrong package name opens a "not found" page. Each one
 * still *opens something*, so only a test says which.
 */
class WearStoreLinkTest {
    /**
     * `market://` is what reaches the watch's own Play Store.
     *
     * The phone app links to Play with `https://play.google.com/store/...`, and
     * copying that idiom here is the obvious-looking mistake. Resolved against a
     * Wear OS 5 image, the two go to different apps:
     *
     * ```
     * market://details?id=…    -> com.android.vending/…WearMainActivity
     * https://play.google.com/ -> …wearable.settings/…ResolverActivity
     * ```
     *
     * The second is the "open this on your phone" handoff, so the https form
     * would send the user to their phone rather than to the Update button on
     * the watch in their hand.
     */
    @Test
    fun theLinkGoesToTheWatchStoreNotTheOpenOnPhoneHandoff() {
        assertTrue(
            "must use the market:// scheme; https resolves to the open-on-phone handoff",
            WearStoreLink.listingUri().startsWith("market://details?id="),
        )
    }

    /**
     * The link names the released package, never the debug one.
     *
     * Debug builds carry `applicationIdSuffix = ".debug"`, so deriving this from
     * `BuildConfig.APPLICATION_ID` would send exactly the builds a developer
     * tests with to a package that does not exist on Play.
     */
    @Test
    fun theLinkNamesThePackageThatIsActuallyOnPlay() {
        assertEquals(
            "market://details?id=com.chriscartland.garage",
            WearStoreLink.listingUri(),
        )
        assertTrue(
            "the debug suffix must never reach the store link",
            !WearStoreLink.listingUri().contains(".debug"),
        )
    }

    /**
     * A released build is recognised as one — and reports only THAT, never the
     * tag it came from. The release tag is internal plumbing; it means something
     * against this repo's tags and the Play track log, and nothing to someone
     * wearing the watch.
     */
    @Test
    fun aReleasedBuildIsRecognisedWithoutNamingItsTag() {
        assertTrue(WearStoreLink.isReleaseBuild(15))
    }

    /**
     * A build that never came from a tag is still told apart from a released
     * one — "this is not a release" and "you are 15 releases behind" are
     * different answers, and settings exists to tell them apart.
     */
    @Test
    fun aBuildWithNoTagIsNotPassedOffAsARelease() {
        assertTrue(!WearStoreLink.isReleaseBuild(0))
        assertTrue(!WearStoreLink.isReleaseBuild(-1))
    }
}
