---
category: reference
status: active
last_verified: 2026-06-28
---

# UI fidelity tiers — per-surface classification

Which Android↔iOS surfaces should **match to the (visual) pixel**, which should
**converge in structure but style natively**, which should **adopt the OS idiom**,
and which are **deliberately one-platform**.

The four tiers, the decision rule, and the rationale are owned by
**[ADR-032](./DECISIONS.md#adr-032)** (which refines [ADR-029](./DECISIONS.md#adr-029)).
This doc is the **living classification** — it stays in sync with the screens as
they're built. When you add or change a surface, classify it here.

## Quick lookup — the decision rule

For any UI element, apply top-down, **first match wins**:

1. Carries brand identity, or **looks different ⇒ means different**? → **Tier 1 — Identity (brand-locked)**
2. Content whose **structure** should match but styling can be native? → **Tier 2 — Convergent**
3. Navigation / chrome / system integration with a strong OS idiom? → **Tier 3 — Native-idiomatic**
4. Genuinely one-platform, by **deliberate** decision? → **Tier 4 — Platform-exclusive**

Tie-breaker: when torn between adjacent tiers, prefer the **more-shared** one unless
a platform idiom clearly serves the user better. A surface may **split** across tiers.

| Tier | Owed | Enforcing layer |
|---|---|---|
| **1 Identity** | Visually equivalent (shape/palette/icon/proportions/color→meaning); *not* literal pixels | Shared **constants** in `domain` + visual contract |
| **2 Convergent** | Same sections/order/data/label-meaning; native styling | Shared **`presentation-model`** typed state (ADR-031) |
| **3 Native-idiomatic** | Platform form diverges; semantics shared | Shared **`domain`/`usecase`**; per-platform UI |
| **4 Platform-exclusive** | One platform, deliberate end state | None — recorded exception |

> **Parity gap ≠ Tier 4.** A Tier 1–3 element that exists on only one platform because
> iOS is mid-build is a *gap to close* (tracked in
> [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md) + the realization plan's inventory),
> **not** a Tier 4 exception. Tier 4 is a deliberate "one platform, on purpose."

## Cross-cutting

| Surface / element | Tier | Notes |
|---|---|---|
| Door-status visualization (canvas geometry, panel layout, proportions) | **1** | Brand through-line. Geometry constants are the next shared-constant candidate (`GarageDoorCanvas.swift` ↔ `AnimatableGarageDoor.kt` currently duplicate them). |
| Door-state color semantics (closed / open / opening-too-long / unknown) | **1** | Color **is** meaning; must not drift. `DoorPalette` (iOS) mirrors Android `Color.kt` by hand today → candidate to share. |
| App icon, feature graphic, brand naming ("Garage") | **1** | Identity assets. See the `play-store-assets` skill + `distribution/playstore/`. |
| Tab **set** (Home / History / Settings) | **1** | The *which-tabs* is identity (ADR-029 §3). Both ship **3** top-level tabs; Functions + Diagnostics are `developerAccess`-gated **sub-screens of Settings** (Android rows / iOS `NavigationLink`s), not tabs. |
| Tab **rendering** (iOS `TabView` vs Android bottom-nav / nav rail / 3-pane) | **3** | Platform idiom; adaptive layout is Android-only (see Tier 4 below). |
| Theme tokens (color roles, typography scale, spacing) | **2** | iOS intentionally uses **system semantic colors + system accent**, *not* a port of Android's ~30-role scheme: `iosApp/Core/Theme/GarageColors.swift` is a 4-color stub (`statusOk`/`statusOpen`/`statusWarning`/`cardBackground`) and applies no `.tint`. Only the door-state colors (above) are brand-locked Tier 1. If the Android pastel-blue primary (`#A6C8FF`) is brand, escalate that single token to Tier 1 (shared constant) + apply on iOS. |
| Navigation chrome (sheets, back gesture, modal presentation) | **3** | `.sheet` vs `ModalBottomSheet`; swipe-back vs predictive-back. |
| Window insets / safe area (edge-to-edge vs safe-area) | **3** | Platform layout idiom. Android `WindowInsets.safeDrawing`; iOS safe-area. |
| Notification presentation (channels/heads-up vs `UNUserNotificationCenter`) | **3** | Shared = the door-state semantics + copy *intent*; rendering is per-OS. See [`docs/NOTIFICATION_RELIABILITY.md`](../../docs/NOTIFICATION_RELIABILITY.md). |
| Status/warning **copy** intent | **1 (intent) / 2 (string)** | Meaning is identity; the localized string is per-UI (ADR-031 "no shared user-visible strings"). |

## Home

| Surface / element | Tier | Notes |
|---|---|---|
| Door visualization (static state render) | **1** | See cross-cutting. iOS shipped #919. |
| Door **animation** trajectory + once-per-event replay | **1 (intent), deferred** | Identity-level, but **verification-gap deferred** — trajectory/replay can't be CLI/snapshot-verified on iOS. See realization plan § Door-canvas animation + ADR-025. |
| Status pill, check-in pill, button-health pill — **meaning + color** | **1** | Device-availability grammar + state colors are brand-locked. ⚠️ Icon **family currently diverges**: Android uses Material `Sensors`/`SensorsOff`, iOS uses SF `antenna.radiowaves…(.slash)` — a Tier-1 drift to reconcile (pick one visually-equivalent family; tracked in PENDING_FOLLOWUPS § 4). Other glyphs (lock, sync, question-circle) + color→meaning already agree. |
| Pill capsule **chrome** (shape, padding, typography) | **2** | Native styling of the shell. |
| Status-card information architecture | **2** | Same sections/data; card vs `List` styling local. |
| "Since · duration" status line | **2** | Shared `SinceStatus` / `ElapsedDuration` (P2); clock/duration formatting per-UI. |
| Alert banners (stale / fetch-error / permission-missing) | **2** | Shared `HomeAlert` + `HomeAlertMapper` (P4); permission probe + escalation copy per-UI. |
| Info sheets (tap a pill → explanation) | **2 (content) / 3 (sheet idiom)** | Static copy mirrored verbatim; `.sheet` vs `ModalBottomSheet`. |
| Network-progress diagram | **2, not started** | Optional richness; remote-button-tied. |

## History

| Surface / element | Tier | Notes |
|---|---|---|
| Day grouping, door icons, durations, transit/anomaly/misaligned tags, empty-state | **2** | Shared `DoorEvent→HistoryDay` pipeline + typed state (P3); 60-case test in commonTest. |
| Day/time/duration **formatting** | **2 (per-UI)** | Trivial `%60` decomposition stays per-UI (P3 rationale); the gnarly merge/group is shared. |
| Alert banner (stale check-in → "Not receiving updates" + reset-FCM Retry) | **2, iOS gap** | Android renders it above the list (`DoorHistoryContent.kt`); iOS has none, so a stale iOS user gets no warning + no recovery. Route via a shared `HistoryAlert` mapper (mirroring `HomeAlertMapper`). Tracked in PENDING_FOLLOWUPS § 4. |
| Load-more (scroll-to-end pagination) | **2, deferred** | iOS renders the current `recentDoorEvents` window; the shared VM already exposes `paginationState` + `fetchOlderDoorEvents()` — iOS just needs to consume them. P3 follow-up; tracked in PENDING_FOLLOWUPS § 4. |

## Settings / Profile

| Surface / element | Tier | Notes |
|---|---|---|
| 3-tab restructure + gated Developer section | **2 (structure) / 3 (Settings idiom)** | Same content; iOS `List`/sectioned-settings idiom vs Android sections. |
| Account name/email, About/version | **2** | Shared data; native row styling. |
| Tap-to-copy version/build/package | **2 (behavior) / 3 (clipboard idiom)** | iOS `UIPasteboard` + "Copied ✓"; Android `LocalClipboardManager` + API-33 Toast gate. |
| Snooze controls + typed failure messages | **2** | Shared `SnoozeAction.Failed` cases; per-UI verbatim copy. |
| Snooze permission state (denied → tap-to-enable) | **2 (state) / 3 (permission flow)** | Shared "granted" boolean; native permission request. |
| Privacy-policy link | **1 (URL) / 3 (link affordance)** | URL is a **shared constant** `AppLinks.PRIVACY_POLICY_URL` (canonical Tier-1-config example); the link/`Link` affordance is native. |
| App Store / Play Store link | **3, deferred** | Per-platform store; iOS link waits on a published listing. |

## Functions

| Surface / element | Tier | Notes |
|---|---|---|
| "Developers only" warning | **1 (intent) / 2 (string)** | Verbatim `function_list_warning`. |
| Test-notification sandbox (topic copy/subscribe/change) | **2** | Diagnostic tool; shared `testNotificationState`. |
| Sign in/out | **4 (intentionally skipped on iOS)** | Settings owns auth; the screen is `functionListAccess`-gated, so a sign-in button is unreachable and sign-out pops you out — platform-native structure. |
| Copy auth token | **2, not started** | Needs an iOS token-fetch bridge + pasteboard-sensitivity decision. |

## Diagnostics

| Surface / element | Tier | Notes |
|---|---|---|
| Log buffer list, lifetime counters, "Clear all" | **2** | Shared diagnostics state; native list/button styling. |
| Export CSV | **2, not started** | Needs a shared CSV-content API + iOS share sheet (shared change → both DI graphs). |
| Copy auth token | **2, not started** | Same bridge gap as Functions. |

## Tier 4 — deliberate platform-exclusive (recorded exceptions)

These are **not** parity gaps; they are end states, kept on one platform on purpose.

| Element | Platform | Why exclusive |
|---|---|---|
| Adaptive 3-pane / navigation rail | Android | No iOS equivalent; iPad `NavigationSplitView` is a *separate* future item, deprioritized — see [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md) § 1. |
| Interactive swipe-back gesture | iOS | Native iOS affordance; Android uses predictive-back. |
| Clipboard `EXTRA_IS_SENSITIVE` redaction (API 33+) | Android | OS-specific redaction flag; iOS pasteboard has no clean equivalent (decision pending if copy-token lands). |

## How to use this when building a surface

1. Classify the surface with the decision rule above (it may split across tiers).
2. Build it through that tier's **enforcing layer**:
   - Tier 1 → put the locale-invariant value in a shared `domain` constant; match the visual.
   - Tier 2 → a `presentation-model` slice (typed state + shared test in commonTest), rendered per-UI. Follow the [realization plan](./PRESENTATION_MODEL_REALIZATION.md) checklist.
   - Tier 3 → share the `domain`/`usecase` semantics; build the UI natively.
   - Tier 4 → add a row to the table above with the reason.
3. Update this doc + `last_verified` in the same PR.

## References

- [ADR-032](./DECISIONS.md#adr-032) — the tiers, decision rule, and rationale (canonical).
- [ADR-029](./DECISIONS.md#adr-029) — the parity principle this operationalizes.
- [ADR-031](./DECISIONS.md#adr-031) + [`PRESENTATION_MODEL_REALIZATION.md`](./PRESENTATION_MODEL_REALIZATION.md) — the Tier-2 layer + parity-gap inventory.
- [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md) § 1 — iOS construction status + open parity gaps.
