---
category: plan
status: active
last_verified: 2026-06-29
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

**The remaining iOS work is the go-forward plan in § 1 "Remaining plan"** — sign-in verification, push *delivery* (device), Phase F release tooling, Phase G App Store, and the one remaining parity gap (iPad/adaptive layout — the door-canvas animation gap closed 2026-06-29). **Code signing + the first TestFlight build are DONE (2026-06-30)** — full procedure + secrets map in [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md). What still needs the user/device: install the TestFlight build to verify **push delivery** + the interactive **sign-in tap-through**, plus (later) the App Store submission.

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

**Status (2026-06-29):** The iOS app is **functional on the simulator with real backend data**. Phases B–F (Xcode project, SwiftUI screens, iOS CI) shipped 2026-06-01; Phase A (Apple Developer + Firebase iOS) is done; Phase C (real Firebase Auth/Messaging bridges + Google Sign-In) shipped 2026-06-24, plus garage backend config (live door data) and the FCM-receive path. The cross-platform **door program + parity backlog landed 2026-06-29** (#949–#979): the animated door canvas (`AnimatedDoorCanvas` with `withAnimation` + shared `DoorAnimationMemory` replay), shared history-alert mapping + stale banner, device-availability pills, History load-more, and the ADR-032 UI-fidelity tiers. **Verified on the simulator:** Firebase init, real signed-out auth state, notification-permission prompt, and **live door STATUS from the production server**. **Remaining is mostly user/device-gated:** interactive sign-in verification, push *delivery* (needs a signed device build), release tooling (Phase F), App Store (Phase G), plus the one remaining feature-parity gap (iPad/adaptive layout — iOS is a plain `TabView` vs Android's `AppLayoutMode`). **Code signing + the first TestFlight build shipped 2026-06-30** (automatic distribution signing, `aps-environment` push entitlement, 1024 app icon; archived → validated → uploaded to TestFlight Internal) — runbook: [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md). The go-forward plan is in **"Remaining plan"** below.

**What's already done** (5 merged PRs):

| PR | Scope |
|---|---|
| [#820](https://github.com/cartland/SmartGarageDoor/pull/820) | Delete dormant `:shared` scaffold, add `:iosFramework` Gradle module |
| [#821](https://github.com/cartland/SmartGarageDoor/pull/821) | iOS targets on `:domain` + `:data`; Ktor Darwin engine; `kotlin.jvm.JvmInline` + `kotlinx.coroutines.IO` portability fixes |
| ~~[#822](https://github.com/cartland/SmartGarageDoor/pull/822)~~ | (Closed — content bundled into #823's squash-merge) |
| [#823](https://github.com/cartland/SmartGarageDoor/pull/823) | `:data-local` Room KMP + DataStore-Okio; DAO suspend refactor; kotlinx-datetime for `currentTimeMillis`; `DatabaseFactory` expect/actual; iOS targets on `:usecase`/`:viewmodel`/`:presentation-model`/`:test-common`; `AppStartup` moved to `:usecase/commonMain` |
| [#824](https://github.com/cartland/SmartGarageDoor/pull/824) | SKIE 0.10.9 plugin; `NativeComponent` DI graph (mirrors `AppComponent` with iOS deps); `IosNativeHelper`; `NoOpAuthBridge` + `NoOpMessagingBridge` placeholders; kotlin-inject 0.8.0 downgrade (see follow-up #2 below) |

**Shipped (Phases A–F + functional backend):**
- **B/D/E/F** (PRs #856/#857/#858, 2026-06-01) — `MobileGarage/iosApp/` foundation (XcodeGen `project.yml`, `SharedViewModel`, theme tokens, 5-tab shell, `KmpViewModelStore`), `.github/workflows/ios-ci.yml` (`macos-latest`), and all 5 screens (Home / History / Profile / Functions / Diagnostics) wired to the real `Default*ViewModel`s via `*ViewModelWrapper`s.
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
   - ✅ **`scripts/validate-ios.sh` — SHIPPED (#992).** CI-exact mirror of `ios-ci.yml` (`:iosFramework:iosSimulatorArm64Test` → `xcodegen generate` → `xcodebuild` simulator build, signing disabled). The local mitigation for the non-required-iOS-CI trap. Snapshot tests intentionally excluded (regenerate-don't-assert, non-gating).
   - ✅ **`MobileGarage/iosApp/CHANGELOG.md` — SEEDED (#992).** History doc with AGENTS.md front-matter; the file the future release gate will read. Keep it current as iOS changes merge.
   - ✅ **`scripts/release-ios.sh` + `.github/workflows/release-ios.yml` — DONE + VERIFIED END-TO-END (`ios/1`, 2026-06-30).** Mirror `release-android.sh`: the script gates (clean tree + `validate-ios.sh` marker + `iosApp/CHANGELOG.md` entry for `MARKETING_VERSION`) and pushes `ios/N`; the workflow archives Release (overriding `CURRENT_PROJECT_VERSION` to N) and `xcodebuild -exportArchive destination=upload` → TestFlight Internal. First automated release (`ios/1`, build 1 / 0.1.0) archived + signed + uploaded green. **The four GitHub secrets are set** — note the API key must be **Admin** role (App Manager fails cloud-signing the Distribution cert; caught on `ios/1` attempt 1). Procedure + the role gotcha: [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md) § "Automated releases".
   - ✅ **Signing + first TestFlight upload — DONE (2026-06-30).** App ID (`com.chriscartland.garage`) + Push capability registered, App Store Connect record (`Garage by Chris Cartland`) created, automatic distribution signing + `aps-environment` entitlement (Debug=development / Release=production) + 1024 app icon wired; first build archived → validated → uploaded to TestFlight Internal via Xcode. Full procedure + the secrets map: [`../../docs/IOS_RELEASE_SETUP.md`](../../docs/IOS_RELEASE_SETUP.md).
   - Consider promoting iOS CI (`Build iOS app + framework test`) to a required check once stable (per CLAUDE.md branch-protection ordering) — closes the non-required-iOS-CI / auto-merge race.
4. **Phase G — App Store** (submission itself user-gated; prep largely DONE 2026-07-05):
   - ✅ **`PrivacyInfo.xcprivacy` — SHIPPED (#1056).** No tracking; User ID + email (linked, app functionality); required-reason APIs (FileTimestamp DDA9.1/C617.1, UserDefaults CA92.1, SystemBootTime 35F9.1). Verified present in the built bundle. **`ios/6` (0.1.0, build 6) was uploaded to TestFlight 2026-07-05 containing the manifest + the #1055 door fix — this is the submission-ready build.** (Marketing version stays 0.1.0 by choice; bump toward 1.0 later.)
   - ✅ **Listing copy + first screenshots — STAGED (#1057)** in `MobileGarage/distribution/appstore/` (`LISTING.md` = subtitle/promo/description/keywords + App Privacy answers + review notes + capture runbook + submission checklist; Home shots at exact ASC sizes for iPhone 6.9" + iPad 13").
   - ✅ **Fresh-install door render bug — FIXED (#1055)** (found while capturing: `AnimatedDoorCanvas` stale-capture `onChange` left the door visibly half-open on first launch until relaunch; live transitions animated one state behind).
   - **Remaining (user):** privacy-policy URL, History/Settings screenshots (one manual tap each — runbook in `LISTING.md`), ASC data entry, pick build, submit.

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

**The pin family grew 2026-07-05 (#1053):** **kermit 2.0.4**, **androidx.datastore 1.1.7**, and **androidx.sqlite 2.5.0** are pinned for the identical reason — their next minors ship iOS KLIBs built with Kotlin 2.2.0/2.2.20/2.3.20. The #1033 Dependabot group bumped them; Android compiled fine so every *required* check passed and it auto-merged, breaking only the non-required iOS CI on main (fix-forward #1053; all three are now in `dependabot.yml`'s ignore list). **Unpin all four together with the Kotlin 2.2+ bump.** General rule: any KMP dep consumed from `commonMain` can hit this — check what Kotlin its iOS KLIBs are built with before bumping.

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

- ✅ **M1 — SHIPPED (#1050, rides `server/34`).** All five scheduled functions declare `runWith(PUBSUB_RUNTIME_OPTS)` (`maxInstances: 1`, 60 s, 256 MB) — scheduled jobs are periodic singletons, so a long tick queues instead of fanning out.
- **M2** — `httpServerConfig` / `httpServerConfigUpdate` use deprecated `functions.config()` API for the static `X-ServerConfigKey` gate. Should migrate to `firebase secrets` and add Firebase ID-token gate ABOVE the static-key check (so even the key holder must be authenticated).
- ✅ **M3 — RESOLVED (premise now stale, 2026-07-05).** The "4 documented allowlists" claim no longer exists anywhere in CLAUDE.md or docs (verified via `git grep` — the only match was this finding itself); the CLAUDE.md sections it referred to were rewritten since the audit. Reality stands as: `snooze`/`remoteButton`/`buttonHealth` share `remoteButtonAuthorizedEmails`; `functionList`, `developerAccess`, and (since `server/33`) `configFlagAdmin` have their own. Nothing to change; splitting snooze out remains an option if ever wanted.
- ✅ **M4 — SHIPPED (#1051).** `r0adkll/upload-google-play` (by then at `v1.1.5` via Dependabot) is pinned to the tag's commit SHA with an inline bump procedure. First-party `actions/*` stay on major tags per the audit's risk assessment.
- **M5** — No required PR reviewers in branch protection (acceptable for solo, worth noting).
- ✅ **M6 — SHIPPED (#984).** `backup_rules.xml` + `data_extraction_rules.xml` now exclude the Room DB (`database` + `-shm`/`-wal`) and both DataStore files (`app_settings.preferences_pb`, `diagnostics_counters.preferences_pb`) from cloud backup; `allowBackup` stays on and `<device-transfer>` is intact. Paths source-confirmed (`getDatabasePath("database")`, `filesDir`-resolved DataStore) so an exclude can't fail open. Firebase Auth's own backup behavior was left as a possible follow-up (not named in M6; fragile internal paths).
- ✅ **M7 — SHIPPED (#1050, rides `server/34`).** `httpDeleteOldData` rejects a missing / non-numeric / non-positive / future `cutoffTimestampSeconds` with a 400 (a huge/future value could previously mean "delete everything"; `cutoff = now` keeps the deliberate full wipe expressible). The `eventHistoryMaxCount` half was already fixed by the pagination work (`parsePositiveInt` + clamp in `Events.ts`, `server/28`).
- **M8** — ESP32 WPA2-PSK without PMF required (KRACK / deauth surface). Set `pmf_cfg.required = true; .capable = true` in `wifi_connector.c`.
- **M9** — ESP32 test fakes are flag-gated by hand-edited `garage_config.h:5-11`, not a Kconfig switch. Easy to ship a build that opens the door every other poll. Move to Kconfig + `#error` guard on release optimization.
- **M10** — `Arduino_ESP32/*/secrets.h` are tracked (placeholder values), not gitignored. Foot-gun for future commits. `git rm --cached` + rename to `secrets.template.h`.
- **M11** — ESP32: no flash encryption, no secure boot v2, no NVS encryption. Anyone with physical access can dump flash and read WiFi creds. Enable `CONFIG_SECURE_FLASH_ENC_ENABLED=y` + secure boot v2 (the bigger ESP32 hardening project).
- **M12** — Firmware-pinned root CA expires 2036-06-22 with no OTA path. Needs signed OTA implemented before 2036, OR bake in fresher GTS Root R3/R4.
- ✅ **M13 — SHIPPED (#982).** The two bash scripts (`decrypt`/`encrypt`) got `set -euo pipefail` (with `${ENCRYPT_KEY:-}` guarding the presence test under `set -u`, and quoted gpg paths); `clean-secrets.sh` (`#!/bin/sh`) got `set -eu` (pipefail isn't POSIX-portable). A failing `gpg` can no longer continue silently.
- ✅ **M14 — SHIPPED (#981).** `.github/dependabot.yml` added with weekly grouped updates for npm (`/FirebaseServer`), gradle (`/MobileGarage`), and github-actions (`/`). Merging stays manual per `docs/DEPENDENCY_UPGRADES.md`; the Node-22 / kotlin-inject-0.8.0 runtime pins are unaffected (Dependabot bumps package versions, not the runtime).
- **M15** — ESP-IDF version not pinned (`idf_component.yml` missing). Recommend `idf: ">=5.3.2,<5.4"` and document upgrade cadence.
- **M16** — Compose BOM ~6 months behind (2025.06.01 vs 2026.01+). No known CVEs at the pinned version; lag is the largest deferred Android upgrade.

**Low / informational findings deferred:**

- 7 of 9 GitHub Actions pinned to floating major tags (not SHAs). First-party = lower risk; third-party (`r0adkll/upload-google-play`) covered by M4 (✅ shipped #1051).
- ✅ **Cleartext-localhost — SHIPPED (#1052).** `network_security_config.xml` split by source set: main is an explicit no-cleartext base config; the debug source set overrides with the localhost/127.0.0.1/10.0.2.2 exceptions. Verified per-variant via merged-resource inspection.
- ProGuard `-keep class com.chriscartland.garage.data.ktor.** { *; }` is broader than needed per ADR-020.
- `cJSON_Print(root)` heap leak inside `ESP_LOGI` — slow OOM over months of uptime.
- No watchdog explicitly armed on ESP32 tasks.
- Server log retention = default 30 days (Cloud Logging). Consider 7-day pin now that token logging is fixed.
- No `/deleteMyData` endpoint (fine for single-user; GDPR-relevant if ever multi-tenant).

**Picking one to ship:** M6, M13, M14 shipped 2026-06-29 (#984 / #982 / #981); M1, M4, M7, the cleartext low-finding, and M3 (resolved-stale) shipped 2026-07-05 (#1050–#1052). The remaining Mediums are larger or need coordination (M11 = flash encryption rollout; M2 = secrets migration + Android client change; M5 policy; M8–M10/M12/M15 = ESP32 work needing the IDF toolchain; M16 = Compose BOM, partially advanced by the #1033 group bump).

### 4. iOS ↔ Android parity-audit findings (2026-06-28)

> **Status (2026-06-29): this batch is COMPLETE.** Every item below shipped across PRs #971–#978 (delete `ProfileScreenState` #974, Diagnostics order #975, "Function list" + Built row #976, HistoryAlert mapper + iOS stale-banner #977, device pill wifi/wifi.slash #978, dead `ErrorCard` #971, iOS Diagnostics copy-token + clear-confirm #972, Home label #973) — plus the earlier door-constants hoist and copy-token/Export-CSV work. The one item not changed (warning tint) was a deliberate "leave as-is" call. § 4 is now resolved.

A 13-agent screen-by-screen parity audit (6 auditors + adversarial verifiers + synthesis, all findings file:line-verified) classified every screen against the [ADR-032](./DECISIONS.md#adr-032) fidelity tiers ([`UI_FIDELITY_TIERS.md`](./UI_FIDELITY_TIERS.md)). Overall verdict: **parity is fundamentally healthy** — the meaning-bearing layers are genuinely shared (History pipeline, snooze typed-failures, info-sheet copy, test-notification sandbox, access tri-states, door semantics). The discrete gaps below are each a self-contained PR. **Already fixed in the same batch:** the one HIGH finding — iOS Home showed an inert remote button when signed out with no Home sign-in path — was resolved by auth-gating iOS Home (consume the shared `AuthState`; signed-out → sign-in CTA + button hidden; redundant Account row dropped).

**Tier 1 — identity (highest leverage):**
- **Door constants shared-hoist (systemic).** The door geometry/palette/offsets/mappings were **hand-duplicated** Kotlin↔Swift with no guard — they matched by coincidence, so an edit on one side silently drifted the brand on the other. Hoist to shared `:domain` constants (the `AppLinks` pattern), consumed by both. Done in slices:
  - ✅ **Geometry — SHIPPED.** Viewport / frame / panel / handle + the derived `CLIP_INSET` now live in `:domain` `GarageDoorGeometry`, read by both `GarageDoorCanvas.kt` and `GarageDoorCanvas.swift`; pinned by `GarageDoorGeometryTest` (commonTest). Verified pure no-op (byte-identical iOS snapshots; the `clipInset` fragility — derived-vs-hardcoded — is gone, both are now the single derived `22`).
  - ✅ **Color palette — SHIPPED.** The 12 light/dark door-fill hex values now live in `:domain` `GarageDoorPalette` (opaque ARGB Longs), read by Android `Color.kt` (`Color(Long)`) and iOS `DoorPalette` (masked to 24-bit → `Color(rgb:)`); pinned by `GarageDoorPaletteTest` (commonTest: opacity, gray-has-no-stale, colored-states-do). The Android-only `on*` text variants stay local (iOS draws overlays with the system label color), so they were correctly left out. Verified pure no-op (all 12 values already matched; byte-identical iOS snapshots).
  - ✅ **Offsets + offset/overlay mappings — SHIPPED.** The 5 offset constants + the full pure `DoorAnimation` spec object (`targetPositionFor`/`fromPositionFor`/`useSpringFor`/`staticPositionFor`/`overlayFor`) + the `DoorOverlayKind` enum now live in `:domain` `DoorAnimation`; pinned by `DoorAnimationTest` (commonTest, moved from the Android unit test). Android `GarageIcon` + iOS `GarageDoorView` both read it (iOS consumes `staticPositionFor` + `overlayFor`; Android also drives its live trajectory from target/from/spring). Verified pure no-op (mappings already identical; byte-identical iOS snapshots).
  - ✅ **colorState mapping — SHIPPED.** `DoorPosition → DoorColorState` (which palette family a state uses) now lives in `:domain` `DoorAnimation.colorStateFor` + the shared `DoorColorState` enum; pinned by `DoorAnimationTest`. Android `doorColorState()` delegates to it (a theme-package `typealias DoorColorState` keeps the 3 UI consumers — `DoorStatusCard` / `HomeContent` / `HistoryContent` — zero-churn; the null-event `else → OPEN` fallback preserved); iOS `DoorPalette.doorRGB` calls the shared mapping (its local enum + `DoorVisual` removed). Verified pure no-op (mapping already identical; byte-identical iOS snapshots). **The door visualization is now fully shared `:domain`** — geometry + palette + the complete visual/animation spec.
  - ✅ **Live-trajectory convergence (b) — SHIPPED.** The maintainer confirmed the 12 s pacing is **brand-essential** (real door travel + ~2 s network slack so the terminal event lands mid-slide and the icon springs to target), i.e. Tier 1 → iOS must match. Shipped in two PRs: the duration + replay policy were hoisted to `:domain` (`DoorAnimation.ANIMATION_DURATION_SECONDS` + `DoorMotionKey`/`DoorAnimationMemory`, pinned by `DoorAnimationTest`/`DoorAnimationMemoryTest`, #959, Android no-op), then iOS's `GarageDoorView` gained an animated mode (#960) — a SwiftUI `@State` offset driven by `withAnimation`: 12 s linear slide for OPENING/CLOSING, slow no-overshoot spring settle on the terminal event, replay-once-per-event via the shared memory (held app-root in `MainScreen`, injected via a `\.doorAnimationMemory` environment value). Per the ADR-032 spec-vs-execution split, the *parameters* are shared/provable; the SwiftUI interpolation is best-effort native execution. Verified: CI-exact iOS build clean + byte-identical static snapshots (all Home preview states are non-motion, so animated mode's at-rest frame equals the static frame). **The door visualization — static and dynamic — is now fully shared `:domain`.**
- ✅ **Device-availability pill icon family — SHIPPED.** Both iOS Home pills (`DeviceCheckInPill` + `RemoteButtonHealthPill`) switched from `antenna.radiowaves…(.slash)` to `wifi`/`wifi.slash` (#978) — the nearest SF visual equivalent of Android Material `Sensors`/`SensorsOff` (concentric signal arcs). The cross-platform pairing is recorded in the `DeviceCheckInPill` doc comment.

**Tier 2 — convergent gaps (mostly iOS-side):**
- ✅ **Copy auth token — SHIPPED.** iOS Functions panel gained it (#966: shared `GetAuthTokenForCopyUseCase` + `UIPasteboard` write with a 2-min expiration as the sensitivity posture + a `CopyAuthTokenButton` flash). Then both platforms were routed through their ViewModels (#968, ADR-033): the UI calls `vm.fetchAuthTokenForCopy()`, never the usecase directly. (iOS Diagnostics copy-token shipped in #972, reusing the `AuthTokenCopier` helper + the VM's `fetchAuthTokenForCopy`.)
- ✅ **Export CSV — SHIPPED.** iOS Diagnostics gained it (#965: shared `AppLogCsv` builder + `UIActivityViewController` share sheet). Then routed through `DiagnosticsViewModel.buildExportCsv()` via a new shared `BuildAppLogCsvUseCase` (#969, ADR-033) — the UI no longer touches the repo. Android writes to a content `Uri`; iOS shares a temp `.csv`; the CSV bytes are identical (shared `AppLogCsv`).
- ✅ **History stale banner + reset-FCM recovery — SHIPPED.** New shared `HistoryAlert` + `HistoryAlertMapper` (presentation-model, mirroring `HomeAlertMapper`, #977) route the show/hide decision for both platforms; Android `DoorHistoryContent` + iOS `HistoryScreen` consume it. iOS gained the tinted recovery banner + `resetFcmAndRefetch()` (deregister FCM + refetch).
- ✅ **History load-more pagination — SHIPPED.** iOS `HistoryViewModelWrapper` now consumes the shared `paginationState` (exposing `canLoadMore` / `isLoadingMore`) + calls `fetchOlderDoorEvents()` via `loadMore()`; `HistoryContentView` renders a bottom `HistoryLoadMoreFooter` (spinner while loading, "You've reached the beginning of your history" terminal note) whose `onAppear` is the scroll-to-end trigger. Mirrors Android's `HistoryFooter` + near-end `LaunchedEffect`. Cold-start fetch (`appStartup.run()` → `InitialDoorFetchManager`) populates the cursor, so the footer is live.
- ✅ **About build-timestamp copy row — SHIPPED.** iOS About gained a 4th copyable "Built" row (#976), sourced from the app bundle (the executable's modification date == build/link time), formatted to match Android's `yyyy-MM-dd HH:mm:ss UTC`.
- ✅ **`ProfileScreenState` dead slice — SHIPPED (deleted, #974).** It was consumed nowhere; deleted as dead code (the maintainer chose delete over realizing it as a shared slice).
- ✅ **Diagnostics counter row order — SHIPPED.** Android reordered so "FCM received" precedes "FCM subscribe" (#975), matching iOS + the shared `DiagnosticsViewModel` field order (the canonical order). Labels stay per-UI.

**Tier 2/3 — low-severity polish:**
- ✅ **Warning tint — resolved (left as-is).** iOS warning tint stays `Color.red` (`GarageColors.statusWarning`) — it's iOS-idiomatic (`systemRed`); the maintainer chose to keep it rather than soften toward a custom warning hue.
- ✅ **Label drift — SHIPPED.** iOS Home section header "Remote button" → "Remote control" (#973, matching Android + iOS's own info-sheet); iOS Functions surface "Functions" → "Function list" (#976, matching Android). Strings stay per-UI per ADR-031.
- ✅ **iOS Diagnostics clear-all confirm — SHIPPED (#972).** Now states the scope (counters + event log), what's unaffected (door history, settings), and the irreversibility, matching Android.
- ✅ **Dead Android `ErrorCard` — SHIPPED (deleted, #971).** The unreachable fetch-error `ErrorCard` in `DoorHistoryContent` was removed (the live stale-check-in `ErrorCard` is unchanged).

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
- [ ] All values byte-identical to pre-migration → no screenshot churn (assert `git status MobileGarage/android-screenshot-tests/` is clean)
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
