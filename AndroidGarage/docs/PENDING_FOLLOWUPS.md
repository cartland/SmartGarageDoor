---
category: plan
status: active
last_verified: 2026-06-28
---

> **Last update 2026-05-13:** iOS Phase 38A fully complete — `NativeComponent` DI graph runtime-verified via `NativeComponentTest` (40/40 pass on `iosSimulatorArm64`, PR #826). Phase 38B–G remain blocked on user setup. User-action checklist added as the first subsection below so the user-blocked moves are visible without scrolling.

# Pending Follow-ups

User-flagged items that aren't tied to a specific release and aren't smoke-test verifications — feature follow-ups, copy revisions, design open questions. Each item should be discrete enough that a single PR could close it.

**Scope:** items that need their own design/implementation effort. Smoke-test verifications belong in [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md). Per-version implementation history belongs in [`../CHANGELOG.md`](../CHANGELOG.md). Architectural conventions belong in [`../../CLAUDE.md`](../../CLAUDE.md).

**Why this lives in the repo, not memory:** project-specific TODOs need to be reviewable in PRs and discoverable to anyone reading the repo cold. See `feedback_dump_context_repo_first.md` for the rule.

## User action items (2026-05-13)

Concrete things only the user can do. Each item points to the detailed section below or to a sibling doc. Ordered by leverage (A unblocks the most downstream work).

### A. iOS Phase A/C + door data — DONE (2026-06-24)

The one-time Apple Developer + Firebase iOS setup is complete, and the Swift Firebase wiring shipped:
- Firebase iOS app registered in `escape-echo` (bundle `com.chriscartland.garage`); `GoogleService-Info.plist` committed; Google Sign-In provider enabled; Apple Developer enrollment done; APNs `.p8` uploaded to Firebase Cloud Messaging.
- Real Firebase Auth + Google Sign-In + live door data, verified on the simulator (PRs #912/#913/#915).

**The remaining iOS work is the go-forward plan in § 1 "Remaining plan"** — sign-in verification, push *delivery* (device), Phase F release tooling, Phase G App Store, and the parity gaps (door canvas, adaptive layout). The only items still needing the user are **Apple code-signing** (TestFlight / App Store) and an interactive **sign-in tap-through**.

### B. Run the 3 pending smoke tests on a device

All three are on internal Play Store track now. Reference: [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md).

1. **2.16.28** — Nav rail "Set default" buttons, Snooze sheet no-preselected radio, Diagnostics button styling.
2. **2.16.29** — Uniform 16 dp inter-section spacing across Home / Settings / History / Diagnostics / Function list.
3. **2.16.30 + 2.16.31** — Snooze / Version / Account sheet rhythm, DoorHistory error banners, History empty state centered, Nav rail derived default still works.

Walk through each on a phone or tablet. Report any failures and the agent files issues; queue clears.

### C. (Optional, no rush) kotlin-inject 0.9.0 decision

Currently pinned at 0.8.0 deliberately. Tripwire conditions in § 2 below — none have fired. Revisit when SKIE or another dep forces Kotlin 2.2+, or when bumping for an unrelated reason lands it for free.

---

**Default suggestion:** start with **A** — it's the only one that unblocks new code. If the setup-time feels heavy and smoke-testing first is preferable, that's also fine; the iOS gates don't expire.

## Open

### 1. iOS app construction (post-`:iosFramework`)

**Status (2026-06-24):** The iOS app is **functional on the simulator with real backend data**. Phases B–F (Xcode project, SwiftUI screens, iOS CI) shipped 2026-06-01; Phase A (Apple Developer + Firebase iOS) is done; Phase C (real Firebase Auth/Messaging bridges + Google Sign-In) shipped this session, plus garage backend config (live door data) and the FCM-receive path. **Verified on the simulator:** Firebase init, real signed-out auth state, notification-permission prompt, and **live door STATUS from the production server**. **Remaining is mostly user/device-gated:** interactive sign-in verification, push *delivery* (needs a signed device build), release tooling (Phase F), App Store (Phase G), plus two feature-parity gaps (door-canvas animation, iPad/adaptive layout). The go-forward plan is in **"Remaining plan"** below.

**What's already done** (5 merged PRs):

| PR | Scope |
|---|---|
| [#820](https://github.com/cartland/SmartGarageDoor/pull/820) | Delete dormant `:shared` scaffold, add `:iosFramework` Gradle module |
| [#821](https://github.com/cartland/SmartGarageDoor/pull/821) | iOS targets on `:domain` + `:data`; Ktor Darwin engine; `kotlin.jvm.JvmInline` + `kotlinx.coroutines.IO` portability fixes |
| ~~[#822](https://github.com/cartland/SmartGarageDoor/pull/822)~~ | (Closed — content bundled into #823's squash-merge) |
| [#823](https://github.com/cartland/SmartGarageDoor/pull/823) | `:data-local` Room KMP + DataStore-Okio; DAO suspend refactor; kotlinx-datetime for `currentTimeMillis`; `DatabaseFactory` expect/actual; iOS targets on `:usecase`/`:viewmodel`/`:presentation-model`/`:test-common`; `AppStartup` moved to `:usecase/commonMain` |
| [#824](https://github.com/cartland/SmartGarageDoor/pull/824) | SKIE 0.10.9 plugin; `NativeComponent` DI graph (mirrors `AppComponent` with iOS deps); `IosNativeHelper`; `NoOpAuthBridge` + `NoOpMessagingBridge` placeholders; kotlin-inject 0.8.0 downgrade (see follow-up #2 below) |

**Shipped (Phases A–F + functional backend):**
- **B/D/E/F** (PRs #856/#857/#858, 2026-06-01) — `AndroidGarage/iosApp/` foundation (XcodeGen `project.yml`, `SharedViewModel`, theme tokens, 5-tab shell, `KmpViewModelStore`), `.github/workflows/ios-ci.yml` (`macos-latest`), and all 5 screens (Home / History / Profile / Functions / Diagnostics) wired to the real `Default*ViewModel`s via `*ViewModelWrapper`s.
- **A** (2026-06-23) — Firebase iOS app registered in `escape-echo`, `GoogleService-Info.plist` committed; Apple Developer enrollment + APNs `.p8` uploaded to Firebase Cloud Messaging.
- **C** (PR #912, 2026-06-24) — real `FirebaseAuthBridge` / `FirebaseMessagingBridge` (`Core/Firebase/`), `AppDelegate` (`FirebaseApp.configure()` + `NativeComponent` build + `AppStartup.run()` + push registration), Google Sign-In (`GoogleSignInCoordinator` + Profile button). SKIE bridge-conformance contract pinned (see `FirebaseAuthBridge.swift` / `IosAuthUserStateHolder.kt` KDoc): suspend methods → `__`-prefixed `async throws`; `observeAuthUser()` → `SkieSwiftOptionalFlow<DataAuthUserInfo>`.
- **Door data** (PR #913) — `GARAGE_BASE_URL` committed; secret `GARAGE_SERVER_CONFIG_KEY` via gitignored `Secrets.local.xcconfig` (mirrors Android's `local.properties`). **Verified on simulator: real STATUS "Closed".**
- **FCM receive** (PR #915) — `IosNativeHelper.parseFcmDoorEvent` (shared `FcmPayloadParser`) + `AppDelegate.didReceiveRemoteNotification` → `ReceiveFcmDoorEventUseCase`. Compiles; not runtime-verified (`simctl` silent push unreliable — device/Phase-G check).

**SKIE binding notes** (for future screens): sealed Kotlin types use `onEnum(of:)`; `:usecase` types are module-prefixed in Swift (e.g. `UsecaseButtonHealthDisplay`); `StateFlow<Long>` → `.value.int64Value`. Local verification workflow + the iosFramework-spotless / non-required-iOS-CI traps: see [`../../CLAUDE.md`](../../CLAUDE.md) § "iOS local verification".

**Snapshot gallery (ADR-030).** iOS now has a browsable visual reference of every SwiftUI `#Preview`, captured to committed PNGs via Prefire + swift-snapshot-testing (regenerate, don't assert — the Android-style posture). Regenerate with `./scripts/generate-ios-screenshots.sh`; browse [`../iosApp/SnapshotTests/SCREENSHOT_GALLERY.md`](../iosApp/SnapshotTests/SCREENSHOT_GALLERY.md). **When adding/refactoring an iOS view, add a `#Preview` so it lands in the gallery.** Coverage now includes the `GarageDoorView` component + states and **all 5 screens** (Home / History / Profile / Functions / Diagnostics), each split into a pure `*ContentView` (plain values + action closures) so it renders without a live `NativeComponent`. Time-dependent previews inject a fixed `PreviewFixtures.now` for determinism (canonical: `HistoryContentView`).

#### Remaining plan

Ordered by leverage. Phases 1–2 are verification of already-built code; Phases 3–4 are the path to a published app; the parity items are tracked under [ADR-029](./DECISIONS.md#adr-029-ios--android--feature-parity-platform-native-design-one-shared-identity).

1. **Sign-in end-to-end verification** (small; simulator). The Google Sign-In code path is wired but unverified — completing the OAuth flow needs an interactive tap-through (Profile → "Sign in with Google"). Confirms `GoogleSignInCoordinator` → `signInWithGoogle` → `AuthBridge` → signed-in auth state + that the snooze / device-health rows light up. **Safe to drive** (never touch the Home remote button while signed in — see CLAUDE.md § "iOS simulator testing").
2. **Push delivery verification** (device-only; depends on Phase 3 signing). Real FCM → APNs → device requires a signed build with the `aps-environment` entitlement. Verifies the FCM-receive path (#915) end-to-end and the subscribe/token side of `FirebaseMessagingBridge`. Cannot be done on the simulator; folds into the first TestFlight build.
3. **Phase F — release tooling + first TestFlight** (1–2 PRs):
   - `scripts/release-ios.sh` mirroring `release-android.sh`: bump `CFBundleShortVersionString` + `CFBundleVersion`, tag `ios/N`, `xcodebuild archive` + upload to TestFlight.
   - `scripts/validate-ios.sh`: build framework + `:iosFramework:iosSimulatorArm64Test` + `xcodebuild build` (+ any SwiftUI unit tests).
   - `AndroidGarage/iosApp/CHANGELOG.md` with the `## N.M.K` heading gate (mirrors the Android changelog gate).
   - Signing: register the App ID + Push Notifications capability + provisioning; first TestFlight Internal Testing upload. **User-gated** (Apple signing identity).
   - Consider promoting iOS CI (`Build iOS app + framework test`) to a required check once stable (per CLAUDE.md branch-protection ordering) — closes the non-required-iOS-CI / auto-merge race.
4. **Phase G — App Store** (1 PR, last): 1024² icon (reuse Android glyph), App Store Connect screenshots (iPhone + iPad), listing copy, `ITSAppUsesNonExemptEncryption = NO` (already set), `PrivacyInfo.xcprivacy` (Firebase Auth = "User ID, linked, App Functionality"), submit for review. **User-gated.**

**Feature-parity audit (ADR-029).** Capability parity is the north star. The full iOS↔Android audit ran 2026-06-27 → the gap inventory + the execution plan now live in **[ADR-031](./DECISIONS.md#adr-031) + [`PRESENTATION_MODEL_REALIZATION.md`](./PRESENTATION_MODEL_REALIZATION.md)** (realize the shared `presentation-model` layer so typed display state is computed once in `commonMain` and rendered by both Compose and SwiftUI). Status:
- **Animated door canvas — DONE (#919).** SwiftUI `GarageDoorView` (full Android trajectory deferred; identity visual landed).
- **Snapshot gallery — DONE (#920–#924).** All 5 screens captured (ADR-030).
- **3-tab restructure + gated Developer section — DONE (#926).** Diagnostics + Functions moved under Settings, `developerAccess`-gated (no longer leaked to all users).
- **Account identity + About/version — DONE (#927).**
- **Remaining richness (Home warnings/duration/alerts, History day-grouping/rich-rows/load-more, info sheets, dev-action gaps)** is the `presentation-model` realization — phased in the plan doc above. This is the active iOS workstream.
- **Adaptive / iPad layout — DEPRIORITIZED (user, 2026-06-27).** `NavigationSplitView` adaptation is low-value right now; revisit after the richness parity lands.

**Sequencing:** Phase 1 (verify sign-in) now → parity items (door canvas, adaptive layout) can proceed in parallel any time → Phase 3 (release tooling + signing) → Phase 2 (device push verify, folds into TestFlight) → Phase 4 (App Store). Phases 3–4 and Phase 2 are user-gated on Apple signing.

**Decisions already locked** (from the iOS migration plan):
- Bundle ID: `com.chriscartland.garage`
- Universal (iPhone + iPad)
- iOS 16 minimum
- Google Sign-In only (no Apple Sign-In v1 — may add if App Store review demands)
- Framework name: `shared`, module name: `:iosFramework`
- DI: kotlin-inject `NativeComponent`
- Bridge tech: SKIE
- Wrapper: `SharedViewModel<VM>` (battery-butler pattern)
- Tabs: Home / History / Profile / Functions / Diagnostics (mirror Android)
- Theme: system light/dark
- Localization: English only at launch
- APNs auth: `.p8` key
- Versioning: independent, `ios/N` tags

### 2. kotlin-inject 0.8.0 → 0.9.0+ via Kotlin 2.2+ bump (deferred)

**Status:** Deferred. PR #824 downgraded kotlin-inject from 0.9.0 → 0.8.0 because 0.9.0's KLIBs are built with Kotlin 2.2.20 and this project is on Kotlin 2.1.20 (the Kotlin/Native KLIB resolver rejects the version mismatch). 0.8.0 is the youngest pre-2.2 release on Maven Central and works fine for both Android (`AppComponent`) and iOS (`NativeComponent`).

**Why deferred:** the downgrade is not blocking iOS work. 0.8.0 still receives maintenance fixes; the 0.9.0 features we'd gain (improved KSP error messages, minor codegen tweaks) are nice-to-haves, not requirements.

**Tripwire — revisit when ANY of these fire:**
- A specific Kotlin 2.2+ language or KMP feature blocks something concrete (not "would be nicer")
- SKIE releases a version requiring Kotlin 2.2+
- kotlin-inject 0.8.x stops getting maintenance fixes (watch the GitHub releases page)
- battery-butler diverges enough in its reference patterns that we lose the cross-reference value (battery-butler is currently on Kotlin 2.3.0 + kotlin-inject 0.9.0)
- Bumping for an unrelated reason (e.g., AGP requires Kotlin 2.2+) lands this for free

**Scope when done** (multi-PR per `docs/DEPENDENCY_UPGRADES.md`):
1. Bump Kotlin to 2.2.20 or 2.3.0 in `libs.versions.toml`
2. Bump KSP to the matching `<kotlin>-<ksp>` version
3. Bump Compose Compiler plugin (tied to Kotlin)
4. Bump Detekt to ≥1.24.0 (Kotlin 2.2 source-compat)
5. Bump kotlin-inject `0.8.0 → 0.9.0+` once Kotlin is on 2.2+
6. Verify Room 2.7.2 KSP still works (or bump to next Room point release)
7. Touch `buildSrc/` Gradle tasks if Kotlin API breaks (the 7 custom architecture-check tasks use compiler reflection)
8. Run `validate.sh` end-to-end
9. Test both Android (debug + release) and iOS framework link

**Why this needs care:** Kotlin major-version bumps are a runtime-level change that touches every module. The project has a documented sequencing playbook at [`docs/DEPENDENCY_UPGRADES.md`](../../docs/DEPENDENCY_UPGRADES.md) for exactly this kind of cascade — use it.

### 3. Security audit 2026-05-14 — deferred findings

**Status:** The top 10 items (1× Critical + 8 High + 1 follow-on) shipped 2026-05-15 as PRs #827–#834 (plus docs PR #835). The Medium / Low / informational findings below were intentionally deferred at the time per user instruction. Each line names the audit finding ID, the file/area, and a one-line description so a future PR can pick one up without re-reading the original audit report.

**Medium findings deferred:**

- **M1** — No `runWith({maxInstances, timeoutSeconds, memory})` on **pubsub** functions. HTTP functions were capped in PR #834; the pubsub side (`functions.pubsub.schedule(...)`) is externally unreachable so this is a maintenance concern, not security. Adds a billing-safety floor if a scheduled job ever runs hot.
- **M2** — `httpServerConfig` / `httpServerConfigUpdate` use deprecated `functions.config()` API for the static `X-ServerConfigKey` gate. Should migrate to `firebase secrets` and add Firebase ID-token gate ABOVE the static-key check (so even the key holder must be authenticated).
- **M3** — "4 documented allowlists" is actually 3 in code (`snooze`, `remoteButton`, `buttonHealth` all share `remoteButtonAuthorizedEmails`; `functionList` and `developerAccess` have their own). Either accept and update the CLAUDE.md wording to "3 allowlists", or split snooze out into its own list. No security impact today.
- **M4** — `r0adkll/upload-google-play@v1.1.3` in `release-android.yml` is pinned to a moving tag. Should pin to a 40-char commit SHA so the upstream maintainer can't swap behavior under the GitHub Actions cache.
- **M5** — No required PR reviewers in branch protection (acceptable for solo, worth noting).
- **M6** — `android:allowBackup="true"` with empty include/exclude rules — Room DB + DataStore back up to Google Drive by default. Populate `backup_rules.xml` to exclude `databases/database` and DataStore preferences, OR set `allowBackup="false"`.
- **M7** — `eventHistoryMaxCount=NaN` / huge numbers and `cutoffTimestampSeconds=NaN` are not validated. Add `Math.min(parseInt(...), 100)` + `Number.isFinite(...)` checks.
- **M8** — ESP32 WPA2-PSK without PMF required (KRACK / deauth surface). Set `pmf_cfg.required = true; .capable = true` in `wifi_connector.c`.
- **M9** — ESP32 test fakes are flag-gated by hand-edited `garage_config.h:5-11`, not a Kconfig switch. Easy to ship a build that opens the door every other poll. Move to Kconfig + `#error` guard on release optimization.
- **M10** — `Arduino_ESP32/*/secrets.h` are tracked (placeholder values), not gitignored. Foot-gun for future commits. `git rm --cached` + rename to `secrets.template.h`.
- **M11** — ESP32: no flash encryption, no secure boot v2, no NVS encryption. Anyone with physical access can dump flash and read WiFi creds. Enable `CONFIG_SECURE_FLASH_ENC_ENABLED=y` + secure boot v2 (the bigger ESP32 hardening project).
- **M12** — Firmware-pinned root CA expires 2036-06-22 with no OTA path. Needs signed OTA implemented before 2036, OR bake in fresher GTS Root R3/R4.
- **M13** — `set -e` missing from `decrypt-secrets.sh` / `clean-secrets.sh` / `encrypt-secrets.sh`. Add `set -euo pipefail` to all three so a failing `gpg` doesn't silently leave plaintext on disk.
- **M14** — No `.github/dependabot.yml`. CI runs `npm audit` warn-only but no automated upgrade flow. Add npm + gradle + github-actions ecosystems.
- **M15** — ESP-IDF version not pinned (`idf_component.yml` missing). Recommend `idf: ">=5.3.2,<5.4"` and document upgrade cadence.
- **M16** — Compose BOM ~6 months behind (2025.06.01 vs 2026.01+). No known CVEs at the pinned version; lag is the largest deferred Android upgrade.

**Low / informational findings deferred:**

- 7 of 9 GitHub Actions pinned to floating major tags (not SHAs). First-party = lower risk; third-party (`r0adkll/upload-google-play`) covered by M4.
- `network_security_config.xml` allows cleartext to `localhost`/`127.0.0.1` in main variant (should be debug-only).
- ProGuard `-keep class com.chriscartland.garage.data.ktor.** { *; }` is broader than needed per ADR-020.
- `cJSON_Print(root)` heap leak inside `ESP_LOGI` — slow OOM over months of uptime.
- No watchdog explicitly armed on ESP32 tasks.
- Server log retention = default 30 days (Cloud Logging). Consider 7-day pin now that token logging is fixed.
- No `/deleteMyData` endpoint (fine for single-user; GDPR-relevant if ever multi-tenant).

**Picking one to ship:** M6 (allowBackup) and M13 (`set -e` in release scripts) are both single-file, single-PR fixes. M14 (dependabot.yml) is a small additive config that unblocks future Dependabot PRs. The other Mediums are larger or require coordination (M11 = flash encryption rollout; M2 = secrets migration + Android client change).

### 4. iOS ↔ Android parity-audit findings (2026-06-28)

A 13-agent screen-by-screen parity audit (6 auditors + adversarial verifiers + synthesis, all findings file:line-verified) classified every screen against the [ADR-032](./DECISIONS.md#adr-032) fidelity tiers ([`UI_FIDELITY_TIERS.md`](./UI_FIDELITY_TIERS.md)). Overall verdict: **parity is fundamentally healthy** — the meaning-bearing layers are genuinely shared (History pipeline, snooze typed-failures, info-sheet copy, test-notification sandbox, access tri-states, door semantics). The discrete gaps below are each a self-contained PR. **Already fixed in the same batch:** the one HIGH finding — iOS Home showed an inert remote button when signed out with no Home sign-in path — was resolved by auth-gating iOS Home (consume the shared `AuthState`; signed-out → sign-in CTA + button hidden; redundant Account row dropped).

**Tier 1 — identity (highest leverage):**
- **Door constants shared-hoist (systemic).** The door geometry/palette/offsets/mappings were **hand-duplicated** Kotlin↔Swift with no guard — they matched by coincidence, so an edit on one side silently drifted the brand on the other. Hoist to shared `:domain` constants (the `AppLinks` pattern), consumed by both. Done in slices:
  - ✅ **Geometry — SHIPPED.** Viewport / frame / panel / handle + the derived `CLIP_INSET` now live in `:domain` `GarageDoorGeometry`, read by both `GarageDoorCanvas.kt` and `GarageDoorCanvas.swift`; pinned by `GarageDoorGeometryTest` (commonTest). Verified pure no-op (byte-identical iOS snapshots; the `clipInset` fragility — derived-vs-hardcoded — is gone, both are now the single derived `22`).
  - ✅ **Color palette — SHIPPED.** The 12 light/dark door-fill hex values now live in `:domain` `GarageDoorPalette` (opaque ARGB Longs), read by Android `Color.kt` (`Color(Long)`) and iOS `DoorPalette` (masked to 24-bit → `Color(rgb:)`); pinned by `GarageDoorPaletteTest` (commonTest: opacity, gray-has-no-stale, colored-states-do). The Android-only `on*` text variants stay local (iOS draws overlays with the system label color), so they were correctly left out. Verified pure no-op (all 12 values already matched; byte-identical iOS snapshots).
  - ✅ **Offsets + offset/overlay mappings — SHIPPED.** The 5 offset constants + the full pure `DoorAnimation` spec object (`targetPositionFor`/`fromPositionFor`/`useSpringFor`/`staticPositionFor`/`overlayFor`) + the `DoorOverlayKind` enum now live in `:domain` `DoorAnimation`; pinned by `DoorAnimationTest` (commonTest, moved from the Android unit test). Android `GarageIcon` + iOS `GarageDoorView` both read it (iOS consumes `staticPositionFor` + `overlayFor`; Android also drives its live trajectory from target/from/spring). Verified pure no-op (mappings already identical; byte-identical iOS snapshots).
  - ✅ **colorState mapping — SHIPPED.** `DoorPosition → DoorColorState` (which palette family a state uses) now lives in `:domain` `DoorAnimation.colorStateFor` + the shared `DoorColorState` enum; pinned by `DoorAnimationTest`. Android `doorColorState()` delegates to it (a theme-package `typealias DoorColorState` keeps the 3 UI consumers — `DoorStatusCard` / `HomeContent` / `HistoryContent` — zero-churn; the null-event `else → OPEN` fallback preserved); iOS `DoorPalette.doorRGB` calls the shared mapping (its local enum + `DoorVisual` removed). Verified pure no-op (mapping already identical; byte-identical iOS snapshots). **The door visualization is now fully shared `:domain`** — geometry + palette + the complete visual/animation spec.
  - ✅ **Live-trajectory convergence (b) — SHIPPED.** The maintainer confirmed the 12 s pacing is **brand-essential** (real door travel + ~2 s network slack so the terminal event lands mid-slide and the icon springs to target), i.e. Tier 1 → iOS must match. Shipped in two PRs: the duration + replay policy were hoisted to `:domain` (`DoorAnimation.ANIMATION_DURATION_SECONDS` + `DoorMotionKey`/`DoorAnimationMemory`, pinned by `DoorAnimationTest`/`DoorAnimationMemoryTest`, #959, Android no-op), then iOS's `GarageDoorView` gained an animated mode (#960) — a SwiftUI `@State` offset driven by `withAnimation`: 12 s linear slide for OPENING/CLOSING, slow no-overshoot spring settle on the terminal event, replay-once-per-event via the shared memory (held app-root in `MainScreen`, injected via a `\.doorAnimationMemory` environment value). Per the ADR-032 spec-vs-execution split, the *parameters* are shared/provable; the SwiftUI interpolation is best-effort native execution. Verified: CI-exact iOS build clean + byte-identical static snapshots (all Home preview states are non-motion, so animated mode's at-rest frame equals the static frame). **The door visualization — static and dynamic — is now fully shared `:domain`.**
- **Device-availability pill icon family diverges.** Android `Sensors`/`SensorsOff` vs iOS SF `antenna.radiowaves…(.slash)`. Pick one visually-equivalent family; record the cross-platform icon-name pairing.

**Tier 2 — convergent gaps (mostly iOS-side):**
- ✅ **Copy auth token — SHIPPED.** iOS Functions panel gained it (#966: shared `GetAuthTokenForCopyUseCase` + `UIPasteboard` write with a 2-min expiration as the sensitivity posture + a `CopyAuthTokenButton` flash). Then both platforms were routed through their ViewModels (#968, ADR-033): the UI calls `vm.fetchAuthTokenForCopy()`, never the usecase directly. (iOS Diagnostics copy-token — Android has it there too — is the only remaining sliver; reuses the same `AuthTokenCopier` helper.)
- ✅ **Export CSV — SHIPPED.** iOS Diagnostics gained it (#965: shared `AppLogCsv` builder + `UIActivityViewController` share sheet). Then routed through `DiagnosticsViewModel.buildExportCsv()` via a new shared `BuildAppLogCsvUseCase` (#969, ADR-033) — the UI no longer touches the repo. Android writes to a content `Uri`; iOS shares a temp `.csv`; the CSV bytes are identical (shared `AppLogCsv`).
- **History stale banner + reset-FCM recovery** absent on iOS. Route through a shared `HistoryAlert` mapper (mirroring `HomeAlertMapper`) so the show/hide decision can't diverge.
- ✅ **History load-more pagination — SHIPPED.** iOS `HistoryViewModelWrapper` now consumes the shared `paginationState` (exposing `canLoadMore` / `isLoadingMore`) + calls `fetchOlderDoorEvents()` via `loadMore()`; `HistoryContentView` renders a bottom `HistoryLoadMoreFooter` (spinner while loading, "You've reached the beginning of your history" terminal note) whose `onAppear` is the scroll-to-end trigger. Mirrors Android's `HistoryFooter` + near-end `LaunchedEffect`. Cold-start fetch (`appStartup.run()` → `InitialDoorFetchManager`) populates the cursor, so the footer is live.
- **About build-timestamp copy row** absent on iOS (Android exposes 4 copyable values; iOS has 3). Source from a shared `AppVersion` value object / the bundle.
- **`ProfileScreenState` dead slice.** The shared `presentation-model` slice exists but is consumed nowhere; both platforms hand-roll the account+snooze projection. Realize it as the shared slice both consume, or delete it as dead code.
- **Diagnostics counter row order drift.** Android renders FCM-subscribe before FCM-received; iOS (and the shared VM field order) is the reverse. Add a shared ordered-counter display model (typed ids, labels per-UI) and align Android.

**Tier 2/3 — low-severity polish:**
- iOS warning tint is a literal `Color.red` placeholder (`GarageColors.statusWarning`) vs Android's M3 error-container role — map to the iOS theme equivalent.
- Label drift: Home section header "Remote button" (iOS) vs "Remote control" (Android, and iOS's *own* info-sheet title); Functions chrome title "Function list" (Android) vs "Functions" (iOS). Pick one each (strings stay per-UI per ADR-031; meaning must align).
- iOS Diagnostics clear-all confirm drops the scope + irreversibility clauses Android states.
- Dead Android `ErrorCard` in `DoorHistoryContent` (the VM always writes `Complete(prev)`, never `Error`) — delete or wire.

**Doc corrections from the audit (shipped with this entry):** `UI_FIDELITY_TIERS.md` had 4 stale rows — tab-set enumerated 5 tabs (real app ships 3: Home/History/Settings), the pill note wrongly said the icon "mirrors Android," the theme row claimed iOS "mirrors" the Android scheme (it's a 4-color stub on system accent), and History had no banner row. All corrected.

<!-- Historical reference: original Phase 1/2/3 migration plan now in Done. -->

<details>
<summary>Archived: original migration plan (closed 2026-05-11)</summary>

### 1. Migrate user-visible strings to Android string resources [DONE]

**Status:** COMPLETE 2026-05-11 in 12 PRs (#784–#795). See the Done section below for the summary entry.

**Aspiration (2026-05-11 user direction):** "I want this app to be a good example so I want this to be done well across the whole app." — the bar is exemplary, not just functional. When this plan is complete, the only `String` literals in production code paths are (a) server-returned text carried verbatim, (b) developer-only / log strings, (c) animation / Compose-tooling debug metadata. Every user-facing label is a resource ID.

**Why migrate:** (a) unblocks future localization (`values-<lang>/strings.xml` becomes a drop-in), (b) centralizes copy for style sweeps (em-dash, sentence case), (c) decouples copy revisions from code review, (d) lets tests assert on typed state instead of fragile text content.

#### Architectural decisions

##### A. Non-Composable contexts: typed-hint refactor (NOT `StringProvider`)

Mappers, ViewModels, and UseCases MUST NOT return user-visible strings. They emit **typed states** (sealed types, enums, primitive args). Composables convert types to localized strings at render time.

This is the pattern the codebase **already uses** for `SnoozeRowState`, `HomeAlert`, `AccountRowState`, `AppLayoutMode`, etc. The migration extends the same pattern to cover the remaining string-emitting cases.

**Concrete refactor examples:**

| Today | After |
|---|---|
| `HomeMapper.stateLabel(DoorPosition): String` returns `"Open"` / `"Closed"` / etc. | DELETED. Composable maps `DoorPosition` enum → `stringResource(R.string.door_state_*)` directly via a `when` block in the UI layer. |
| `HomeMapper.warning(event): String?` returns `"Opening, taking longer than expected"` etc. | Returns `DoorWarning?` sealed type (server-message variant carries the verbatim server string; fallback variants are enum cases). Composable resolves enum to resource. |
| `HomeMapper.formatDuration(seconds): String` returns `"5 hr 30 min"` | DELETED. Composable does `formatDurationDisplay(seconds)` with `pluralStringResource` for count-based parts. |
| `NotificationPermissionCopy.justificationText(int): String` builds the multi-line message | Returns `NotificationJustification(attemptCount: Int)` data class. Composable assembles via `stringResource` + `pluralStringResource`. |
| `HomeAlert.Stale(message = "Not receiving updates from server")` (default arg) | `data object Stale : HomeAlert` (no `message` field). Composable maps `Stale` → `R.string.home_alert_stale_message`. |

**Why typed-hint, not `StringProvider`:**

- (a) Aligns with existing codebase pattern — `SnoozeRowState`, `HomeAlert`, `AccountRowState` are already typed-hint. Don't introduce a parallel abstraction.
- (b) Mappers stay pure-function unit-testable without `Context` / `Resources`.
- (c) KMP-portable — mapper modules have no Android dependency.
- (d) `StringProvider` would add a DI dep to ~30 sites. Typed-hint adds ~5 sealed types in `domain/` or `usecase/`.
- (e) Tests can assert on typed shape (`assertEquals(DoorWarning.OpeningTooLong, mapper.warning(event))`) rather than text — so a copy revision doesn't break the test.

##### B. Naming convention

`<screen>_<section>_<purpose>[_<state>]`. Snake_case (Android convention). Examples already in `strings.xml`:

```
settings_account_sign_in
settings_notifications_subtitle_loading
settings_notifications_subtitle_snoozing_until    (with %1$s arg)
settings_about_version_subtitle                    (with %1$s, %2$s args)
```

For door states, alerts, warnings (Phase 2):
```
home_door_state_open
home_door_state_closed
home_warning_opening_too_long
home_alert_stale_message
home_alert_action_retry
home_duration_days                (plural: one/other)
```

##### C. What gets migrated

| Class | Migrate? | Notes |
|---|---|---|
| `Text("literal")` in Composable body | ✅ Yes | Direct `stringResource` swap |
| Mapper / ViewModel returning user-visible string | 🔁 Refactor | Emit typed state, Composable renders |
| Server-returned text (`event.message`) | ❌ No | Arbitrary data, not a label |
| `Logger.*` messages | ❌ No | Internal only |
| `AppAnimatedVisibility(label = "...")` | ❌ No | Compose-tooling debug metadata |
| `@Preview` fake data (display names, sample times, version strings) | ❌ No | Not user-visible at runtime |
| Test fixture strings | ❌ No | Internal to tests |
| `.takeIf { it.isNotBlank() } ?: "(unknown)"` style fallback | ✅ Yes | User sees this when name is blank |

##### D. Plurals + format args

- Count-based strings → `<plurals>` + `pluralStringResource(R.plurals.X, count, count)`. Examples: history-event count, duration "N days" / "N min".
- Interpolated strings → `formatArgs` (`%1$s`, `%2$d`). Already in use for snooze-until-time and version subtitle in `strings.xml`.

##### E. Lint enforcement (ratchet)

After ~80% of the migration is complete, add `checkNoLiteralStringsInCompose` to `validate.sh`:

- Scans `androidApp/src/main/.../**/*.kt` for `Text("...")`, `Text(text = "...")` and other text-rendering Composables with literal first arg.
- Allows: `Text("")`, `Text("\n")`, `Text(stringResource(...))`, `Text(arg)` where `arg` is a parameter or property.
- Exemption file (`androidApp/string-literal-exemptions.txt`) lists violations remaining at lint-introduction time. New violations are blocked.
- Exemption-file shape mirrors `screen-viewmodel-exemptions.txt` from ADR-026.

**Don't add the lint early** — it would gate the migration PRs themselves.

Optional follow-up lint: scan `strings.xml` for em dashes (`—`) and warn (CLAUDE.md style rule centralization).

#### Phased file checklist

##### Phase 1 — Composable surfaces (low risk, mechanical)

These files have user-visible literal strings inside Composable bodies. Direct `stringResource` swap; no architectural change. One PR per file unless two are tightly coupled.

| File | Approx strings | PR |
|---|---|---|
| `androidApp/.../ui/settings/SettingsContent.kt` | 16 | ✅ #784 |
| `androidApp/.../ui/home/HomeContent.kt` | ~12 (section headers, alert action labels, "Allow", "Retry") | TODO |
| `androidApp/.../ui/home/DoorHistoryContent.kt` | ~6 | TODO |
| `androidApp/.../ui/FunctionListContent.kt` | ~8 (function-list warning + action labels) | TODO |
| `androidApp/.../ui/settings/DiagnosticsContent.kt` | ~10 (counter labels, button labels, dialog text) | TODO |
| `androidApp/.../ui/settings/SnoozeBottomSheet.kt` | ~6 (duration option labels) | TODO |
| `androidApp/.../ui/settings/AccountBottomSheet.kt` | ~3 (sign out label + body) | TODO |
| `androidApp/.../ui/settings/VersionBottomSheet.kt` | ~5 (row labels, copy labels) | TODO |
| `androidApp/.../ui/home/DoorStatusInfoBottomSheet.kt` | ~10 (title + 5 paragraph bodies) | TODO |
| `androidApp/.../ui/home/RemoteControlInfoBottomSheet.kt` | ~6 | TODO |
| `androidApp/.../ui/ProfileContent.kt` | ~2 (`"(unknown)"` fallback, version-sheet copy labels) | TODO |
| `androidApp/.../ui/auth/AuthTokenCopier.kt` | ~2 (Toast strings) | TODO |

##### Phase 2 — Typed-hint refactor (mappers / non-Composable copy objects)

Each is its own PR because the API change ripples across module boundaries (mapper test assertions change from text to type).

| Refactor | Strings | PR |
|---|---|---|
| `HomeMapper`: `stateLabel` deleted (Composable does `when (DoorPosition)` → `stringResource`); `warning` → `DoorWarning` sealed type; `formatDuration` deleted (Composable side w/ plurals) | ~12 + plurals | TODO |
| `HistoryMapper`: parallel refactor — date-grouping labels, item subtitles | ~6 | TODO |
| `NotificationPermissionCopy` → `NotificationJustification(attemptCount: Int)` data class; `Composable.justificationMessage(j)` assembles via stringResource + plural for "$attemptCount times" | 4 strings + 1 plural | TODO |
| `HomeAlert.Stale` / `HomeAlert.FetchError` default messages → drop default-string args, Composable resolves type → resource | 3 | TODO |
| `DiagnosticsMapper` (if any): counter labels → typed enum | TBD | TODO |

##### Phase 3 — Lint + cleanup

| Work | PR |
|---|---|
| Add `checkNoLiteralStringsInCompose` lint to `validate.sh` + exemption file for any Phase-1/2 stragglers | TODO |
| Optional: `checkNoEmDashInStringResources` lint scanning `strings.xml` for `—` | TODO |
| Review entire `strings.xml` for sentence case consistency | TODO |

#### Per-PR checklist

- [ ] Strings extracted with `<screen>_<section>_<purpose>[_<state>]` naming (snake_case)
- [ ] `formatArgs` (`%1$s`, `%2$d`) for interpolated strings
- [ ] `<plurals>` + `pluralStringResource` for count-based strings
- [ ] `@Preview` fake data NOT migrated (not production-visible labels)
- [ ] All values byte-identical to pre-migration → no screenshot churn (assert `git status AndroidGarage/android-screenshot-tests/` is clean)
- [ ] If screenshot churn happens, it's an intentional copy change in the same PR — note it in the PR body
- [ ] `R` imported as `com.chriscartland.garage.R`
- [ ] `import androidx.compose.ui.res.stringResource` (or `pluralStringResource`)
- [ ] `validate.sh` PASS before push (per the validate-before-first-push rule in `CLAUDE.md`)
- [ ] PR description lists strings extracted + what's deferred to other PRs

#### Out of scope for migration PRs

- **Copy revisions.** If a string reads poorly during the move, file a follow-up — don't conflate "extract to resource" with "rewrite". Mixing both makes the PR harder to review and the diff harder to revert.
- **Localization itself.** This plan unblocks localization but does not add `values-<lang>/strings.xml` files; that's a separate decision.
- **String resources for FCM payloads, Firestore field names, log keys.** These are wire / internal — not user-visible labels.

</details>

## Done (recent)

- **String-resource migration COMPLETE — 12 PRs, 2026-05-10 → 2026-05-11.** Every user-visible label in the production app now lives in `androidApp/src/main/res/values/strings.xml`; mappers and ViewModels emit typed values; the Composable layer resolves to localized strings via `stringResource` + `pluralStringResource` at render time. Phase 1 (Composable-scope mechanical migrations, ~50 strings across 12 files): SettingsContent (#784), HomeContent (#786), DiagnosticsContent + SnoozeBottomSheet + AccountBottomSheet + VersionBottomSheet + ProfileContent + AuthTokenCopier (#787), DoorHistoryContent + InfoBottomSheet (#788), FunctionListContent (#789). Phase 2 (typed-hint refactors of mapper APIs): `DoorWarning` sealed type (#790), `HomeMapper.stateLabel` deletion + `doorStateLabel(DoorPosition)` Composable resolver (#791), `HomeStatusFormatter` pure-function utility + `rememberSinceLine` Composable + `home_duration_*` plurals + `HomeAlert` default-arg drops (#793), `HistoryMapper` full refactor — `AnomalyKind` / `DayLabel` / `TransitWarning` / `HistoryFormatter`, ~50 test rewrites (#794), `NotificationJustification` typed (#795). Phase 3: `checkNoLiteralStringsInCompose` lint Gradle task in `validate.sh` (#792) — guards against new `Text("literal")` regressions; exemption file empty. Plan + per-PR rationale archived in this file under "Archived". Typed-hint pattern (`AnomalyKind`, `DayLabel`, `TransitWarning`, `DoorWarning`, `NotificationJustification`, etc.) now established as the convention for any new mapper that would otherwise emit user-visible strings.
- **Dedicated Developer allowlist flag — both sides shipped.** Server: `server/26` (2026-05-10) added `GET /developerAccess` and the Firestore `featureDeveloperAllowedEmails` field. Android: `android/235` / 2.16.21 (2026-05-11, PR #781) flipped Settings → Developer's outer gate to the new endpoint and added an independent `showFunctionListRow` gate (still keyed on `functionListAccess`) so the two allowlists can diverge. `KtorNetworkFeatureAllowlistDataSource` now issues both endpoint GETs in parallel and combines them; the data-source-level abstraction stayed unchanged so no new repository/DI wiring was needed (the original ~6-file plan turned out to be ~12 files concentrated at the data + UI layers instead). Smoke matrix in `PENDING_SMOKE_TESTS.md` item 6.
- **Home permission banner copy revision** — closed in 2.16.16 (`copy/home-permission-banner-shorter`). Production `NotificationPermissionCopy.justificationText(0)` now reads *"Turn on notifications to get alerted when the door is left open."* (2 lines on Home, was 3). Same imperative-request framing so escalation lines at attempt 3+/4+ still flow without changes. Settings row copy was already short (em-dash sweep landed in 2.16.9). Three variants are no longer in tension; if a future PR wants a single canonical string both surfaces could read from, that's a fresh follow-up.
