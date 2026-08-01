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

/**
 * The facts an About section states about the running build, in the order it
 * states them.
 *
 * Shared presentation model (ADR-035: shared decides, platform words it). What
 * is shared is **which facts are shown and in what order** — that is a product
 * decision, and both stores' About sections should answer the same questions.
 * What is NOT shared is the wording, because the platforms genuinely disagree
 * about what these things are called.
 *
 * ## Why the case names avoid both platforms' vocabulary
 *
 * This type exists because iOS shipped a row labelled **"Package"**. That is
 * Android's word — Android has an `applicationId`, universally called the
 * package name. iOS has a **bundle identifier**, and every Apple-facing
 * surface (Xcode, App Store Connect, the Settings app) calls it the *Bundle
 * ID*. The label was correct on one platform and simply wrong on the other,
 * and it got there the obvious way: the iOS screen was built by mirroring the
 * Android one, row for row, label for label.
 *
 * So the cases here are named after the CONCEPT and never after either
 * platform's term for it. Someone wording [STORE_IDENTIFIER] has to ask what
 * their own platform calls that, which is exactly the question that was
 * skipped. A case called `PACKAGE` would have invited the same copy again.
 *
 * Each case's KDoc records the native API it comes from on both platforms, and
 * the user-facing label each one should use, so the answer is in front of
 * whoever adds the next one.
 *
 * ## What this does and does not guarantee
 *
 * It guarantees the two About sections show the same facts in the same order:
 * both platforms exhaust this enum (Kotlin `when`, Swift `switch` with no
 * `default` — see CLAUDE.md on enum bridging), so adding a case fails BOTH
 * builds until both have worded it.
 *
 * It cannot guarantee the wording is *right* — nothing in a type system can.
 * The defence there is the naming above plus the per-case notes.
 */
enum class AppBuildFact {
    /**
     * The human-facing release version, e.g. `2.23.6`.
     *
     * Android `versionName` · iOS `CFBundleShortVersionString`.
     * Both platforms label this **"Version"** — the one row where the two
     * genuinely agree.
     */
    RELEASE_VERSION,

    /**
     * The monotonic build counter behind that version, e.g. `277`.
     *
     * Android `versionCode` (a `Long`) · iOS `CFBundleVersion` (a *string*,
     * and permitted to be dotted like `1.2.3`). The types differ, which is
     * why each platform supplies its own already-formatted value rather than
     * this enum carrying one.
     *
     * Both label it **"Build"**. Android's own term is "version code", but
     * "Build" is what the Play Console shows a human and it matches iOS.
     */
    BUILD_NUMBER,

    /**
     * The identifier the app is published under, e.g.
     * `com.chriscartland.garage`.
     *
     * Android `applicationId` · iOS `CFBundleIdentifier`
     * (`Bundle.main.bundleIdentifier`).
     *
     * **The labels differ and must:** Android says **"Package"**, iOS says
     * **"Bundle ID"**. The strings happen to be identical for this app, which
     * is precisely why the mislabel survived review — nothing looked wrong,
     * only the noun was.
     */
    STORE_IDENTIFIER,

    /**
     * When this binary was built.
     *
     * Android reads it from the build config · iOS from the executable's
     * modification date. Both label it **"Built"**, which carries no platform
     * vocabulary.
     */
    BUILT_AT,
}
