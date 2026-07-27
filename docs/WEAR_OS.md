---
category: reference
status: active
last_verified: 2026-07-26
---

# Wear OS App (`MobileGarage/wearApp/`)

A standalone Wear OS app with a single hero experience: the animated garage
door with a 2-second hold-to-press remote button. Built on the same shared
KMP modules as the phone and iOS apps.

## The hero interaction

The screen is one animated door (same `:domain` `DoorAnimation` spec, geometry,
and palette as phone/iOS) with the door state label under it.

**One gesture: press and hold the door for 2 seconds.** A radial progress ring
sweeps around the bezel; when it completes, the press is submitted
(`SendingToServer → SendingToDoor → Succeeded` when the door actually moves).
Releasing early sends nothing and resets to the starting state.

**A tap does nothing at all.** There is no tap handler on the door — no arming
step, no armed state, no confirm-the-confirm. Only a hold that runs its full
duration can reach the real garage button.

Under the hood this still drives the shared `ButtonStateMachine` rather than
bypassing it. Finger-down sends the machine's first tap and starts the
countdown together, so `Ready → Preparing → AwaitingConfirmation` elapses
*under the user's finger* and its 500ms arming delay is never something the
user waits on or reads about. The countdown's completion sends the second
tap, which submits. An incomplete hold calls `reset()`, so
`AwaitingConfirmation` exists only while a finger is down and `Cancelled`
(reached via the machine's confirmation timeout) is unreachable here.

### Why this is safe against accidental presses

The old model used a separate arming tap as the guard. Hold-only replaces it
with two others:

- **Drift cancels the hold.** `GarageDoorTarget` abandons the gesture if the
  finger moves more than ~20dp. A deliberate thumb press is steady; the
  sustained accidental contact that would otherwise be dangerous (a sleeve, a
  wrist against a surface) wanders.
- **The hold announces itself.** Haptics fire at the start, midpoint, and
  completion of the 2 seconds, so an accidental hold is buzzing the wrist a
  full second before it could fire. That converts a silent countdown into an
  actively-noticed one.

### Haptics

Six one-shot cues, decided by the ViewModel and performed by the UI:

| Cue | When | Constant |
|---|---|---|
| `HoldEngaged` | finger lands | `GESTURE_START` |
| `HoldHalfway` | 1.0s, pacing cue | `CLOCK_TICK` |
| `PressCommitted` | 2.0s, press sent | `CONFIRM` |
| `HoldAborted` | released or drifted early | `GESTURE_END` |
| `PressSucceeded` | the door actually moved | `CONTEXT_CLICK` |
| `PressFailed` | server or door failure | `REJECT` |

Design notes worth not re-litigating:

- **Three discrete cues, not a continuous ramp.** At a fixed 2s there is no
  actionable quantity to convey, and wrist actuators are too coarse to read
  intensity as progress anyway. The silence between cues is load-bearing: it
  is what lets `PressCommitted` land with contrast instead of blending into a
  stream of ticks. A ramp would win at 4–5s or at a variable duration.
- **The last cue changes rhythm, not just intensity.** `CONFIRM` is a
  two-beat pattern, so "did it fire, or am I halfway?" is never ambiguous.
- **The midpoint is a pacing cue, not a point of no return.** Releasing
  cancels right up to the end. Moving the commit point earlier would give the
  haptic tidier semantics at the cost of a real safety property; not worth it.
- **`View.performHapticFeedback`, not `Vibrator`.** No `VIBRATE` permission,
  and it respects the watch's own touch-feedback setting — which is exactly
  why the ring, not the buzz, stays the authoritative channel. Every constant
  used exists at the app's `minSdk` (30), so no version guards.
- **A door someone else opened never buzzes.** `PressSucceeded` derives from
  a state only reachable after this watch submitted a press;
  `doorMovedWithoutOurPressEmitsNothing` pins it.

Cues are emitted as a `Channel`-backed `Flow`, not a `StateFlow` — they are
events, and conflation would drop one whose neighbour repeated. Modelling them
as ViewModel decisions rather than UI-inferred state transitions is what makes
them testable: a buzz cannot be asserted from the command line, but the cue
*sequence* can, and `WearHomeViewModelTest` asserts it for completed holds,
aborts before and after the midpoint, signed-out, success, and failure.

### Press-submitted feedback

At the moment of commitment the ring **completes and changes colour**, and
stays that way until the door responds (paired with "Sending" → "Waiting for
the door"). A brief flash at the commit instant was considered and rejected:
a state is more useful than a transient here, and unlike a 400ms animation it
is deterministically capturable by the `submitted` screenshot stage.

`WearHomeViewModelTest` pins the safety property from every direction —
signed out, released early, released one millisecond early, repeated aborted
holds, and touches landing while a press is already in flight or while a
terminal result is on screen. Verified to have teeth by mutation: shortening
the hold threshold fails 12 of its 19 tests.

### Which door states may predict what a press does

The button sends **one remote press** and the garage decides what that does —
open, close, or pause. Our door position is an interpretation of two sensors,
so the resting hint only promises an outcome where an affirmative sensor
reading backs it up:

| Door state label | Hint | Why |
|---|---|---|
| Closed | "Hold to open" | closed sensor affirms, open sensor does not contradict |
| Open | "Hold to close" | open sensor affirms, closed sensor does not contradict |
| Opening / Closing / Unknown / Sensor conflict / Connecting… | "Hold to press the remote" | no affirmative sensor; position is inferred from history |

`OPEN_MISALIGNED` is grouped with Open: it is a confident Open whose sensor
dropped out for under 3 seconds, it is well tested in the field, and the door
state label already renders it as "Open" — the hint must agree with the line
directly above it. That "key off the displayed label, never the raw
`DoorPosition`" rule is what keeps the two lines from ever disagreeing, and
`HeroScreenMappersTest` pins it across the whole enum (a newly added
`DoorPosition` defaults to not predicting until someone decides otherwise on
purpose).

## Voice demo (simulated — 0.3.0)

A second, deliberately-experimental surface: speak "open the garage door" and
watch the whole command loop run **against a fake door**. It never presses the
real remote button. This is the watch half of
[`MobileGarage/docs/VOICE_COMMANDS.md`](../MobileGarage/docs/VOICE_COMMANDS.md)
phase 3, shipped as a simulation first.

### Why a mic chip, and not the other triggers

| Candidate | Verdict |
|---|---|
| **Small mic chip on the hero screen** | **Chosen.** A separate target with a separate gesture, so the door keeps its single meaning. |
| Single tap on the door launches voice | **Rejected.** The door's tap is *deliberately* dead so only a continuous hold can reach the garage. A tap is also the opening frame of every hold and of every drift-cancelled accidental touch, so tap-to-talk would make a sleeve brush open a full-screen mic — re-creating the accidental-activation problem 0.2.0 removed, aimed at a new target. |
| Crown scroll opens a menu with a voice button | **Rejected for now.** The app is deliberately one hero screen; adding scroll navigation is a structural change, and it buries a demo two interactions deep. This is the natural home if the watch ever grows a third feature. |

The chip sits at `CenterEnd`, not the top: the top centre belongs to
`TimeText`, and at the vertical centre the round screen's chord is at its
widest, so a chip beside the door (which occupies only the middle 52%) clears
both the door and the mask without resizing anything. Signed-in only — the
signed-out screen has one job and has already been fixed once for overflow
(0.1.2).

Tapping it opens a dedicated screen rather than inlining the flow: the loop has
seven states plus a transcript and a countdown, which does not fit beside the
door, and the separation is itself a safety property — you cannot be looking at
the demo and think you are operating the real door. `SwipeToDismissBox` gives
the standard Wear swipe-back, with the hero screen composed underneath as the
background so returning does not re-run its cold-start fetch.

On the demo screen the **whole screen is the button** (0.3.1). A 52dp target is
a poor thing to have to hit to stop something already counting down, and the
controller's model is "a tap always means listen to me now", so the tap area may
as well be everything. No ripple (a full-screen one reads as a glitch) and
disabled while `Sending`, because a press cannot be unsent. The mic button is a
child, so its own click wins where they overlap, and gesture disambiguation
still lets a horizontal drag reach `SwipeToDismissBox`.

### Capture: in-app recognizer, system dialog as fallback (0.3.1)

`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` behaves differently on a watch than
on a phone, and the difference is visible to the user. On Wear the intent
resolves to **Gboard's `WearRemoteInputActivity`** — the general text-ENTRY
surface, not a one-shot recognizer. Its job is to produce text for an input
field, so it transcribes and then asks you to review/accept before returning.
That review step is the point of that activity, so **no `EXTRA_*` turns it
off**. Verify on any Wear image with:

```bash
adb shell pm query-activities --brief -a android.speech.action.RECOGNIZE_SPEECH
```

So 0.3.1 prefers `android.speech.SpeechRecognizer` (`WearSpeechCapture`), the
headless API underneath: results arrive on `RecognitionListener` callbacks, the
app draws everything, and speaking is one tap. Partial results feed a live
transcript line, without which replacing a rich system screen with a static
"Listening…" would be a downgrade.

Two things make this safe to prefer rather than mandate:

- **`RECORD_AUDIO` is requested on first mic tap, never at launch**, and
  declining falls back to the system flow. The demo always works; it is just
  slower. The microphone is never touched by the real remote button.
- **`SpeechRecognizer` needs a `RecognitionService`, which is not guaranteed.**
  `isRecognitionAvailable()` gates it, and the `wear_capture` emulator image has
  **no such service at all** — so this path cannot be exercised locally and the
  emulator always takes the fallback. The manifest needs a `<queries>` entry for
  `android.speech.RecognitionService` or Android 11+ package visibility makes
  `isRecognitionAvailable()` answer false on every device.

**Verification gap:** whether the confirmation step is actually gone, and
recognition quality, are only observable on a real watch. Everything else — the
state machine, the gate, partial-text handling, the fallback decision — is
CLI-tested.

### The listening state gets the whole screen (0.3.2)

"Is it hearing me?" has to be answerable without reading anything, so Listening
is a distinct full-screen layout rather than the normal one with a changed
label: a large filled mic with concentric rings pulsing outward from it, the
common idiom so it needs no explanation.

**The two phases are driven by the microphone, not faked.** `onRmsChanged`
supplies a live level (`VoiceLevel.normalize`, clamped to the nominal -2..10 dB
and exponentially smoothed — raw RMS arrives ~10x/second and jitters enough to
read as a flicker rather than a voice). Quiet: rings travel `IDLE_REACH_FRACTION`
of the way to the bezel at low opacity, so silence still shows life. Loud: they
reach the bezel, brighten, and the mic itself scales slightly. `VoiceLevelTest`
pins the arithmetic — an unclamped level would push rings off-screen.

`RING_COUNT` rings share **one** looping phase, offset evenly, so a single
animation produces a continuous procession rather than N staggered ones.

The level is UI-local state, not ViewModel state: it is continuous presentation
data with no decision attached, and routing ~10 updates/second through a
StateFlow would recompose the world for an animation. The ViewModel still owns
everything meaningful (the state, the transcript).

**Help text changes job partway through.** Before speech: one example command —
load-bearing rather than decorative, because the classifier accepts a narrow
imperative grammar, so the example is the difference between a command that
works and one that is refused. Once words arrive, that same slot becomes the
live transcript; the screen stops instructing the moment it has something to
reflect. Deliberately absent: the "Demo door" line (irrelevant mid-capture) and
any cancel hint (a tap during Listening is a no-op in the shared controller, so
advertising one would be a lie). The "Simulated" marker **stays** — a
full-screen animated mic is the frame most likely to be mistaken for a real
assistant, so it is the one that can least afford to drop the label. It sits
clear of `TimeText`, which owns the top arc.

### How it says "this is not real"

Three independent signals, because one is easy to miss on a glance:

1. A persistent **"Simulated"** marker at the top of the column.
2. **Conditional wording throughout** — "Would open the door", never "Opening"
   — and a terminal state that says outright that **nothing was sent**.
3. The door line is labelled **"Demo door"**, so the thing visibly reacting is
   never mistaken for the garage.

The demo door is stateful, which is what makes it worth demonstrating: commit
"open", watch it travel and settle Open, then say "open the garage door" again
and the gate refuses with "Demo door is already open". Every refusal path is
reachable by voice alone except `UNKNOWN`, which the simulation never enters.

### Why it cannot press the real button

Structural, not a runtime check, and pinned in three places:

| Guarantee | Pinned by |
|---|---|
| `WearVoiceViewModel` has no remote-button dependency at all | `WearVoiceViewModelTest.cannotReachTheRealRemoteButton` (reflection over the constructor) |
| The only `VoiceCommandEnvironment` in the Wear graph is the simulated one | `WearComponentGraphTest.theOnlyVoiceEnvironmentIsSimulated` |
| That environment's `pressButton` touches nothing but its own in-memory `StateFlow` | `SimulatedVoiceCommandEnvironmentTest` (`:usecase`) |

Two further layers show up if you try to break it: the kotlin-inject provider
constructs the ViewModel explicitly, and the test constructs it explicitly, so
adding a real-door dependency is a *compile* error in two files before the
reflection test even runs. Verified by mutation — all three fire.

Everything upstream of the press is the production path: the same
`VoiceCommandController`, the same `RuleBasedVoiceIntentClassifier` (Rules v3),
the same two-stage door gate, the same cancel window. Reimplementing a toy
would have demoed the toy.

### Voice haptics

Three more cues on the same `Channel`-backed flow, mapped in `WearHaptics`
alongside the hold cues so the two surfaces cannot drift:

| Cue | When | Constant |
|---|---|---|
| `VoiceArmed` | a command passed the gate; countdown starts | `GESTURE_START` |
| `VoiceCommitted` | the window elapsed (where a real press would go) | `CONFIRM` |
| `VoiceRefused` | classifier or gate said no | `REJECT` |

`VoiceCommitted` fires on `Sending`, not `Sent` — `Sent` arrives a fake
round-trip later and would put the buzz in the wrong place.

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
  no data yet — "Connecting…", no ⚠ badge) → `closed` ("Hold to open") →
  `inferred` (a position with no affirmative sensor, so the hint stops
  predicting: "Hold to press the remote") → `holding` (full ring, press
  about to fire, hint slot empty) → `submitted` (ring complete in the sent
  colour, "Waiting for the door") → `moving` → `open` ("Hold to close"),
  plus `signed_out` and `sign_in_error`. **When the hero screen gains a new
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
