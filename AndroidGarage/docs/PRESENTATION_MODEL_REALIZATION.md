---
category: plan
status: active
last_verified: 2026-06-27
---

# Presentation-model realization — phased plan

Realize the dormant shared `presentation-model` layer so the per-screen **display
mapping** lives in `commonMain`, is exposed by the shared `Default*ViewModel`s,
and is rendered by **both** Jetpack Compose (Android) and SwiftUI (iOS). This is
the implementation plan for **[ADR-031](./DECISIONS.md#adr-031)** (decision +
principles) and the mechanism for closing the iOS↔Android feature-parity gaps
from the 2026-06-27 audit (ADR-029).

> **One-line goal:** move `androidApp/`'s `HomeMapper` / `HistoryMapper` /
> `DoorWarning` / `HomeStatusFormatter` into shared typed state, so iOS gets the
> richness for free and Android's UI gets thinner.

## Why (short)

The display mapping (typed warnings, "Since … · duration", history-entry kinds,
day grouping) is in `androidApp/` today, unreachable from iOS. Re-implementing it
in Swift = a second source of truth that drifts. A shared `presentation-model`
module already exists (`HomeScreenState`, `DoorHistoryScreenState`,
`ProfileScreenState`) but is skeletal and unused. Wire it up.

## Architectural rules (from ADR-031)

- **Shared emits typed state, never user-visible strings.** Sealed types, enums,
  numeric parts (`days/hours/minutes`, epoch deltas). Each UI renders localized
  strings at render time (Compose `stringResource`/plurals; SwiftUI formatters).
- Mapping logic is **pure functions in `presentation-model/commonMain`** and/or
  computed `StateFlow`s on the shared VM. No platform types.
- Android `*Content.kt` and iOS `*ContentView`/wrapper become thin renderers of
  the VM's typed state. The `androidApp/` mapper is **deleted** per slice.
- Mapper unit tests move to shared `commonTest`.
- **Calendar / time-zone work uses `kotlinx-datetime`, never `java.time`** — the
  latter is import-boundary-forbidden in `presentation-model` (P3 added the dep).
  Take the zone as an IANA id `String` (`zone.id` on Android,
  `TimeZone.current.identifier` on iOS) and **expose primitive date parts**
  (`year`/`monthNumber`/`dayOfMonth`), not a `kotlinx-datetime` type, so no date
  library leaks across the SKIE/Swift bridge (keep the dep `implementation`, not
  `api`). Canonical: `DayLabel.Date` (P3).
- **Shape shared APIs for SKIE's bridge limits when they're consumed from Swift.**
  SKIE does **not** bridge Kotlin **default arguments** — a shared
  `fun f(x, y, z = default)` fails the iOS compile with *"missing argument for
  parameter 'z'"* (Swift sees only the full-arity signature). Provide an explicit
  overload (`fun f(x, y) = f(x, y, default)`) instead of a Kotlin default.
  Canonical: `CheckInStatusMapper.forCheckIn`'s two-arg overload (P5). This bites
  only at the CI-exact iOS build (`generate-ios-screenshots.sh` / iOS CI), never
  `validate.sh` (Android-only) — so build iOS locally after any shared-API change.
- **A pure mapper called from each UI is a valid alternative to a VM `StateFlow`**
  when the existing consumer already maps at render (Android `remember`). It needs
  **no VM ctor change → no DI-graph edits** (avoids the two-DI-component trap).
  Use it when the slice doesn't otherwise need new VM state. Canonical:
  `HistoryMapper` (P3). Prefer a computed VM `StateFlow` only when the value must
  be observed/seeded for no-flicker (P1/P2).
- **Share the gnarly *decision*, not necessarily every byte of arithmetic.** If a
  slice's only-remaining-shared candidate is a trivial `%60` decomposition AND
  sharing it would churn a behavior-preserving render path, leave it per-UI (a
  small Swift mirror is fine). Share when the *choice* is non-trivial (P2's
  `ElapsedDuration` bucket); skip when it's just division (P3's duration parts).

## Per-slice checklist

Each slice is one PR (cross-cutting; `validate.sh` required):

