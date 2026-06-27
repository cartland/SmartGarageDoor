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

### Phase 4 — Home alerts + permission/notification state

`HomeAlert` typed state (Stale / FetchError / PermissionMissing) as shared typed
state where it's platform-neutral; the notification-permission piece stays
per-UI (iOS `UNUserNotificationCenter` vs Android runtime permission), driven by
a shared "permission needed" boolean. iOS gains the alert banners.

### Phase 5 — Remaining richness

Info sheets (door-status / remote-control), check-in + button-health pills as
typed display state, then the developer-surface gaps (Diagnostics Export CSV /
copy-token; Functions auth + test-notification actions), and door-canvas
animation polish.

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
  (P2)~~ ✅; alert banners stale/fetch-error/permission (P4); info sheets +
  check-in/health pills (P5); network-progress diagram (P5, optional).
- **History** — ~~day grouping, door icons, durations, transit/anomaly tags,
  empty-state (P3)~~ ✅; load-more (P3 follow-up — deferred, see Phase 3 note).
- **Diagnostics** — Export CSV, copy-auth-token (P5).
- **Functions** — sign in/out, copy-auth-token, test-notification topic
  (copy/subscribe/change), "developers only" warning (P5).

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
