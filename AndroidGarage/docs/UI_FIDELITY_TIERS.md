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
| Door-status visualization (canvas geometry, panel layout, proportions) | **1** | Brand through-line. **Geometry + fill palette + the full animation spec + the live trajectory are now shared** — `:domain` `GarageDoorGeometry` + `GarageDoorPalette` + `DoorAnimation` (offset constants + `DoorPosition → offset/overlay/color-state` mappings + `ANIMATION_DURATION_SECONDS`) + `DoorAnimationMemory` (replay policy), consumed by both canvases (pinned by `GarageDoorGeometryTest` / `GarageDoorPaletteTest` / `DoorAnimationTest` / `DoorAnimationMemoryTest`). iOS now drives the same 12 s slide + spring-settle + replay-once-per-event (#959/#960). The door visualization — static and dynamic — is fully shared. |
| Door-state color semantics (closed / open / opening-too-long / unknown) | **1** | Color **is** meaning; must not drift. **Now shared** — iOS `DoorPalette` reads `:domain` `GarageDoorPalette` (the same 12 fill values Android `Color.kt` consumes). |
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
| Door **animation** trajectory + once-per-event replay | **1 (spec) / 3 (execution)** | Splits by layer: the offsets/targets/duration/replay *policy* are Tier-1 provable (hoist + `commonTest`); the real-time interpolation is Tier-3 best-effort (native engine + reduce-motion). **Spec AND trajectory now shared/converged** — `:domain` `DoorAnimation` (offsets/targets/static/overlay/`ANIMATION_DURATION_SECONDS`, pinned by `DoorAnimationTest`) + `DoorAnimationMemory` (replay, pinned by `DoorAnimationMemoryTest`). iOS now drives the live trajectory (12 s linear slide, spring-settle on the terminal event, replay-once-per-event) via SwiftUI `withAnimation` reading those shared params — the **(b)** convergence shipped in #959/#960. Execution stays native per platform (Compose `Animatable` vs SwiftUI `@State`). See [§ Animation: split the spec from the execution](#animation-split-the-spec-provable-from-the-execution-best-effort) below + ADR-025. |
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

### Hoisting an *existing* duplicated Tier-1 constant (verified-no-op recipe)

When a Tier-1 value already exists as hand-duplicated literals on both platforms (geometry, palette, offsets), the hoist is a **refactor whose success = a proven no-op**. Recipe (canonical: `GarageDoorGeometry`, #952):

1. **Confirm the Kotlin and Swift values are already identical** *before* writing anything. If they differ, that is a finding to decide on — not a silent merge that changes one platform's render.
2. **Move them to a `:domain` `object`**, both UIs read via SKIE (`Object.shared.CONST` — names bridge verbatim; see CLAUDE.md § SKIE bridge shapes for `const val` / `List<Float>`). Derive related values (e.g. a clip inset from frame + gap) so they can't disagree.
3. **Pin the values/relationships with a `commonTest`** — this is the drift guard the duplication lacked; it runs on both platforms.
4. **Prove the no-op:** iOS snapshots **byte-identical** after `generate-ios-screenshots.sh` (local, real render — `git status` shows zero PNG diff), and the **Android CI screenshot-comparison** job green vs the *unchanged* golden PNGs (Android renders blank locally, so that half is CI-side). A non-identical snapshot is the alarm to investigate, not accept.
   - **Exception — non-integer constants.** Integer-valued constants (geometry: `300f`, `22f`; palette: `0xFF…`) are exactly representable, so regen is bit-for-bit. A value that is *not* exactly representable in IEEE-754 (door offsets `-0.65f` / `-0.20f` / `-0.35f`) renders a **benign sub-pixel delta** when the shared Kotlin `Float` widens to `CGFloat` differently than iOS's old `Double` literal. Signature: a **single ~1px-tall horizontal line** per affected edge (diff `bbox` is `(x0, y, x1, y+1)`; `DoorAnimation` slice (a) hit exactly this on 2 snapshots, ≤0.02% of pixels, max channel Δ ~53). That is a semantic no-op — and actually *improves* parity, since Android Compose also renders offsets as `Float`, so the old iOS `Double` was the divergent one. **Accept the regen** for a 1-D edge line; a **2-D region** of difference is a real change — investigate. Always inspect the diff `bbox` shape before accepting.

A plain `object` needs no DI → both DI graphs are untouched (no two-DI-component trap). Scope by blast radius: geometry (touches only the canvases) before palette (Android theme path) before offsets/mappings (Android animation API).

### Animation: split the spec (provable) from the execution (best-effort)

An animation is **not** one un-verifiable thing — and "best-effort consistency vs provable consistency" is a false choice. Split it into three layers and verify each at the strongest level it admits. Most of "do the two platforms animate the same" then becomes provable; only the genuinely-native residue stays best-effort.

1. **Spec — parameters + decision logic. Tier 1, fully provable.** The offset constants, durations, and the pure state→target mappings (`targetPositionFor` / `fromPositionFor` / `useSpringFor` / `staticPositionFor` / `overlayFor`) + the replay-once-per-event *policy*. This is *what the animation does*. Hoist to a `:domain` `object`, pin in `commonTest` (exhaustive `when` + specific mappings); static-endpoint snapshots prove both platforms render closed/open/midway identically. Exactly the geometry/palette treatment.
2. **Trajectory shape — a sampleable pure function. Tier 1, provable *by construction*.** Don't share only "duration + an easing-curve enum" — expose `offsetAt(progress: Float): Float`, a pure evaluation of the from→target curve. Then the motion *shape* is snapshot-able **deterministically** at sampled points: render a **static** door at `offsetAt(0.25/0.5/0.75)` (you compute the offset; you never run the animation engine). `commonTest` pins the curve; a fixture snapshots mid-slide on both platforms with no engine involved. This is the lever that drags most of "it looks the same in motion" out of best-effort and into provable.
3. **Real-time execution — Tier 3, genuinely best-effort, and correctly so.** The engine driving `progress` 0→1 over wall-clock (Compose `Animatable.animateTo(tween/spring)` vs SwiftUI `.animation`), plus frame pacing, mid-flight interruption/retargeting, and OS reduce-motion / animator-duration-scale. Do **not** try to byte-match these — each OS should honor its own scheduler and accessibility settings. Verify by **structure, not pixels**: the view must *feed* the shared spec, with no literal duration / curve in it. A lint/grep banning literal animation durations + easing in the door views is the drift guard (the enforcement analog to the const-hoist `commonTest`).

**The boundary:** shared & provable = *what the animation does* (targets, curve-as-function, replay policy, durations); best-effort = *only the real-time clock + OS accessibility adaptation that drives it*. That is the smallest possible best-effort surface, and it is exactly the part that should be native. So animation **splits across tiers** (1 for the spec, 3 for the execution) — classify each layer, not "the animation" as a blob.

**State of this codebase (both shipped).** The shared spec is `:domain` `DoorAnimation` (contract in [`DOOR_ANIMATION.md`](./DOOR_ANIMATION.md)); both Android (`GarageIcon.kt`, Compose `Animatable`) and iOS (`GarageDoorCanvas.swift`, SwiftUI `@State` + `withAnimation`) drive it. The offsets/mappings work was **two** things, both now done:
- **(a) Offsets + offset/overlay mappings — SHIPPED.** The 5 offset constants + the full pure `DoorAnimation` spec object (`targetPositionFor`/`fromPositionFor`/`useSpringFor`/`staticPositionFor`/`overlayFor`) + the `DoorOverlayKind` enum now live in `:domain` `DoorAnimation`, read by Android `GarageIcon` and iOS `GarageDoorView` (iOS consumes `staticPositionFor` + `overlayFor`; Android also drives its live trajectory from target/from/spring); pinned by `DoorAnimationTest` (commonTest, moved from the Android unit test). Verified pure no-op (mappings already identical; byte-identical iOS snapshots). **`colorState` shipped too** — `DoorAnimation.colorStateFor` + the shared `DoorColorState` enum; Android `doorColorState()` delegates via a theme-package `typealias` (zero-churn for the 3 UI consumers), iOS `DoorPalette` calls it directly. The door visualization is now fully shared `:domain`.
- **(b) Trajectory convergence — SHIPPED (#959/#960).** The maintainer confirmed the 12 s pacing is **brand-essential** (real door travel + ~2 s network slack, so the terminal event lands mid-slide and the icon springs to target) → Tier 1, iOS must match. The duration (`ANIMATION_DURATION_SECONDS`) + replay policy (`DoorMotionKey`/`DoorAnimationMemory`) were hoisted to `:domain` (#959, pinned by `DoorAnimationTest`/`DoorAnimationMemoryTest`); iOS's `GarageDoorView` then gained an animated mode (#960) — a SwiftUI `@State` offset driven by `withAnimation`: 12 s linear slide for OPENING/CLOSING, slow no-overshoot spring settle on the terminal event, replay-once-per-event via the shared memory (held app-root in `MainScreen`, injected via a `\.doorAnimationMemory` environment value). The real-time SwiftUI interpolation stays honest Tier-3 best-effort; the *parameters* it animates toward are all shared/provable. (We did not need the sampleable `offsetAt` for the linear case — the curve is `lerp(from, target)`, which SwiftUI's `.linear` reproduces exactly; the param pins + byte-identical static snapshots were sufficient.)

## References

- [ADR-032](./DECISIONS.md#adr-032) — the tiers, decision rule, and rationale (canonical).
- [ADR-029](./DECISIONS.md#adr-029) — the parity principle this operationalizes.
- [ADR-031](./DECISIONS.md#adr-031) + [`PRESENTATION_MODEL_REALIZATION.md`](./PRESENTATION_MODEL_REALIZATION.md) — the Tier-2 layer + parity-gap inventory.
- [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md) § 1 — iOS construction status + open parity gaps.
