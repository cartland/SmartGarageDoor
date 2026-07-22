---
category: reference
status: active
last_verified: 2026-07-21
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

## Deliberately not included (follow-ups, in rough priority order)

1. **On-device verification.** Nothing here has run on a watch or emulator —
   by design, this was built and verified entirely via CLI (compile + unit
   tests with fakes; nothing can reach the real button path in tests). The
   Wear Compose 1.6.2 M3 API usage compiles against stable APIs, but layout
   polish on real round screens, the Credential Manager sign-in flow, and
   Firebase-on-watch behavior all need a device/emulator pass before any
   release. **Do not exercise the real remote button while verifying — it
   operates the physical door.** The signed-out app is inert
   (`PushRemoteButtonUseCase` gates on `Authenticated` before any network
   call), so UI/layout verification while signed out is always safe.
2. **Release plumbing.** No `wear/N` tag flow, no Play upload workflow,
   versionCode hardcoded to 1. When wiring releases: Wear versionCodes must
   not collide with phone artifact codes (use a large offset), R8 must be
   enabled + ADR-020 keep rules ported (minification is off in this first
   cut), and the Wear track in Play Console needs the standalone APK.
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
