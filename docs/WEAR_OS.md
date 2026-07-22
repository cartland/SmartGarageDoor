---
category: reference
status: active
last_verified: 2026-07-22
---

# Wear OS App (`MobileGarage/wearApp/`)

A standalone Wear OS app with a single hero experience: the animated garage
door with tap-to-arm and a 2-second radial hold-to-confirm remote button.
Built on the same shared KMP modules as the phone and iOS apps.

## The hero interaction

The screen is one animated door (same `:domain` `DoorAnimation` spec, geometry,
and palette as phone/iOS) with the door state label under it.

1. **Tap the door** while signed in → arms the button
   (`Ready → Preparing → AwaitingConfirmation` on the shared `ButtonStateMachine`).
   A faint ring appears around the door: "this is holdable."
2. **Press and hold the door for 2 seconds** while armed → a radial progress
   ring sweeps around the door; when it completes, the machine's second tap
   fires and the press is submitted (`SendingToServer → SendingToDoor →
   Succeeded` when the door actually moves).
3. **Release early** → the ring resets, nothing is sent. The machine's own
   8s confirmation timeout eventually disarms (`Cancelled → Ready`).

A quick tap while armed does nothing — deliberately stricter than the phone's
second-tap confirm, because a watch face is much easier to touch accidentally.
`WearHomeViewModelTest` pins these safety properties (stray tap never submits,
early release never submits, signed-out is inert).

## Architecture

- **Module**: `:wearApp` (`com.chriscartland.garage.wear`), Compose for
  Wear OS **Material 3** (`androidx.wear.compose:compose-material3:1.6.x` —
  versioned separately from the phone compose-bom), minSdk 30, standalone
  (`com.google.android.wearable.standalone = true`).
- **Reused shared code** (`:domain` + `:data` + `:usecase` only — enforced by
  the `checkArchitecture` allow-map): `ButtonStateMachine`,
  `PushRemoteButtonUseCase` (auth-gated), `FirebaseAuthRepository`,
  `NetworkDoorRepository`, `CachedServerConfigRepository`,
  `NetworkRemoteButtonRepository`, `ButtonAckToken`, `DoorAnimation` /
  `GarageDoorGeometry` / `GarageDoorPalette` / `DoorAnimationMemory`.
- **DI**: `WearComponent` (kotlin-inject), mirroring `iosFramework`'s
  `NativeComponent` — platform deps via constructor, `@WearSingleton` scope,
  `WearComponentGraphTest` pins singleton identity with `assertSame`.
- **Wear-only implementations**: `InMemoryLocalDoorDataSource` (no Room —
  the watch shows live status only), `LogcatAppLoggerRepository` (no
  diagnostics DB), a copied `FirebaseAuthBridge` (identical to the phone's;
  hoisting both into a shared Android library is a follow-up), and
  `WearGarageIcon`/`GarageDoorCanvas` ports (the DrawScope execution is
  re-implemented per platform, like iOS; all constants stay in `:domain`).
- **ViewModel**: `WearHomeViewModel` owns the state machine wiring, the
  hold-to-confirm countdown (authoritative in the VM; the UI ring animation
  only mirrors it), and a **foreground-only refresh loop** — poll every 10s
  while visible, tightening to 2s while a press is waiting on the door so
  the machine's door-moved success detection fires promptly. No FCM, no
  background work, zero battery cost while the app is closed.
- **Auth**: Firebase Auth directly on the watch. Sign-in uses Credential
  Manager's Sign in with Google (`GetSignInWithGoogleOption`), the
  recommended Wear OS 5.1+ flow, feeding the same
  `SignInWithGoogleUseCase → FirebaseAuthRepository` chain as the phone.
  On watches without Credential Manager support the sign-in chip simply
  fails gracefully (token relay from the phone over the Data Layer is the
  documented fallback, not yet built).

## Build / CI integration

- `scripts/validate.sh` builds `:wearApp:assembleDebug` and auto-discovers
  `:wearApp:testDebugUnitTest`; CI's `build_debug` job assembles the Wear APK
  and the `unit_tests` aggregate runs its tests.
- `wearApp/google-services.json` is a copy of the phone's (same
  `applicationId` — Wear ships on the Wear form-factor track of the same
  Play listing). `release/decrypt-secrets.sh` produces it;
  `.github/actions/setup-android` drops the placeholder for secret-less
  (Dependabot) runs; it is gitignored.
- Secrets: same `SERVER_CONFIG_KEY` / `GOOGLE_WEB_CLIENT_ID` from
  `local.properties` (or `-P` properties in CI).

## Releasing (`wear/N` tags)

Mirrors the phone release model. Use `./scripts/release-wear.sh` — never
create or push `wear/N` tags directly (the guardrails hook blocks them).

