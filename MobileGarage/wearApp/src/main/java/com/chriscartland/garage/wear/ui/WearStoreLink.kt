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

/**
 * The watch's link to its own Play Store listing, and how the running build
 * identifies itself next to it.
 *
 * `market://` is deliberate and is NOT interchangeable with the
 * `https://play.google.com/store/…` form the phone app uses in `ProfileContent`.
 * On Wear OS the two resolve to different apps entirely:
 *
 * ```
 * market://details?id=…    -> com.android.vending/…WearMainActivity      (the watch's Play Store)
 * https://play.google.com/ -> …wearable.settings/…ResolverActivity       ("open this on your phone")
 * ```
 *
 * Measured with `adb shell cmd package resolve-activity` against a Wear OS 5
 * image. Copying the phone's https idiom here would therefore punt the user to
 * their phone instead of showing the watch's own Update button, which is the
 * opposite of the point — hence this constant rather than a shared helper.
 *
 * Every device this app can install on has that Play Store (`minSdk = 30`, i.e.
 * Wear OS 3+), so there is no legacy fallback. It can still be missing on a
 * watch shipped without Play services, which the caller handles by catching
 * `ActivityNotFoundException`. A pre-flight `resolveActivity` check would NOT be
 * a valid substitute: under Android 11 package visibility that query returns
 * null unless the manifest declares a matching `<queries>` element, so it would
 * report "no Play Store" on watches that have one. Launching is not filtered
 * that way, so trying and catching is both simpler and more accurate.
 */
internal object WearStoreLink {
    /**
     * The package name of the Play listing.
     *
     * Hardcoded rather than read from `BuildConfig.APPLICATION_ID` because debug
     * builds carry `applicationIdSuffix = ".debug"`, which is not a package that
     * exists on Play — deriving the link from `APPLICATION_ID` would open a
     * "not found" page on exactly the builds a developer is testing with.
     * Mirrors the phone's `playStorePackageName` (wired in `AppComponent`), which
     * hardcodes it for the same reason.
     */
    const val PLAY_STORE_PACKAGE_NAME: String = "com.chriscartland.garage"

    /** The watch Play Store listing for [packageName]. */
    fun listingUri(packageName: String = PLAY_STORE_PACKAGE_NAME): String = "market://details?id=$packageName"

    /**
     * Whether this build came from a release tag at all.
     *
     * Deliberately a BOOLEAN, not the `wear/N` tag it is derived from. Settings
     * used to render that tag, and it is internal release plumbing — it means
     * something against this repo's tags and the Play track log, and nothing to
     * anyone wearing the watch. Returning a boolean keeps the tag string out of
     * the app entirely rather than relying on every future caller choosing not
     * to display it.
     *
     * The distinction it does keep is the one worth surfacing: a build that
     * never came from a release is a different thing from an old one, and
     * `versionName` alone cannot say which you are holding. Local builds have no
     * tag number (Gradle falls back to 0).
     */
    fun isReleaseBuild(tagNumber: Int): Boolean = tagNumber > 0
}
