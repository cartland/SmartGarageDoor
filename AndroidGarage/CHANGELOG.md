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

## 2.10.1
- **Remote-control pill is now always visible** (debug rollout). The Home tab's Remote-control section now shows a pill for every state — `Remote: unauthorized` / `Remote: checking…` / `Remote: unknown` / `Remote: online` (neutral palette) and `Remote offline · {duration}` (error palette, unchanged from 2.10.0). Previously the pill was Offline-only. Intended as a temporary diagnostic surface to make remote-button connectivity legible at a glance; reverting to Offline-only is a one-line call-site swap back to `RemoteOfflinePill` plus deleting the new `RemoteButtonHealthPill`.
- **Internal: `PreviewSurface` split into `PreviewScreenSurface` (fillMaxSize) and `PreviewComponentSurface` (wrapContentSize).** Component-level reference PNGs (pills, icons, buttons) shrunk from ~20 KB phone-canvas captures to 1–6 KB intrinsic-sized captures. No production impact. Screen-level previews (HomeContent, FunctionListContent) still use `PreviewScreenSurface` so their dark-mode page background fills the canvas.
- **Internal: removed unnecessary `Row` wrapper from the 5 new pill previews** — the pill is the component, not a row of pills.

## 2.10.0
- **New "Remote offline" indicator on the Home tab.** A small rounded pill (wifi-off icon + duration label like "11 min ago") appears next to the Remote-control section title when the wall-button ESP32 device has stopped checking in with the server. Only allowlisted users (the same allowlist that gates the existing remote-button push) ever see it. Healthy/unknown/loading states render no pill — UI is identical to 2.9.5 for everyone else.
- **Server detects the OFFLINE state**, mobile just consumes it. Server flips OFFLINE if the device hasn't polled within 60 sec, and back to ONLINE on the next successful poll. Worst-case detection latency: ~10 min (acknowledged). Server side shipped in `server/24`.
- **Updates flow via data-only FCM** (never a system-tray notification). Subscription is gated by signed-in + allowlist + the device buildTimestamp from server config; the subscription manager unsubscribes-and-re-subscribes correctly when the device buildTimestamp rotates (firmware reflash).
- **Existing door FCM path is unchanged.** `FCMService` dispatches by topic prefix (`buttonHealth-` → new path; everything else → existing door path). The else branch is bit-for-bit identical to 2.9.5.
- 95 new tests across the rollout (server + Android); 4 new screenshot tests for the pill (light + dark, four duration buckets). Full architecture: `docs/BUTTON_HEALTH_ARCHITECTURE.md`.

