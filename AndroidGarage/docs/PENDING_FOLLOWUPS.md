---
category: plan
status: active
last_verified: 2026-05-13
---

> **Last update 2026-05-13:** iOS Phase 38A fully complete — `NativeComponent` DI graph runtime-verified via `NativeComponentTest` (40/40 pass on `iosSimulatorArm64`, PR #826). Phase 38B–G remain blocked on user setup. User-action checklist added as the first subsection below so the user-blocked moves are visible without scrolling.

# Pending Follow-ups

User-flagged items that aren't tied to a specific release and aren't smoke-test verifications — feature follow-ups, copy revisions, design open questions. Each item should be discrete enough that a single PR could close it.

**Scope:** items that need their own design/implementation effort. Smoke-test verifications belong in [`PENDING_SMOKE_TESTS.md`](./PENDING_SMOKE_TESTS.md). Per-version implementation history belongs in [`../CHANGELOG.md`](../CHANGELOG.md). Architectural conventions belong in [`../../CLAUDE.md`](../../CLAUDE.md).

**Why this lives in the repo, not memory:** project-specific TODOs need to be reviewable in PRs and discoverable to anyone reading the repo cold. See `feedback_dump_context_repo_first.md` for the rule.

## User action items (2026-05-13)

Concrete things only the user can do. Each item points to the detailed section below or to a sibling doc. Ordered by leverage (A unblocks the most downstream work).

### A. Unblock iOS Phase 38B — Apple Developer + Firebase iOS setup (~1 hour, one-time)

Single block unblocks ~10 PRs of Swift / SwiftUI work. Three sub-steps:

1. **Apple Developer Account** → register bundle ID `com.chriscartland.garage`.
2. **Firebase Console** → add iOS app to the existing project (same bundle ID). Download `GoogleService-Info.plist`. (Config, not a secret — same posture as Android's `google-services.json`.) Hand the file to the agent and it will commit to `AndroidGarage/iosApp/iosApp/`.
3. **Apple Developer Console** → generate an APNs `.p8` key, then upload it to Firebase Console → Cloud Messaging tab. (`.p8` chosen over `.cer` so there's no annual rotation.)

Once those land, tell the agent "iOS setup done" and it will start Phase 38B (Xcode project scaffold).

Reference: § 1.A below.

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

**Status:** Blocked on Apple Developer + Firebase iOS setup. The Kotlin/KMP side is complete as of `feat/ios-skie-nativecomponent` (PR #824) — the iOS framework builds end-to-end with the full kotlin-inject DI graph and SKIE bridging.

**What's already done** (5 merged PRs):

| PR | Scope |
|---|---|
| [#820](https://github.com/cartland/SmartGarageDoor/pull/820) | Delete dormant `:shared` scaffold, add `:iosFramework` Gradle module |
| [#821](https://github.com/cartland/SmartGarageDoor/pull/821) | iOS targets on `:domain` + `:data`; Ktor Darwin engine; `kotlin.jvm.JvmInline` + `kotlinx.coroutines.IO` portability fixes |
| ~~[#822](https://github.com/cartland/SmartGarageDoor/pull/822)~~ | (Closed — content bundled into #823's squash-merge) |
| [#823](https://github.com/cartland/SmartGarageDoor/pull/823) | `:data-local` Room KMP + DataStore-Okio; DAO suspend refactor; kotlinx-datetime for `currentTimeMillis`; `DatabaseFactory` expect/actual; iOS targets on `:usecase`/`:viewmodel`/`:presentation-model`/`:test-common`; `AppStartup` moved to `:usecase/commonMain` |
| [#824](https://github.com/cartland/SmartGarageDoor/pull/824) | SKIE 0.10.9 plugin; `NativeComponent` DI graph (mirrors `AppComponent` with iOS deps); `IosNativeHelper`; `NoOpAuthBridge` + `NoOpMessagingBridge` placeholders; kotlin-inject 0.8.0 downgrade (see follow-up #2 below) |

**Remaining work** — each requires user setup that the Kotlin side can't supply:

#### A. Firebase iOS configuration (one-time, user)
- Create iOS app in the existing Firebase project (bundle ID `com.chriscartland.garage` per the iOS plan)
- Download `GoogleService-Info.plist` and commit to `AndroidGarage/iosApp/iosApp/` (it's config, not a secret — same posture as Android's `google-services.json`)
- Auto-generated iOS OAuth client ID gets pasted into `Info.plist` URL Types
- Generate APNs `.p8` key in Apple Developer console, upload to Firebase Cloud Messaging tab (decision: `.p8` key over `.cer` cert — no annual rotation)

#### B. Xcode project scaffold (1 PR)
- New folder `AndroidGarage/iosApp/` with `iosApp.xcodeproj` and standard SwiftUI app skeleton
- Folder layout matches battery-butler's `ios-app-swift-ui/`: `Core/`, `Features/`, `iosApp/iOSApp.swift`, `iosApp/AppDelegate.swift`
- Build phase script invokes `./gradlew :iosFramework:embedAndSignAppleFrameworkForXcode`
- `FRAMEWORK_SEARCH_PATHS` points at `$(SRCROOT)/../iosFramework/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
- Embed `shared.framework` with "Embed & Sign"
- SPM packages: `firebase-ios-sdk` (Auth + Messaging), `GoogleSignIn-iOS`
- Capabilities: Push Notifications, Background Modes → Remote notifications
- Deployment target: iOS 16
- `SWIFT_STRICT_CONCURRENCY = minimal` + `@preconcurrency import shared` everywhere (Kotlin types lack Swift `Sendable` info)
- Empty `iOSApp.swift` that just calls `IosNativeHelper().createComponent(NoOpAuthBridge, NoOpMessagingBridge, IosNativeHelper.defaultDevAppConfig)` — proves the framework integration before any real bridges

#### C. Swift bridge implementations (1 PR)
- `iosApp/Auth/FirebaseAuthBridge.swift`: conforms to Kotlin's `AuthBridge` protocol. Wraps Firebase `Auth.auth().addStateDidChangeListener` in `callbackFlow` per ADR-018; `signInWithGoogleToken(idToken)` calls `Auth.auth().signIn(with: GoogleAuthProvider.credential(...))`; `getIdToken(forceRefresh:)` and `signOut()` thin wrappers
- `iosApp/Fcm/FirebaseMessagingBridge.swift`: conforms to Kotlin's `MessagingBridge`. `subscribeToTopic` / `unsubscribeFromTopic` / `getToken()` via `Messaging.messaging()` calls
- `iosApp/AppDelegate.swift`: `UIApplicationDelegate` adopter. `application(_:didFinishLaunchingWithOptions:)` calls `FirebaseApp.configure()`, instantiates bridges, calls `IosNativeHelper().createComponent(...)`, kicks `component.appStartup.run()`. `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` forwards APNs token to FCM
- `UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:)` calls `component.receiveFcmDoorEventUseCase(event)` with the parsed payload — same KMP `FcmMessageHandler` Android uses

#### D. SwiftUI infrastructure (1 PR)
- `Core/SharedViewModel.swift` — the `@StateObject`-friendly wrapper that owns a `ViewModelStore` tied to SwiftUI view lifetime (pattern from kmp-mvvm-exploration). `deinit` → `viewModelStore.clear()` → Kotlin `viewModelScope` cancels
- `Core/KotlinAliases.swift` — `typealias` cleanups for K/N's snake_case generated type names
- `Core/Theme/` — hand-translated `Colors.swift` and `Spacing.swift` mirroring `androidApp/ui/theme/`. Light/dark via system
- Empty `Features/` directory ready for screens
- Tab bar shell with 5 placeholder tabs

#### E. SwiftUI screens (5 PRs, one per screen)
- `Features/Home/HomeScreen.swift` + `HomeViewModelWrapper.swift` — canonical example. Uses SKIE's `.collect(flow:into:)` modifier to bind `DefaultHomeViewModel`'s `StateFlow`s to SwiftUI `@State`. Actions dispatch via direct `vm.instance.onTapButton()` calls. Sealed-class state types map to Swift `enum` via SKIE's `switch onEnum(of:)`
- `Features/History/HistoryScreen.swift` + wrapper
- `Features/Profile/ProfileScreen.swift` + wrapper
- `Features/FunctionList/FunctionListScreen.swift` + wrapper
- `Features/Diagnostics/DiagnosticsScreen.swift` + wrapper

#### F. CI + release (1 PR)
- New `scripts/release-ios.sh` mirroring `release-android.sh`: bump `CFBundleShortVersionString` + `CFBundleVersion`, tag `ios/N`, `xcodebuild archive` + `xcrun altool` to TestFlight
- New `scripts/validate-ios.sh` building the framework + running Kotlin tests for iOS targets + `xcodebuild test` for SwiftUI unit tests
- GitHub Actions `ios-ci.yml` on `macos-latest`
- `AndroidGarage/iosApp/CHANGELOG.md` with `## N.M.K` heading rule (mirrors Android changelog gate)
- First TestFlight Internal Testing distribution

#### G. App Store submission (1 PR — last)
- App icon (1024×1024, reuse Android artwork's primary glyph)
- Screenshots for App Store Connect (iPhone + iPad)
- Marketing description, keywords, age rating, privacy policy URL, support URL
- Encryption export compliance: `ITSAppUsesNonExemptEncryption = NO` in `Info.plist`
- Privacy manifest `PrivacyInfo.xcprivacy` (Firebase Auth = "User ID, linked to user, used for App Functionality")
- Submit for review

**Sequencing**: B → C → D → E (each screen as a separate PR, parallelizable after D) → F → G. Total: 10–14 PRs depending on whether screens parallelize.

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