- [ ] Add/enrich the typed state in `presentation-model/commonMain` (sealed
      types/enums/numeric parts).
- [ ] Add the pure mapping (domain → typed state) in `presentation-model`
      (or a computed `StateFlow` on the VM). No strings, no platform types.
- [ ] Expose it from the shared `Default*ViewModel` (enrich `*ScreenState` or add
      a `StateFlow`). Update **both** DI graphs if the ctor changes —
      `androidApp/.../di/AppComponent.kt` **and**
      `iosFramework/.../NativeComponent.kt` (two-DI-component rule).
- [ ] Android: point `*Content.kt` at the VM state; **delete** the `androidApp/`
      mapper + its now-dead types.
- [ ] Move the mapper's unit tests to shared `commonTest` (guards the contract on
      every platform).
- [ ] iOS: render the typed state in the `*ContentView` (+ expose via the
      `*ViewModelWrapper`); add/refresh `#Preview`s → snapshot gallery.
- [ ] `./scripts/validate.sh` (Android) + iOS CI-exact build + regenerate both
      galleries. Android screenshots may change — that's expected (the render is
      equivalent; verify the diff).

## Phases (ordered by leverage / risk)

Start small to prove the pattern, then widen.

### Phase 1 — `DoorWarning` (smallest end-to-end slice; proves the pattern) — ✅ SHIPPED

`DoorPosition → DoorWarning` (sealed: `ServerMessage(text)` + enum fallbacks
`OpeningTooLong` / `ClosingTooLong` / `OpenMisaligned` / `SensorConflict`). Moved
`androidApp/.../ui/home/DoorWarning.kt` + the `HomeMapper.warning` mapping into
shared `presentation-model` (`DoorWarning` + `DoorWarningMapper`, tests in
`commonTest`); `DefaultHomeViewModel` exposes `warning: StateFlow<DoorWarning?>`
(derived via `map` + `stateIn(Eagerly)`, seeded synchronously — no flicker, no
ctor change so both DI graphs are untouched). Android keeps the chip but sources
`HomeStatusDisplay.warning` from the VM (route wrapper `.copy(warning = …)`), not
the mapper. iOS gained a real warning chip (`DoorWarningChip` in
`HomeContentView`, resolved from the typed value in `HomeViewModelWrapper`).

**As-built note on the "thinner Android" goal:** the typed `DoorWarning` lives on
the existing `HomeStatusDisplay` bundle rather than as a standalone screen-level
Composable parameter. A nullable-with-default Composable param (`warning:
DoorWarning? = null`) is blocked by `ComposableNullableDefaultKonsistTest`
(the "fixture silently omits a production element" guard); the data-class field —
deliberately out of that check's scope — is the convention-clean home. The
mapping + type still moved to shared; only the render-time field location stayed.

### Phase 2 — Home status line ("Since · duration") — ✅ SHIPPED

The "Since 9:47 AM · 2 hr 14 min" line. Shared `presentation-model` now owns the
gnarly part — the elapsed-bucket *granularity* (`ElapsedDuration`:
Days / HoursMinutes / Minutes / Seconds, with leading-unit-only truncation) +
`SinceStatus` + `SinceStatusMapper.forEvent`; `DefaultHomeViewModel.sinceStatus:
StateFlow<SinceStatus?>` combines the door event with the live clock. Each UI
formats clock time + localized units with its own APIs (Compose
`DateTimeFormatter` + plurals; SwiftUI `DateFormatter`). **iOS gained the line**
(it had none — it now replaces the redundant raw door-message subtitle, matching
Android). Android's `HomeStatusFormatter.durationParts` was deleted; its tests
moved to shared `commonTest`.

**Door color/icon semantics — DEFERRED (not part of this slice).** The
fresh/stale door color was originally grouped here, but it's low-value to unify:
iOS already handles stale via its own per-position `DoorPalette`
(`GarageDoorView(position:isStale:)`), and the Android `DoorColorState` is a
coarser 3-bucket model. Sharing the 3-bucket enum would be Android-only-consumed,
not a real cross-cutting win. Revisit only if a future change needs the color
*bucket* shared.