```bash
./scripts/validate.sh                        # writes the validation marker
./scripts/release-wear.sh --check            # prints the exact next command
./scripts/release-wear.sh --confirm-tag wear/N
```

- **Version mapping:** tag `wear/N` builds versionCode `1000000 + N` (offset
  keeps Wear codes unique vs the phone's `android/N` codes in the shared
  applicationId). versionName comes from `wearApp/version.properties`; the
  script gates on a matching `wearApp/CHANGELOG.md` heading, same as the
  phone/Firebase gates.
- **Workflow** (`release-wear.yml`): builds + signs the Wear AAB on CI and
  always uploads it as a 1-day artifact (`wear-release-aab-<code>`). When the
  repo Actions variable `WEAR_PLAY_UPLOAD_ENABLED` is `'true'`, it also
  uploads to the Play **Wear internal** track (`tracks: wear:internal`,
  same pinned uploader action and service account as the phone).
- **One-time bootstrap (manual, Play Console):** the variable stays unset for
  the first release. Cut `wear/1`, download the artifact, then in Play
  Console: opt in to the **Wear OS form factor** (Setup → Advanced settings →
  Form factors), create a **Wear OS internal testing** release, upload the
  AAB, add testers. After it's accepted, run the `play-track-snapshot`
  workflow and check the track-state issue: the wear track's API name should
  read `wear:internal` — if it differs, fix `release-wear.yml` first. Then set
  `WEAR_PLAY_UPLOAD_ENABLED=true` (repo Settings → Secrets and variables →
  Actions → Variables) and every later `wear/N` release deploys automatically.
- The `play-track-snapshot` renderer resolves wear versionCodes back to
  `wear/N` tags, so the track-state log stays readable.
- **Release notes:** automated uploads send `distribution/wear-whatsnew/`
  (rolling, current release). For the one-time manual Console upload, paste
  the same text.

## Store listing assets (Wear)

Same two-location model as the phone assets (`/play-store-assets` skill):
`MobileGarage/screenshots/store/wear/` is generated staging;
`MobileGarage/distribution/playstore/wear/` is the curated set mirroring
what's uploaded in Play Console. Play's Wear screenshot rules: 1:1 aspect
ratio, ≥384px, up to 8, actual on-watch UI without device frames or
marketing overlays — the raw 454×454 emulator captures satisfy this as-is.

Captures come from the debug-only fixture screen
`wearApp/src/debug/.../ScreenshotStagesActivity` (renders `HeroScreenContent`
with canned states — no ViewModel, no network, no auth) on a Wear emulator:

```bash
adb shell am start -n com.chriscartland.garage.debug/com.chriscartland.garage.wear.debug.ScreenshotStagesActivity -e stage closed   # closed|armed|moving|open
adb exec-out screencap -p > wear-closed.png    # wait ~4s after am start; force-stop between stages
```

The four stages tell the hero story: closed ("Tap door to arm") → armed
(hold ring, "Hold door to press") → moving (mid-slide, up arrow, "Door is
moving") → open. Captured 2026-07-22 on a `wearos_large_round` API 34
emulator — which is also the app's first verified render on a round watch
canvas (TimeText, centering, palette, overlay badge all correct).

## Deliberately not included (follow-ups, in rough priority order)

1. **Real-watch verification.** The fixture screen has rendered correctly on
   a round API 34 emulator (2026-07-22, see § Store listing assets), which
   verifies layout/palette/typography on the round canvas — but the live app
   (real network, Credential Manager sign-in, Firebase-on-watch) has still
   never run on a physical watch. **Do not exercise the real remote button
   while verifying — it operates the physical door.** The signed-out app is
   inert (`PushRemoteButtonUseCase` gates on `Authenticated` before any
   network call), so UI/layout verification while signed out is always safe.
2. **R8 for the Wear release build.** Minification is deliberately OFF in
   the release build type — the phone needed hand-tuned keep rules for
   kotlinx.serialization (ADR-020) and there is no CLI way to verify a
   minified Wear build end-to-end yet. Fine for internal testing; enable +
   port the keep rules (and verify on a device) before any wider rollout.
3. **FCM push on the watch** (replace/augment polling; the shared
   `FcmRegistrationManager` + `MessagingBridge` seam already exists).
4. **Tiles + complications** — the natural Wear surfaces for door status
   (a complication showing OPEN/CLOSED; a tile with the door + one-shot arm).
5. **Ambient / always-on handling** beyond the default (currently the
   activity simply stops polling when hidden).
6. **Check-in staleness on the watch** (`CheckInStalenessManager` is shared
   and available; the door currently always renders the FRESH palette).
7. **Hoist the duplicated `FirebaseAuthBridge`** (phone + wear copies) into
   a shared Android library module.
8. **Phone-relay sign-in fallback** (Wearable Data Layer token relay) for
   watches without Credential Manager.