## 2.9.5
- No user-facing changes. Re-tags the same APK behavior under a fresh `versionName` after a series of internal-only fidelity fixes:
  - **Inner `HomeContent.deviceCheckIn` parameter is now required** (was nullable with a `null` default). The wrapper already passed it, so production behavior is identical — but the type system now stops a future fixture from silently omitting a piece of UI that production always renders. Caught 7 silent instrumented-test gaps the day it landed (#625).
  - **Pill fixtures made realistic** to match the ~10-min ESP32 heartbeat cadence (typical pill reads "5 min ago", not the just-checked-in "30 sec ago" edge case). The framed README Home shot also separates `lastChangeTimeSeconds` and `lastCheckInTimeSeconds` so a days-old door-event timestamp doesn't accidentally feed the pill (#626).
  - **`checkPreviewCoverage` Gradle task** added (ported from battery-butler) — fails the build if any public `*Preview` Composable isn't imported by a screenshot test. Closed 6 currently-uncovered previews (4 `TitleBarCheckInPill*`, 2 `ErrorCard*` edge cases). Coverage 64/70 → 70/70 (#629).

## 2.9.4
- **Device check-in pill moves from the title bar into the Home tab's "Status" section header**, right-aligned in line with the section label. The pill still ticks live (1s cadence via the LiveClock-backed flow) and still flips to the red `errorContainer` variant past the 11-min staleness threshold; the "Not receiving updates from server" alert above the Status card remains as the actionable retry. The TopAppBar is now title-only on every tab.
- **Stale-state pill icon updated** from the custom `outline_signal_disconnected_24` vector to Material's `Icons.Outlined.SignalWifiOff` — same icon the Stale alert banner already uses, so both stale signals on Home now share a single visual vocabulary. Same concept (no signal), small visual delta.
- Trade-off: the device-heartbeat pill is now Home-only — gone from History, Settings, Function list, and Diagnostics. Retained on Home as the at-a-glance ambient indicator; the alert banner remains the actionable surface.

## 2.9.3
- **Settings: "Tools" section moved to the bottom**, below "About" — Account / Notifications / About / Tools is the new order. The Tools section is also wrapped in an `AnimatedVisibility` so it expands and collapses smoothly when the allowlist gate flips for a signed-in user, instead of popping in or out abruptly.

## 2.9.2
- **Last-contact indicator returns to the title bar** as a small rounded pill (antenna icon + duration). Replaces the full-width "Device" section that 2.9.1 placed at the top of Home and History — same data plumbing (LiveClock-driven `DeviceCheckIn.format`, app-scoped staleness check) and same 11-min stale threshold, but denser surface and visible from every main tab. Hidden on the Function list and Diagnostics sub-screens. The pill uses neutral M3 tokens (`surfaceVariant` fresh, `errorContainer` stale) instead of the door-state tint the original pill used pre-2.7/2.8.
- The "Not receiving updates from server" alert with the **Retry** button still appears on Home and History when the device hasn't checked in — the pill is the ambient glance, the alert is the actionable banner.

## 2.9.1
- **Live durations now tick every second again.** The "Since X · Y" line on the Home and History tabs and the device check-in row crawled in 10-second steps in 2.9.0 because the new app-scoped `LiveClock` ticked at 10 s instead of the 1 s cadence the old per-Composable timers used. `MutableStateFlow`'s equality dedup makes per-second ticks free for unchanged formatted strings.
- **Door icon animation restored on Home.** OPENING/CLOSING tweens and terminal/error springs no longer fired in 2.9.0 because `home/HomeContent` froze the icon at its static position. Animation is now active for the live-status icon. History rows stay static (they're past snapshots, by design).
- **Stale-state door coloring restored on Home.** When the device check-in goes stale, the door color now mutes again. 2.9.0 hardcoded the bright variant.
- **FCM-pushed door events update the UI immediately.** Server-pushed events route through `ReceiveFcmDoorEventUseCase` and persist via the repository on the application coroutine scope. Manual pull-to-refresh is no longer required to see the latest state after a push.
- **New "Device" section on Home and History** showing the device heartbeat age (e.g. "30 sec ago") and an offline icon when stale. Replaces the TopAppBar pill the redesign retired.
- **Compose preview backgrounds follow the active theme.** All previews now wrap in a shared `PreviewSurface` instead of `@Preview(showBackground = true)`, so dark-mode reference screenshots no longer have a white edge around dark UI.

## 2.9.0
- No code changes from 2.8.0. Re-tags the same APK behavior under a fresh `versionName` so the Play Store "What's New" surface mentions the **Home tab redesign** (which shipped to internal track in `android/187` as part of 2.8.0 — see the 2.8.0 entry below for full feature list). Patch 2.8.1 is bypassed; it contained only CI-script and skill changes that don't affect the APK.

## 2.8.0
- **History tab redesigned** to a Material 3 sectioned list grouped by day. Each row uses the GarageIcon door art for its leading visual and shows the duration of *that* state ("Open for 6 min" / "Closed for 22 min" / "Since 10:15 AM · 12 min and counting" for the most recent). Anomalies (sensor conflict, stuck opening/closing, unknown state) are surfaced inline with their own door-art variant.
- **Misalignments merge into the previous Open** instead of cluttering the list as a separate row — `OPEN_MISALIGNED` sets a flag on the Opened it follows and shows a "Door was misaligned" tag below the duration.
- **`_TOO_LONG` transitions** that resolve into a terminal carry an inline warning chip ("Took 4 min to open — longer than expected"). Transitions that never resolve still surface as "Stuck opening" / "Stuck closing" anomalies.
- **Home tab redesigned** to match the Settings/History M3 sectioned-list aesthetic, while keeping the hero/action two-zone shape (the door art and the remote button each remain visually prominent — they are not list rows). Status section shows door state, the "Since X · Y" duration line, and an inline warning chip for stuck/anomalous states; Remote-control section hosts the existing button when signed in, or a sign-in row when signed out. Banner stack above Status carries stale-server, missing-permission, and fetch-error alerts in priority order.
- **Pull-to-refresh** on the Home and History tabs via Material 3 `PullToRefreshBox`. Replaces the previous tap-anywhere/tap-on-a-card-to-refresh affordance (hidden on individual rows); the M3 spinner shows progress while the fetch is in flight.
- 3-phase rewrite plan progress: PRs #598 (History Phase 2 + 3), #599 (History pull-to-refresh), #603 (Home Phase 2 + 3), and #605 (companion test fix).
- **`HistoryMapper`** added: pure-function pipeline (raw `DoorEvent`s → `HistoryDay` display data) with 93 unit tests covering merge rules, dedup, duration computation, and formatting in isolation. The Composable takes pre-formatted strings — no logic.
- **`HomeMapper`** added: pure-function pipeline (`LoadingResult<DoorEvent?>` + auth + permission + stale-check → display data + ordered alert list) with 55 unit tests covering all 9 `DoorPosition` variants, alert ordering, time/duration formatting, and Loading/Error/Complete handling. Mirrors the `HistoryMapper` pattern.

## 2.7.1
- Fixed two regressions from the 2.7.0 Settings redesign: the Play Store and Privacy Policy rows in the About section are functional again (they were placeholder no-ops in 2.7.0), and the Settings card now sits at the standard 16dp from the screen edge (was inadvertently doubled to 32dp by overlapping padding wrappers, making the card visibly narrower than the rest of the app's chrome).

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