### Phase 3 — History entries — ✅ SHIPPED

`DoorEvent → HistoryEntry` (Opened / Closed / Anomaly), `AnomalyKind`,
`TransitWarning`, and the day-grouping key (Today / Yesterday / date). Moved
`androidApp/.../ui/history/HistoryMapper.kt` + the entry types into shared
`presentation-model` (`HistoryMapper.toHistoryDays(events, nowEpochSeconds,
timeZoneId)` + `History.kt`); the 60-case test suite moved to `commonTest`. The
one platform-coupled step — epoch-seconds → local calendar day for grouping —
uses **`kotlinx-datetime`** (added to the module), taking the zone as an IANA id
string (`zone.id` on Android; `TimeZone.current.identifier` on iOS). iOS gained
the full rich screen: day sections, garage-door icons, durations, and
transit / anomaly / misaligned tags (it was a flat position + relative-time list
before).

**As-built notes:**

- **No VM / DI change.** The mapper is a pure function called from each UI
  (Android `DoorHistoryContent`'s `remember`; the iOS wrapper) — matching how
  Android already consumed `HistoryMapper`. Both DI graphs are untouched, so the
  two-DI-component trap doesn't apply to this slice.
- **`DayLabel.Date` carries primitive `year` / `monthNumber` / `dayOfMonth`** (not
  a date-library type), so the typed surface — and the Swift bridge — stay free
  of any `kotlinx-datetime` type. `kotlinx-datetime` is an `implementation` dep,
  so it never leaks into the framework's public API.
- **Duration *formatting* stays per-UI.** Only the gnarly merge / dedup /
  duration-span / grouping logic moved (the real drift risk). Android keeps
  `HistoryFormatter` (java.time formatting + the `%60` parts decomposition) and
  the Composable's granularity switch; iOS reimplements the same small arithmetic
  in Swift. Sharing the trivial decomposition wasn't worth churning the
  behavior-preserving Android render path. (Contrast Phase 2, which shared
  `ElapsedDuration` because the bucket *choice* was the non-trivial part.)
- **iOS load-more is deferred.** The iOS screen renders the current
  `recentDoorEvents` window; the scroll-to-end load-more trigger (Android has it;
  server + data support it per ADR-028) is a follow-up — it needs the pagination
  `StateFlow` + a SwiftUI scroll-position trigger, independent of the shared
  display mapping this slice delivered.

### Phase 4 — Home alerts + permission/notification state — ✅ SHIPPED

`HomeAlert` typed state (Stale / PermissionMissing / FetchError) + the
banner-selection logic moved to shared `presentation-model` (`HomeAlert` +
`HomeAlertMapper.toHomeAlerts`, tests in `commonTest`). iOS gained the full
alert-banner stack above the Status card (it had none) — stale, permission, and
fetch-error banners with action buttons, mirroring Android's `HomeAlertCard`.
The androidApp `HomeAlert` sealed type + `HomeMapper.toHomeAlerts` were deleted;
the now-pointless `NotificationJustification` wrapper (a single `Int`) was
inlined to `HomeAlert.PermissionMissing.attemptCount`.

**As-built notes:**

- **No VM / DI change.** Two of the four mapper inputs are platform-specific
  UI-layer state — `notificationPermissionGranted` (Android runtime permission /
  iOS `UNUserNotificationCenter`) and `notificationRequestCount` (Android
  `rememberSaveable` / iOS in-memory counter) — so the alerts can't be a pure VM
  `StateFlow`. The mapper is a pure function each UI calls with the VM's
  `currentDoorEvent` + `isCheckInStale` plus its own permission inputs (same
  pure-mapper-from-UI shape as Phase 3). Both DI graphs untouched.
- **Permission detection stays per-UI** (ADR-031 — the gnarly *decision* of
  which banners to show is shared; the platform permission API is not). iOS
  probes `UNUserNotificationCenter.getNotificationSettings`, requests via
  `requestAuthorization`, and defaults `granted = true` so the banner doesn't
  flash before the async probe resolves. The shared layer is driven by the
  resulting boolean.
- **Escalation copy is per-UI.** The shared `PermissionMissing` carries only
  `attemptCount`; each UI assembles its own localized multi-line justification
  (Android `stringResource` referencing Android Settings; iOS literals
  referencing the iOS Settings app). The 3+/4+/5+ escalation thresholds match.
- **iOS dropped the redundant in-card "Check-in is stale" label** now that the
  Stale banner + muted door color (`GarageDoorView(isStale:)`) signal staleness,
  matching Android (which never had an in-card stale label).

### Phase 5 — Remaining richness (paused 2026-06-28)

Run as a sequence of small PRs, each one a self-contained slice. **Status:** the
clean, snapshot-verifiable iOS-only content-parity slices are done (check-in pill,
button-health pill, info sheets #939, Functions warning + test-notification #941).
Paused here by the user — each remaining item either has a verification gap
(door-canvas animation) or needs shared-module work (Export CSV, copy-auth-token),
so they're picked up deliberately rather than auto-continued.

- **Check-in pill — ✅ SHIPPED.** `DoorEvent.lastCheckInTimeSeconds` + the live
  clock → typed `CheckInStatus` (`NoData` / `Reported(age, isStale)`) with a
  `CheckInAge` bucket (JustNow / Seconds / Minutes / Hours / Days), moved to
  shared `presentation-model` (`CheckInStatus` + `CheckInStatusMapper`, tests in
  `commonTest`). iOS **gained the pill** (it had none — only the door-color mute);
  it renders an antenna / antenna-slash capsule in the Status section header,
  mirroring Android's `DeviceCheckInPill`. As-built notes:
  - **Pure-mapper-from-UI, no VM / DI change** (Phase 3/4 shape). Both UIs already
    map at render from seeded StateFlows (`nowEpochSeconds` + the door event), so a
    VM `StateFlow` wasn't needed; the mapper is a pure `object` each UI calls. iOS
    gets the per-tick re-bucketing by observing the existing `vm.nowEpochSeconds`.
  - **Android churn was near-zero:** `DeviceCheckIn.format()` kept its signature
    and became a thin adapter over the shared mapper, so the pill, all previews,
    the route wrapper, and the Android reference PNGs are unchanged. Only the
    bucketing arithmetic + the duplicated 11-min threshold const moved out.
  - **String formatting stays per-UI** (ADR-031): the shared layer emits the typed
    bucket; Android assembles "1 min 30 sec ago" in `DeviceCheckIn`, iOS mirrors
    the same arithmetic in Swift. The staleness *threshold* (the shared decision)
    lives in `CheckInStatusMapper.STALE_THRESHOLD_SECONDS` (kept in sync by hand
    with `CheckInStalenessManager.CHECK_IN_STALE_THRESHOLD_SECONDS`).
- **Button-health pill (iOS render) — ✅ SHIPPED.** iOS already received the
  typed `ButtonHealthDisplay` (from `ComputeButtonHealthDisplayUseCase`) but
  flattened it to plain secondary `Text` in a separate "Device health" section.
  iOS now renders a styled pill in the "Remote button" section header — label +
  availability icon, only `Offline` tinted (warning) — mirroring Android's
  `RemoteButtonHealthPill` (labels "Available" / "Unavailable · {ago}" /
  "Checking…" / "Unauthorized" / "Unknown"); the redundant "Device health"
  section was removed (Android never had one). **iOS-only slice:**
  `ButtonHealthDisplay` was already shared and consumed, so no shared / Android /
  DI / VM change — the wrapper resolves the typed value to a plain `ButtonHealthItem`
  (same view-struct shape as `HomeAlertItem` / `DeviceCheckInItem`) and the pill
  reuses the check-in pill's antenna icon family for a consistent
  device-availability grammar.
- **Info sheets (iOS render) — ✅ SHIPPED.** Tapping the Status or Remote-button
  pill on Home now opens a `.sheet` explaining what the indicator means — the
  SwiftUI analog of Android's `DoorStatusInfoBottomSheet` /
  `RemoteControlInfoBottomSheet` (`InfoBottomSheet.kt`). **Content-parity slice,
  not a typed-state mapping:** the copy is static UI text (info icon + title +
  two paragraphs), so — like the rest of the iOS app's user-facing strings — it
  is sourced inline on iOS, matching Android's `strings.xml` copy verbatim
  (reviewed with the user). No shared / Android change; iOS-only. A pure
  `HomeInfoSheetContentView` (mirroring Android's `InfoSheetLayout`) gets its own
  `#Preview`s so both sheets land in the snapshot gallery; the pills became
  tappable via `.onTapGesture` driving a local `@State` `HomeInfoSheet?` (an
  explicit `init` on `HomeContentView` keeps that `private @State` from lowering
  the synthesized memberwise init to `private`, which would break the generated
  snapshot test).
- **Functions: developer warning + test-notification sandbox (iOS render) — ✅
  SHIPPED.** The iOS Functions screen gained Android's developer **warning**
  banner (verbatim `function_list_warning` copy) at the top of the granted list,
  plus the **test-notification sandbox** section — topic display + Copy test
  topic + Subscribe/Unsubscribe (state-driven label) + Change test topic — shown
  only once the personal topic exists (mirrors Android, which hides the rows
  until then). **iOS-only slice, not a typed-state mapping:** the shared
  `FunctionListViewModel` already exposed every action **and**
  `testNotificationState`, so the wrapper just published the topic/subscribed
  pair (the optional `TestNotificationTopic` value class bridges as `Any?`
  holding an NSString — `state.topic as? String`, the `User.name`/`.email`
  pattern) and forwarded the calls; no shared / Android / DI / VM change. As-built
  notes:
  - **Sign in / out intentionally skipped on iOS Functions** (platform-native,
    ADR-029). Settings owns the Account section + sign-out, and the Functions
    screen is reached only via Settings → Developer → Functions (gated on
    `functionListAccess == true`, which requires being signed in). So a sign-in
    button would be unreachable and a sign-out would pop you out of the screen
    you're on — duplicating them is an Android-flat-list artifact, not iOS-native.
  - **Test-notification section extracted to a pure `TestNotificationSectionView`
    subview** (mirrors the `HomeInfoSheetContentView` extraction) so the snapshot
    gallery captures the rows directly. In the full screen the section sits below
    Door / Refresh / FCM / Diagnostics, i.e. below the top-anchored snapshot fold
    — a full-screen "test notifications" preview was byte-identical to "Functions
    granted". Same fold issue as Android Layoutlib rendering a LazyColumn at
    scroll 0; the focused preview is the fix.
  - **Warning string is a `private static let`, not a `private let` instance
    property** — a private *stored instance* property would lower the struct's
    synthesized memberwise init to `private` and break the cross-file generated
    snapshot test (same trap as the #939 `private @State`; see CLAUDE.md § iOS
    snapshot gallery). A type property is never part of the memberwise init.
- **Developer-surface gaps (remaining)** — Diagnostics **Export CSV** (needs a
  shared CSV-content API + an iOS share sheet; that's a shared change, so
  two-DI-component territory) and **copy auth token** on both Diagnostics +
  Functions (Android does it via a UI-layer `rememberAuthTokenCopier` helper that
  fetches the token; iOS has no token-fetch bridge yet, and the Android
  API-33 `EXTRA_IS_SENSITIVE` redaction nuance needs an iOS-pasteboard
  equivalent decision). Each is its own scoped slice.
- **Door-canvas animation polish — deferred (verification gap).** iOS currently
  uses a simple 0.6 s ease between per-state offsets. The full Android contract
  (`AnimatableGarageDoor.kt` / `GarageIcon.kt` / `DoorAnimationMemory.kt`, see
  `docs/DOOR_ANIMATION.md`) is a sizable faithful port: a 12 s **linear tween**
  for OPENING/CLOSING, a no-bounce **spring** for terminal/error states, a
  `static` vs animated rendering split (so non-animated snapshots keep the
  mid-motion `staticPositionFor` offsets), and a **once-per-event "replay from
  the start"** gated by presentation-layer memory (`DoorAnimationMemory`, keyed
  on `(position, lastChangeTimeSeconds)`, held in a Compose-root `remember` —
  the iOS analog is an app-root SwiftUI `@State` exposed via `Environment`, NOT
  the DI graph — pure view-only memory belongs in the view tree, not DI).
  **Why deferred (not just
  "later"):** the animation's *core value* — the trajectory and the replay-once
  behavior — cannot be verified by any CLI/snapshot means on iOS. The snapshot
  gallery renders settled `static` frames only (Android itself needs an
  *instrumented* semantics-reading test, `GarageDoorAnimationBehaviorTest`, to
  audit the trajectory); `simctl` push is documented-unreliable; and the door
  can't be driven into OPENING/CLOSING on the simulator without signing in
  (and the remote button must never be tapped). A full port would therefore
  ship its main behavior unverified — the verification-gap the project treats
  as a design defect. Revisit when there's an iOS animation-audit harness (or
  accept watch-on-device verification deliberately). Static gallery is unchanged
  by the deferral; the per-state *static* renders already exist.

### Out of scope — adaptive / iPad layout

The adaptive iPad layout (`NavigationSplitView` vs the phone-style `TabView`) is
a separate iOS parity item, **not** a presentation-model slice — it's an
adaptive-layout concern, not shared display logic, so it doesn't follow the
per-slice recipe above. It's deprioritized (user, 2026-06-27) and tracked in
[`PENDING_FOLLOWUPS.md` § 1 "iOS app construction"](./PENDING_FOLLOWUPS.md)
alongside the other device/user-gated iOS work (sign-in tap-through, push
delivery, release tooling, App Store).

## iOS parity-gap inventory (the work this plan delivers)

Condensed from the 2026-06-27 audit. Items already shipped are struck through.

- **Settings** — ~~3-tab restructure + gated Developer section (#926)~~;
  ~~account name/email + About/version (#927)~~. Remaining: store/privacy links
  (deferred until published), tap-to-copy version, typed snooze-failure messages,
  snooze permission state.
- **Home** — ~~typed `DoorWarning` chip (P1)~~ ✅; ~~"Since · duration" line
  (P2)~~ ✅; ~~alert banners stale/fetch-error/permission (P4)~~ ✅; ~~check-in
  pill (P5)~~ ✅; ~~button-health pill render (P5)~~ ✅; ~~info sheets (P5)~~ ✅
  (#939); network-progress diagram (P5, optional).
- **History** — ~~day grouping, door icons, durations, transit/anomaly tags,
  empty-state (P3)~~ ✅; load-more (P3 follow-up — deferred, see Phase 3 note).
- **Diagnostics** — Export CSV, copy-auth-token (P5).
- **Functions** — ~~test-notification topic (copy/subscribe/change), "developers
  only" warning (P5)~~ ✅; copy-auth-token (P5); sign in/out **intentionally
  skipped** (Settings owns auth; the screen is `functionListAccess`-gated, so a
  sign-in button is unreachable and sign-out pops you out — platform-native).

## Risks / guards

- **High blast radius on Android.** Every slice edits `androidApp/` UI + deletes a
  mapper. `validate.sh` is mandatory (architecture lints, `:test-common` compile,
  unit-test compile, screenshot/preview coverage).
- **Two DI graphs.** A VM ctor change that compiles on Android can break iOS
  (`NativeComponent`) — iOS CI is not required, so grep `NativeComponent.kt` after
  any `viewmodel/` ctor change.
- **Screenshot churn.** Android reference PNGs may change when a screen switches
  from mapper-computed to VM-computed state; the render should be equivalent —
  inspect the diff, don't blindly accept. iOS gallery regenerates too.
- **Keep the typed-not-string rule.** If a slice is tempted to expose a formatted
  string from shared, stop — expose the typed/numeric input instead and format
  per-UI (the whole point of the string-resource migration).

## Done = 

Every screen's display *meaning* is computed once in `commonMain`, exposed by the
shared VM, and rendered by both UIs; `androidApp/` has no display mappers; the
iOS parity-gap inventory above is cleared.
