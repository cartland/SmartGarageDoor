package com.chriscartland.garage.domain.model

/**
 * App-wide external links, shown identically on every platform.
 *
 * Single source of truth so the Android Settings privacy link and the iOS
 * Settings privacy link cannot drift. Lives in `domain.model` so both the
 * Android UI and the iOS app (via the shared framework) reference the same
 * value — the URL is locale-invariant config, not display copy, so unlike
 * user-visible strings it belongs in shared rather than per-platform.
 */
object AppLinks {
    /** Privacy policy, opened from Settings on Android and iOS. */
    const val PRIVACY_POLICY_URL: String = "https://chriscart.land/garage-privacy-policy"
}
