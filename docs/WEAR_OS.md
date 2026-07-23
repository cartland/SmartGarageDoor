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
3. **Release early** → the ring resets, nothing is sent, and the button
   **stays armed**. Every touch anywhere on the screen (down and up, seen by
   a non-consuming Initial-pass observer) restarts the machine's
   confirmation timeout via `ButtonStateMachine.onUserInteraction()`, so
   the armed window counts from the **last touch**: partial taps and
   aborted holds keep it armed, and only ~8s with no touches disarms it
   (`Cancelled → Ready`). Because finger-down restarts the window, a hold
   started at the last instant always gets its full 2 seconds — the machine
   can never disarm mid-hold (pre-0.1.4, a late hold could visually
   complete its ring and then fire into an already-cancelled state).

A quick tap while armed never submits — it only keeps the window alive.
This is deliberately stricter than the phone's second-tap confirm, because
a watch face is much easier to touch accidentally. There is deliberately no
hard cap on how long touches can keep the button armed: the execute gate is
the continuous 2s hold, not the window length, and the quiet-period timeout
handles abandonment. `WearHomeViewModelTest` pins these safety properties
(stray tap never submits, early release never submits, signed-out is inert,
late hold still completes, quiet period disarms); `ButtonStateMachineTest`
pins that `onUserInteraction` extends only the armed window and never
disturbs the machine's other timers (it has a single timer slot).

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
- **Auth**: local-first with a phone relay fallback, composed in
  `RelayFallbackAuthBridge` (consumed unchanged by the shared
  `FirebaseAuthRepository`). Watch-local Credential Manager Sign in with
  Google is attempted via the Sign in button — but **GMS hard-rejects it on
  Wear OS** ("Google Identity Services do not support this Android
  Credential Manager API on Wear OS", captured via logcat on a Pixel
  Watch 4 / Wear OS 7 / GMS 26.28, 2026-07-22), so in practice auth comes
  from the **phone relay**: while signed out, the watch polls the paired
  phone over the Wearable Data Layer (`MessageClient.sendRequest` RPC,
  wire shape pinned by `:data`'s `WearAuthRelayProtocol` codec + tests);
  the phone's `WearAuthRelayService` (`:androidApp`, 2.21.0+) answers with
  the signed-in identity and a fresh Firebase ID token per call. Requires
  the phone reachable over Bluetooth/Wi-Fi for pushes — door *status*
  needs no auth. Poll cadence: 15s, only while signed out and the app
  process is alive.

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
- **One-time bootstrap — COMPLETED 2026-07-22:** `wear/1` (0.1.0 /
  versionCode 1000001) was uploaded manually in Play Console (Wear OS
  form-factor opt-in → Wear OS internal testing → full rollout), the
  `play-track-snapshot` log confirmed the track's API name is
  `wear:internal`, and `WEAR_PLAY_UPLOAD_ENABLED` was set `true`. From
  `wear/2` on, every release deploys automatically. (Recovery note: if the
  variable is ever unset, releases fall back to artifact-only with a
  manual-upload notice — same behavior the bootstrap used.)
- **Watch visibility requires a SEPARATE app-level Wear OS opt-in + Google
  quality review** (Advanced settings → Form factors → Wear OS → opt in and
  agree to the review policy). A rolled-out release on `wear:internal` is
  NOT enough: until the review approves, the Device catalog shows every
  watch as "Not opted in" and the app is hidden from watch Play Stores
  **even for internal testers** (and the phone's remote-install picker
  won't offer watches). Discovered empirically 2026-07-22 — the wear/1 and
  wear/2 releases were live on the track for a day while the Pixel Watch 4
  couldn't see the app; the opt-in was submitted the same day. The Wear
  store-listing screenshots (`distribution/playstore/wear/`) satisfy the
  review's asset requirements. **Outcome: approved same-day (2026-07-22)**
  — the app then surfaced on the watch Play Store and 0.1.1 was installed
  on a Pixel Watch 4 (Wear OS 7), the app's first run on real hardware.
- The `play-track-snapshot` renderer resolves wear versionCodes back to
  `wear/N` tags, so the track-state log stays readable.
- **Release notes:** automated uploads send `distribution/wear-whatsnew/`
  (rolling, current release). For the one-time manual Console upload, paste
  the same text.
- **Troubleshooting "the watch can't see the app"** (diagnostic order from
  the 2026-07-22 bootstrap): (1) the **Device catalog** is authoritative —
  search the watch model; "Not opted in" means the form-factor opt-in/review
  gate above, "Excluded" states the exact reason (API level, feature); (2)
  the phone Play Store's remote-install picker offering *other* devices but
  not the watch points at compatibility/opt-in, not propagation; (3) verify
  the artifact's declared requirements from the local APK with
  `aapt2 dump badging wearApp/build/outputs/apk/debug/*.apk` — expect
  `uses-feature: android.hardware.type.watch` and nothing else exclusionary;
  (4) watch account must be the tester account (watch Settings → Accounts);
  (5) Wear OS 2 watches (pre-2021, API < 30) are excluded by design
  (minSdk 30); (6) only after all that, suspect watch Play Store
  propagation/caching (up to ~24h; reboot or clear Play Store cache).

## Screenshots (gallery + store assets)

**One fixture, one script, one committed gallery.** At the current scale
(one hero screen) there is deliberately no second rendering stack: the
screenshot gallery and the Play Store staging set are the same artifacts,
captured from a real Wear emulator by a single script.

```bash
./scripts/generate-wear-screenshots.sh
```

- **Fixture** — `wearApp/src/debug/.../ScreenshotStagesActivity` is the
  single enumeration of capture-worthy states (renders `HeroScreenContent`
  with canned values — no ViewModel, no network, no auth, no path to the
  real door). Eight stages tell the whole story: `connecting` (cold start,
  no data yet — "Connecting…", no ⚠ badge) → `closed` → `armed` →
  `holding` (full ring, press about to fire) → `moving` → `open`, plus
  `signed_out` and `sign_in_error`. **When the hero screen gains a new
  visual state, add a stage** — that is the whole maintenance contract.
- **Script** — creates/boots the `wear_capture` AVD headless
  (`wearos_large_round`, 454×454, `system-images;android-34;android-wear`;
  self-installing, needs cmdline-tools 13114758+ for SDK XML v4), builds +
  installs the debug APK, pins the emulator clock to **10:10** (best
  effort via `adb root`, so TimeText doesn't churn every PNG on regen —
  the wear analog of the iOS fixed-clock rule), captures every stage with
  `force-stop` between, sanity-checks sizes, and regenerates the gallery
  README. Per stage it waits for the fixture's **window focus**
  (`dumpsys window mCurrentFocus` naming the activity class) before the
  settle sleep — both the system launch splash ("Starting…") and the
  charging overlay are windows over a technically-RESUMED activity, so a
  fixed sleep or a resumed-activity check race them; the launch is
  re-issued every few seconds to climb back over any overlay. On success
  the warm emulator is **left running** (cold boots are the flakiness
  source; the script reuses a running instance on any port) — kill it
  manually with `adb -s <serial> emu kill` when done.
- **Committed** — `MobileGarage/screenshots/store/wear/` (PNGs +
  `README.md`; that README *is* the wear screenshot gallery, the analog of
  `SCREENSHOT_GALLERY.md`). **Manual** — copying the curated live subset
  to `MobileGarage/distribution/playstore/wear/` (no generator writes into
  `distribution/`; `/play-store-assets` skill). Play's Wear rules — 1:1,
  ≥384px, up to 8, real UI, no frames/overlays — are satisfied by the raw
  454×454 captures as-is.
- **When to run** — on demand: whenever a PR visibly changes the hero
  screen, and before store-asset updates. Deliberately NOT in CI (emulator
  boot is slow/flaky; the posture is regenerate-don't-assert with the PR
  diff as the review surface). With the clock pinned, static stages are
  **byte-stable across regens** — a diff means a real visual change —
  with one exception: `moving` captures the door mid-slide of the 12s
  animation, so the focus-wait's ±1s timing variance shifts its door
  position slightly between runs (cosmetic churn; accept it). `holding`
  is stable: `animateFloatAsState` initializes at its target on first
  composition, so the static fixture renders the ring already full
  (mid-sweep is not capturable from a static fixture; the full ring is
  the deterministic "press about to fire" illustration).
- **Graduation (directed, not just a tripwire)** — the maintainer's
  standing direction (2026-07-22) is that these images should come from
  the screenshot-testing libraries, not an emulator: add a Layoutlib
  PR-time tier — AGP's screenshot-test plugin on `:wearApp` with a
  `screenshotTest` source set importing the previews (flip them
  `private` → `internal`), folded into `generate-android-screenshots.sh`
  — the phone model, including its local blank-render caveat (references
  render in CI). The emulator script then narrows to store assets +
  animation states only, or retires entirely if Play accepts the
  Layoutlib renders.

## Deliberately not included (follow-ups, in rough priority order)

1. **Functional pass on the real watch.** The app has run on a physical
   Pixel Watch 4 (Wear OS 7) since 0.1.1 (2026-07-22), and sign-in WAS
   exercised on-device — that run is what proved GMS rejects Credential
   Manager sign-in on Wear OS and motivated the phone relay
   (§ Architecture). Still unconfirmed on-device: the phone-relay sign-in
   end-to-end (needs the watch on 0.1.3+ AND the paired phone on 2.21.0+
   from the internal tracks), live door status accuracy, the foreground
   refresh cadence, and the tap-to-arm → hold-to-confirm press. Only the
   maintainer can run that last one: **the remote button operates the
   physical door.** The signed-out app is inert (`PushRemoteButtonUseCase`
   gates on `Authenticated` before any network call), so signed-out
   exploration is always safe.
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
8. **True standalone auth** (no phone dependency). The per-call phone
   relay (shipped 0.1.3) requires the paired phone reachable at press
   time. A server-minted Firebase custom token could give the watch its
   own session after a one-time phone-assisted bootstrap — needs a new
   authenticated server endpoint; write the design (ADR) before building.
