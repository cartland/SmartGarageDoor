---
category: reference
status: active
last_verified: 2026-04-25
---
# Android Changelog

Internal release history. For Play Store "What's New" text, see `distribution/whatsnew/`.

## Versioning

- **Major (X.0.0)** — App rewrite or a change in the core experience so significant the previous version feels like a different product.
- **Minor (X.Y.0)** — A new user-facing feature or capability (something a user couldn't do before), **or** the removal of a user-facing feature.
- **Patch (X.Y.Z)** — Bug fixes, UI polish, performance, refactors. No new capability.

Every version gets an entry in this file (internal history). Play Store `distribution/whatsnew/` gets a line per minor/major — patches roll up into the next minor's line, or get a combined line if promoted to production on their own.

## 2.7.0
- **Settings tab redesigned** to a Material 3 sectioned list. Same capabilities as 2.6.x, reorganized into four sections (Account / Notifications / Tools / About) of one-tap rows in place of the previous stacked expandable cards. Direction A from the 3-phase rewrite plan; PRs #588 (screenshots) + #589 (production wiring).
- **Snooze** now opens in a half-sheet picker (radio list + Cancel/Save) instead of an inline expandable card.
- **Account row** taps open a half-sheet with avatar, name, email, and Sign Out (signed-in) or fires Google Sign-In directly (signed-out).
- **Diagnostics** moved from an expandable card on Settings to a dedicated sub-screen with back navigation, the 8 telemetry counters, and a CSV export button.
- **Version row** taps a Material `AlertDialog` showing version / build / package / built timestamp.
- **Function List** entry button is now a row in the new "Tools" section, gated by the same allowlist as before.
- Retired the legacy expandable-card components (`SnoozeNotificationCard`, `UserInfoCard`, `AndroidAppInfoCard`, `LogSummaryCard`, `ExpandableColumnCard`) along with their unit + instrumented tests.

## 2.6.1
- The "Function list" entry button on the Settings tab is now hidden for users not on the server-maintained allowlist. Allowlisted users see no change. Previously the button appeared for everyone and only the destination screen denied access; the button now disappears at the source for cleaner UX.

## 2.6.0
- New **Function List** screen on the Settings tab. Tapping "Function list" opens a screen with quick-access buttons for the actions you'd otherwise reach across multiple tabs: open or close the garage door, refresh the door status or history, snooze notifications for an hour, sign in or sign out. Reachable via an up-arrow back to Settings.
- Function List is gated by a server-maintained email allowlist. Out of the box the screen shows "Access not enabled for your account" — only users explicitly added to the allowlist (in the Firebase console) see the buttons.

## 2.5.1
- Faster re-tap after a cancelled remote-button confirmation. The "Cancelled" state now clears in 2 seconds (was 10), and the confirmation tap window is extended to 8 seconds (was 5). Re-tapping to retry happens immediately instead of waiting for the long banner.
- Updated clock and calendar icons in the door status card to standard Material designs (stopwatch + calendar grid). Functionally equivalent; visually slightly heavier.
- Fixed swapped TalkBack labels in the door status card: the duration icon now reads "Time since last change" and the timestamp icon reads "Last change timestamp" (previously these were reversed).

## 2.5.0
- Smoother garage door animation. Linear motion while the door is opening or closing, soft settle when it reaches its final state. Warning states (open-too-long, sensor conflict) now indicate with a midway resting position instead of snapping.

## 2.4.4
- Fixed Home tab stuck on "Loading" when the door state hadn't changed since the last fetch (e.g. tap-to-refresh, app launch). FCM pushes already worked because a state change produces a distinct value; the refresh path was silently latched by StateFlow value-equality dedup.

## 2.4.3
- Snooze card updates to "snoozed until X" immediately after saving (no app restart needed)

## 2.4.2
- Snooze card shows "Door notifications enabled" / "Door notifications snoozed until X"
- Snooze status loads immediately instead of showing "Loading"
- Action feedback (Saved/Error) aligned under button
- Improved card layout alignment

## 2.4.1
- Faster sign-in updates and improved stability
- Fixed auth state not updating UI until app restart (reactive AuthStateListener)
- Door notification card now shows current status (enabled/snoozed)

## 2.4
- Redesigned garage door button with confirmation flow and network status diagram
- Improved color contrast for accessibility

## 2.3
- Improved architecture and performance

## 2.2
- New colors and design

## 2.1
- Snooze garage notifications when the door is open
- Snooze applies to all users for the current door position

## 2.0
- Brand new app built with Jetpack Compose
