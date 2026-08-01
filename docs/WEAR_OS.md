---
category: reference
status: active
last_verified: 2026-07-31
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
| `PressCommitted` | 2.0s, press sent — **emitted twice**, 110ms apart (0.3.6) | `CONFIRM` |
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
- **The commit is emitted TWICE (0.3.6).** Since the screen now marks that
  instant with a full-screen bloom, a single tick underneath read as an
  understatement. A wrist actuator cannot express "harder", so more is
  expressed as *again*. It rides on its **own job**, not `holdJob`: lifting
  your finger cancels `holdJob`, and on a completed hold that typically
  happens well inside the 110ms gap, so sharing the job would have let how
  fast you let go decide whether the press felt acknowledged.
  `theSecondCommitBeatSurvivesAnImmediateFingerLift` pins it, and inlining the
  beat back onto `holdJob` fails exactly that test.
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

Cues are emitted as a `Flow`, not a `StateFlow` — they are events, and
conflation would drop one whose neighbour repeated. Modelling them as ViewModel
decisions rather than UI-inferred state transitions is what makes them testable:
a buzz cannot be asserted from the command line, but the cue *sequence* can, and
`WearHomeViewModelTest` asserts it for completed holds, aborts before and after
the midpoint, signed-out, success, and failure.

**Backed by a `MutableSharedFlow(replay = 0)`, not a `Channel` (0.3.4).** A
Channel *queues* for an absent collector, so cues emitted while no screen was
subscribed were saved and replayed in a burst when one came back — a buzz
arriving seconds after the thing it describes. Combined with a demo that used to
keep running after being swiped away, reopening it buzzed twice for a command
abandoned earlier. Dropping is the right failure mode here: a missed buzz is
nothing, a late one is a lie. `cuesAreNotQueuedWhileNothingIsWatching` pins it,
and reverting the primitive fails it.

### The ring's four phases (0.3.6)

The ring is the only channel that reports whether a press happened, so what it
does at each moment is a correctness question, not a decorative one. It is
driven by `HeroRing.phaseFor(isHolding, buttonState)` — pure, and unit-tested
by `HeroRingTest`, because a Composable is not reachable from the command line
and "did it celebrate a press that never happened?" is exactly the kind of
claim that must be.

| Phase | When | What it draws |
|---|---|---|
| `Sweeping` | finger down | **Snaps to empty**, then sweeps over 2s on a faint track |
| `Settling` | gesture over, machine has not said which way | nothing changes — holds still |
| `Committed` | `SendingToServer` / `SendingToDoor` | bloom to a solid disc, then a slowly rotating gapped ring |
| `Idle` | anything else | **unwinds** the sweep back to empty over 450ms |

Four decisions worth not re-litigating:

- **A new hold snaps to zero; it never inherits.** Previously the sweep
  animated from wherever the last one had got to, so a hold begun during the
  previous release started part-full and promised a press sooner than the
  ViewModel's countdown — which always starts from zero — would deliver one.
- **An abandoned hold unwinds rather than vanishing.** The sweep is the only
  record that the gesture happened; watching it run backwards is what makes
  "nothing was sent" legible, where a disappearance reads as a glitch. 450ms,
  much shorter than the 2s it took to build: a retraction, not a second
  gesture.
- **`Settling` exists because `AwaitingConfirmation` is ambiguous.**
  `ButtonStateMachine.onTap()` and `reset()` both post to a Channel, so for a
  frame or two after the finger leaves that state is exactly as consistent
  with a completed hold as with an abandoned one. Neither answer may be drawn
  yet: unwinding would flinch on a press that succeeded, and blooming would
  claim a press that was never sent. Doing nothing is the honest option, and
  it costs one or two frames of delay on a genuine abort.
- **Only a real submission blooms.** The bloom keys off the two states
  reachable *only* by calling the server, never off the hold timer or off
  `AwaitingConfirmation`. `onlyARealSubmissionCommits` enumerates every other
  state; mapping `AwaitingConfirmation` to `Committed` fails it and
  `theAmbiguousFrameHoldsStillInsteadOfGuessing` together.

**Why the in-flight ring rotates.** It can be on screen for ten seconds or
more — the server has to answer and then the door has to physically start
moving. A static ring cannot distinguish "working on it" from "stalled"; a
rotating gapped one can, and it costs nothing because the screen is already
being held awake for exactly this window. The infinite transition is composed
**only** while a press is outstanding, since it renders frames for as long as
it exists and this is a watch.

The trade the previous static ring bought was determinism: the `submitted`
screenshot stage now captures an arbitrary rotation phase, so that PNG
legitimately differs between regens. The voice pulse stages already had this
property, and the 4s capture settle still guarantees the bloom (~720ms) is
over, so what is captured is always the steady in-flight state.

**Why the demo does not bloom.** 0.3.4 argued that the demo, whose whole job is
to rehearse the real interaction, should not teach a different vocabulary — and
that argument still holds for the *shape* of the countdown, which is why the
demo ring still completes and holds. It does **not** extend to the bloom. The
bloom's meaning is precisely "a real press has been sent to the real door";
that is why it is gated on `SendingToServer`/`SendingToDoor` and refused during
the ambiguous frame. Spending it on a surface that by construction sends
nothing would make it mean "something happened" instead, which is the same
class of dishonesty `RingPhase.Settling` exists to prevent — and it would
undercut the demo's own punchline ("Nothing was sent"). Rehearsing an
interaction does not require borrowing its receipt. Do not "fix" this
divergence without an argument that survives that point.

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

## Voice (live since 0.6.0; simulated 0.3.0–0.5.4)

Speak "open the garage door" and the whole command loop runs. This is the watch
half of
[`MobileGarage/docs/VOICE_COMMANDS.md`](../MobileGarage/docs/VOICE_COMMANDS.md)
phase 3.

It shipped as a **simulation** in 0.3.0 and stayed one for six releases, which
was the right order: the interaction was unproven on a wrist, and a fake door
made every refusal path reachable at a desk. 0.6.0 promoted it — the mic on the
door screen now presses the real garage button — and moved the rehearsal to
**Settings → Simulated voice**, where it remains permanently. Both run the same
loop; see [Two surfaces, one loop](#two-surfaces-one-loop).

### Two surfaces, one loop

| | Live | Simulated |
|---|---|---|
| Entry point | Mic chip on the door screen | Settings → Simulated voice |
| ViewModel | `WearLiveVoiceViewModel` | `WearSimulatedVoiceViewModel` |
| Environment | `RemoteButtonVoiceCommandEnvironment` | `SimulatedVoiceCommandEnvironment` |
| Door the gate reads | The real observed door | An in-memory one |
| A committed command | Presses the real button | Presses nothing |
| Ring colour | White | **Azure** |
| Header | Door line only | `SIMULATION` + "Demo door" |

Everything else — layout, timings, gestures, classifier, gate, cancel window,
haptics — is the shared `WearVoiceViewModel` base class. That is deliberate: a
rehearsal that looked or behaved differently from the real thing would teach
the wrong interaction, and two copies of the loop would drift.

**Placement is part of the design.** The rehearsal lives in Settings and not
beside the door, because the mic on the door screen is now the real control and
a pretend one must never sit where a hand reaching for the real one might land.

### Why a mic chip, and not the other triggers

| Candidate | Verdict |
|---|---|
| **Small mic chip on the hero screen** | **Chosen.** A separate target with a separate gesture, so the door keeps its single meaning. |
| Single tap on the door launches voice | **Rejected.** The door's tap is *deliberately* dead so only a continuous hold can reach the garage. A tap is also the opening frame of every hold and of every drift-cancelled accidental touch, so tap-to-talk would make a sleeve brush open a full-screen mic — re-creating the accidental-activation problem 0.2.0 removed, aimed at a new target. |
| Crown scroll opens a menu with a voice button | **Still rejected (0.5.0).** The crown now scrolls the settings list, and settings is a swipe away rather than a scroll away — but putting the mic *there* would bury it two interactions deep, which was the original objection and is unchanged. |

The chip sits at `CenterEnd`, not the top: the top centre belongs to
`TimeText`, and at the vertical centre the round screen's chord is at its
widest, so a chip beside the door (which occupies only the middle 46%) clears
both the door and the mask without resizing anything. Signed-in only — the
signed-out screen has one job and has already been fixed once for overflow
(0.1.2).

Since 0.5.0 it is the **only** chip on the hero screen; the settings entry point
that used to mirror it at `CenterStart` is gone (see below). The mic survived
that cull for a specific reason: what it opens is not a peer surface but a live
microphone, and a surface with that effect has to be entered deliberately rather
than arrived at by a stray horizontal swipe.

**Opening the demo starts listening (0.5.0).** Reaching it already takes a
deliberate press of the chip, so the `Ready` "Tap to speak" state was asking for
the same intent a second time — most of the interaction, on a device whose whole
proposition is to say two words and put your arm down. `Ready` is still reachable
and still worded the same way; it is now where a *finished* command lands rather
than where a new one starts. Entry is the only automatic trigger: a demo that
reopened the mic every time it finished would be a live mic nobody asked to keep
open. On a watch with no recognizer at all this surfaces the refusal ("No voice
input on this watch") immediately instead of after a tap, which is strictly more
useful.

Tapping it opens a dedicated screen rather than inlining the flow: the loop has
seven states plus a transcript and a countdown, which does not fit beside the
door, and the separation is itself a safety property — you cannot be looking at
the demo and think you are operating the real door. `SwipeToDismissBox` gives
the standard Wear swipe-back. Both ViewModels are resolved at the app root and
outlive either destination, so returning re-fetches nothing.

**The door's app-scoped effects live at the root, not on the hero screen
(0.3.4).** Foreground polling, the screen-wake window and the press-outcome
haptics are about *the app being in the foreground with something outstanding*,
not about the hero screen being visible. Hosting them inside `HeroScreen` made
all three quietly dependent on whether `SwipeToDismissBox` keeps its background
composed while the demo is on top — an implementation detail of the navigation
container that this app should not have an opinion about. If it does not, then
opening the demo mid-press would have stopped the very polling that detects the
door moving, and deferred the outcome buzz until you came back. They now sit in
`WearApp`'s `DoorSurfaceEffects`, which makes the behaviour identical either
way: a press you started completes, wakes the screen and buzzes, whichever
screen you are looking at.

**Leaving the demo ends the demo session (0.3.4).** A swipe-back is *not* a
lifecycle stop — the app stays perfectly foreground — so `onBackgrounded` never
fires for it and nothing else told the controller to stop. Walking away
mid-countdown therefore left the demo running behind the hero screen: it
committed off-screen, moved the demo door, buzzed the wrist for a command the
user had abandoned, and was still showing that outcome on the way back in. A
`DisposableEffect` now calls `WearVoiceViewModel.onScreenLeft` (which is
`onCancel`, so it stops `Listening` as well as `Armed` — the microphone must
not outlive the screen that opened it). `Sending` is left alone for the usual
reason, and the terminal states expire on their own.

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

### The tap rule (0.3.3)

**A tap starts what is not running and stops what is.** One sentence covers the
whole screen.

| State | Tap | Why |
|---|---|---|
| `Ready`, `Sent`, `Failed`, `Ignored` | start listening | nothing is running |
| `Listening` | **cancel → `Ready`** | universal mic-toggle convention |
| `Armed` | **cancel → `Ready`** | the on-screen label promises exactly this |
| `Sending` | inert | a press cannot be unsent |

This deliberately diverges from the phone, which uses
`VoiceCommandController.onMicTap` — a tap there means "listen to me now" and so
*restarts* from a pre-commit state, making the correction flow ("close… no
wait, open") one tap. That is right for a small on-screen button and wrong when
**the whole screen is the tap target**: a brush during the countdown would open
a live mic nobody asked for. Wear therefore calls the additive
`VoiceCommandController.onCancel` instead; nothing changes for `onMicTap`
callers. It also makes the existing "Tap anywhere to cancel" label literally
true, which it was not before.

**Cancelling must stop the recognizer too**, not just the controller. A
`LaunchedEffect` keyed on "is Listening" calls `WearSpeechCapture.cancel()` on
the way out, so the microphone can never stay live behind a screen that says it
is not listening. Harmless when the recognizer already finished on its own.

### One skeleton, every state (0.3.4)

Three anchors, and none of them moves: a **header** pinned to the top
("Simulated" plus the demo door), the **mic** on the screen's exact centre, and
a **text block** pinned to the bottom. Listening changes what those slots
contain — rings appear, the mic grows, the text becomes a live transcript — but
not where any of them is.

This replaced a vertically-centred column, whose height was a shared resource:
any line-count change moved everything else in it. Measured on the 454px
`wear_capture` emulator, the mic and the "Simulated" marker sat **18px higher**
on "Demo door is already open" (two lines) than on "Tap to speak" (one), and
moved again on the way to "Nothing was sent" — **three shifts per utterance**,
on exactly the states the user is reading. Reserving the tall slots pinned the
height too, but left blank gaps in the common case and could not fix the larger
problem: the listening takeover already used absolute anchors, so the two modes
disagreed about where the mic lived and entering one jumped.

Only the two variable text lines can now move, they move only when their own
text changes, and because the block is bottom-anchored they grow upward into
empty space rather than pushing anything. The gallery is where a regression
shows up: across every `voice_*` stage the header sits at y=65 and the resting
mic disc at 175–278.

Two consequences worth knowing before editing this screen:

- **The demo-door line moved into the header and is now visible while
  listening**, reversing its earlier "irrelevant mid-capture" rationale. It is
  the opposite of irrelevant — it is what decides whether the sentence you are
  about to say is accepted or refused, so the moment before speaking is when it
  is most worth reading. Keeping it there is also what frees the centre for the
  mic.
- **Bottom width and bottom padding are one decision, not two.** On a round
  screen the usable chord shrinks fast near the edge, so a wider block must sit
  higher. Sized by measurement: at 34dp of bottom padding the widest single
  line came out ~10px over the available chord and wrapped, orphaning a word.
  At 38dp / 0.72 every stage clears the mask by ≥25px. Re-measure after
  changing either.

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
works and one that is refused — plus the way out. Once words arrive, that same
slot becomes the live transcript and the cancel hint disappears entirely; the
screen stops instructing the moment it has something to reflect, and someone
mid-sentence is not looking for an exit.

The "Simulated" marker **stays** — a full-screen animated mic is the frame most
likely to be mistaken for a real assistant, so it is the one that can least
afford to drop the label. It sits clear of `TimeText`, which owns the top arc.

**The cancel hint was missing for a whole release, and its absence was a bug
rather than restraint (fixed 0.3.4).** It had been omitted on the grounds that a
tap during Listening was a no-op in the shared controller — true when written,
and false from 0.3.3, when a tap started cancelling. That left Listening as the
one state that could be escaped but never said so, which is the worse half of
the original problem: a user stuck in a noisy room watching rings pulse had no
visible way out short of swiping away the whole demo. It borrows `Armed`'s exact
wording, because it is the same gesture with the same effect and two phrasings
would imply otherwise.

**The hint sits ABOVE the live line, and that ordering is load-bearing.** The
block is bottom-anchored, so its last element is the one with a fixed position —
and the last element has to be the line the user is actually watching. With the
hint underneath, the prompt sat one line higher than the transcript replacing
it, so the first word spoken shunted the text down; and while the hint was
instead *reserved* as an empty line, the two stacked lines pushed the transcript
up into the mic. Above, it occupies empty space and then stops.

### The countdown ring completes and holds (0.3.4)

> **Superseded for the hero screen in 0.3.6** — see "The ring's four phases"
> above. The hero ring now blooms at commit and rotates while in flight. The
> demo ring still completes and holds, and that divergence is **deliberate**:
> see "Why the demo does not bloom" below.

The hero screen's hold ring has two jobs: it sweeps while counting, then holds a
*complete* ring in a different colour for as long as the press is outstanding.
That second job is deliberate — a state beats a transient, it pairs with the
text, and unlike a flash it is capturable by a fixture.

The demo's countdown ring only ever had the first job: it **vanished** at the
instant of commitment, which is the one instant a user most wants confirmed. For
a surface whose entire purpose is to rehearse the real interaction, teaching a
different vocabulary was a straight inconsistency. It now holds a complete ring
through `Sending`, with the faint track dropped (there is no longer any distance
left to go) — the same character change the hero screen makes. `voice_committing`
is the fixture stage; the state exists long enough to capture for exactly the
reason the hero screen's `submitted` does.

### The outcome stays up long enough to read (0.3.4)

`VoiceCommandController`'s `resultFlashMs` is now a constructor parameter,
defaulted to the shared 1.5s. That default is right on the real button, where
the outcome is a receipt for something the user just watched happen. It is wrong
here: "Nothing was sent" **is** the message of the whole demo, and it arrives
with a second line explaining that the demo door responds instead. Two lines of
new information on a wrist is not a 1.5-second read, so Wear passes the 4s that
refusals already get. `resultFlashDurationIsPerSurface` pins the parameter;
`theOutcomeStaysUpLongEnoughToRead` pins the Wear value end to end.

### How the simulation says "this is not real"

**Four** independent signals since 0.6.0, up from three, because the stakes
changed: before, everything on the watch was a simulation and the marker only
had to say so; now an almost-identical screen one swipe away really does open
the garage.

1. A persistent **`SIMULATION`** marker in the header. Since 0.3.4 the header
   is shared by every state, so it is present during the listening takeover
   too, not just at rest. Upper case is not styling — it has to survive being
   glanced at over a moving animation.
2. **An azure ring** instead of the real one's white (`WearRingColors.simulated`).
   This is the signal added in 0.6.0, and the one that carries the most: hue is
   what reads while the ring is sweeping and the words are not being read. It
   deliberately inverts the rule that gave the real ring its neutral greys —
   see `WearRingColors`' KDoc for why azure specifically (it is the one hue
   with no existing job; green and red are the door's, amber would read as a
   warning about the press).
3. **Conditional wording throughout** — "Would open the door", never "Opening"
   — and a terminal state that says outright that **nothing was sent**.
4. The door line is labelled **"Demo door"**, so the thing visibly reacting is
   never mistaken for the garage.

Signals 1 and 2 are the ones that work without reading, which is the case that
matters: the risk is not misreading the screen, it is not reading it.

The demo door is stateful, which is what makes it worth rehearsing against:
commit "open", watch it travel and settle Open, then say "open the garage door"
again and the gate refuses with "Demo door is already open". Reaching those
refusals on the live surface would mean genuinely cycling the garage.

### Why the rehearsal cannot press the real button

Structural, not a runtime check. **There is no `VoiceCommandEnvironment`
binding in the Wear DI graph** — each surface constructs its own literally, so
the two are separated by a declared type at the call site rather than by a
provider that could be re-bound.

| Guarantee | Pinned by |
|---|---|
| `WearSimulatedVoiceViewModel` has no remote-button dependency — nor `ObserveDoorEventsUseCase`, so it cannot even be *gated* on the real door | `WearSimulatedVoiceViewModelTest.cannotReachTheRealRemoteButton` (reflection over the constructor) |
| `WearLiveVoiceViewModel` **does** hold the button | `…Test.theLiveSurfaceDoesReachTheRealRemoteButton` |
| The two are distinct types, and the simulated one owns a simulated door | `WearComponentGraphTest.theTwoVoiceSurfacesAreDistinctTypes` |
| Every action-describing state is worded per surface | `VoiceStringsTest` |
| The fake's `pressButton` touches nothing but its own in-memory `StateFlow` | `SimulatedVoiceCommandEnvironmentTest` (`:usecase`) |

The second row is the one that is easy to leave out and shouldn't be: without
it, deleting voice control outright would make every other safety test in the
file pass *more* comfortably than before. A guard that gets happier as the
feature dies is not measuring the feature.

### What protects the live surface

The same four things that protect the hold-to-confirm button, from the same
places — voice is not a side door:

- **Auth.** `PushRemoteButtonUseCase` refuses before touching the network
  unless the session is authenticated (ADR-027). The mic chip is signed-in-only
  anyway.
- **The grammar.** Only a HIGH-confidence imperative arms. "Is the garage door
  open" contains every keyword and means the opposite of a command; it is
  refused.
- **The door gate, twice.** The real door is projected through
  `VoiceDoorStateMapper` (deny-by-default: every anomaly maps to `UNKNOWN`, and
  `UNKNOWN` refuses both directions). The gate is re-checked at commit, so a
  door that moves during the countdown — someone hitting the wall button —
  cancels the press instead of completing it.
- **The cancel window.** Three seconds, the controller's maximum, during which
  a tap anywhere on the screen calls it off. Leaving the screen or
  backgrounding the app cancels too, so nothing commits off-screen.

`WearLiveVoiceViewModelTest` walks each of these, asserting on
`FakeRemoteButtonRepository.pushCount` rather than on UI state — the question
is always "did a sentence reach the garage", never "did the screen look right".

One documented gap: the watch passes `isCheckInStale = false` to the mapper,
because `CheckInStalenessManager` is phone-only. That is not a claim the
reading is fresh; it is the absence of the phone's extra suspicion on top of
the mapper's own rules. Voice inherits exactly the exposure the hold-to-confirm
button already has, which is the right bar — both act on the same mirror.
Closing it means giving the watch a staleness signal, which is a change to the
door surface as a whole, not to voice. See `LiveVoiceDoor`.

The press is tagged: the ack token carries a `-voice` marker in the appVersion
slot, so server logs can tell a spoken press from a held one. (The server
compares the token for ack equality only — the format is opaque to it.)

### Voice haptics

Three more cues on the same kind of flow, mapped in `WearHaptics`
alongside the hold cues so the two surfaces cannot drift:

| Cue | When | Constant |
|---|---|---|
| `VoiceArmed` | a command passed the gate; countdown starts | `GESTURE_START` |
| `VoiceHalfway` | halfway through the cancel window | `CLOCK_TICK` |
| `VoiceCommitted` | the window elapsed (where a real press would go) | `CONFIRM` |
| `VoiceRefused` | classifier or gate said no | `REJECT` |

Each borrows the constant of the **hold** cue at the same point of the journey,
because both surfaces put a ring around the bezel and drive it from empty to
full — the same picture should feel the same whichever screen drew it.
`VoiceHalfway` was missing until 0.3.5, which left the identical-looking ring
silent in the middle, and left the *longer* of the two journeys (3s against the
hold's 2s) with the least to go on. Like `HoldHalfway` it is pacing, not a point
of no return: cancelling works right up to the end.

It is scheduled rather than derived from a state change — the midpoint of the
cancel window is not a state — and leaving `Armed` for any reason cancels the
pending tick, so a cancelled countdown never buzzes afterwards.

`VoiceCommitted` fires on `Sending`, not `Sent` — `Sent` arrives a round-trip
later and would put the buzz after the moment the press actually happened.

## Settings: a page beside the door (0.4.0, reshaped in 0.5.0)

Swipe **left** from the door to reach settings and **right** to come back. It
shows which account is signed in, the running build, a **Simulated voice** entry
(0.6.0 — the rehearsal, deliberately housed here and not beside the door), and a
**Check for update** button that opens this app's listing in the **watch's own
Play Store**.

It exists because the watch is updated far more often than it is configured, and
until 0.4.0 it could answer neither "what am I running?" nor "is there anything
newer?" without going through the phone. Wear OS does update apps by itself, but
on its own schedule, which is no help when the build you want was cut minutes
ago.

**`market://` is load-bearing and is NOT interchangeable with the phone's
`https://play.google.com/...` idiom.** On Wear OS the two resolve to different
apps. Measured with `adb shell cmd package resolve-activity` against a Wear OS 5
image:

```
market://details?id=…    -> com.android.vending/…WearMainActivity    (watch Play Store)
https://play.google.com/ -> …wearable.settings/…ResolverActivity     ("open on your phone")
```

So copying `ProfileContent`'s https form here would punt the user to their phone
instead of showing the Update button on the watch in their hand — the opposite
of the point. Verified end-to-end on the emulator: tapping the button focuses
`com.android.vending`.

Three further constraints, all encoded in `WearStoreLink`:

- **The package name is hardcoded** (`com.chriscartland.garage`), not read from
  `BuildConfig.APPLICATION_ID`. Debug builds carry `applicationIdSuffix =
  ".debug"`, so deriving it would open a "not found" page on exactly the builds
  a developer is testing with. The phone hardcodes `playStorePackageName` in
  `AppComponent` for the same reason.
- **Failure is caught, not pre-checked.** A pre-flight `resolveActivity` would
  report "no Play Store" on watches that have one, because Android 11 package
  visibility hides it from that query unless the manifest declares a matching
  `<queries>` element. Launching is not filtered that way, so the launcher tries
  and catches `ActivityNotFoundException`, reporting the result back so the
  screen can say so rather than leaving a button that looks broken. There is no
  Wear OS 2 fallback to write: `minSdk = 30` means every device that can install
  this app has the on-watch Play Store.
- **`BuildConfig.WEAR_TAG_NUMBER` is reduced to a BOOLEAN** (`isReleaseBuild`),
  never rendered. Through 0.4.0 the version row printed the tag it was cut from
  (`wear/15`); 0.4.1 removed that, because a `wear/N` tag means something against
  this repo's tags and the Play track log and nothing at all to someone wearing
  the watch, who already has the version number directly above it. A build with
  no tag still says **Local build**, because "this is not a release" and "this is
  an old release" are genuinely different answers and `versionName` cannot tell
  them apart.

Settings is reachable **whether or not you are signed in**. It is the one surface
whose value does not depend on a session: it exists to get a newer build onto the
watch, and a build broken enough to leave you stuck at the sign-in screen is
exactly when reaching the store matters most.

### Why it stopped being a chip and a leaf (0.5.0)

Through 0.4.x this was a leaf screen behind a three-dot chip at `CenterStart`,
mirroring the voice chip. Both parts of that were wrong for the platform:

- **`⋮` is a phone glyph.** It refers to an overflow menu, a convention Wear does
  not have, so it named nothing. Meanwhile it spent hero pixels advertising a
  destination the platform already has a gesture *and* a persistent visible
  indicator for.
- **A leaf is the wrong relationship.** Settings is not somewhere you finish and
  return from; it is the other half of the app. Peer pages say that; a pushed
  screen says the opposite.

So the door and settings are now pages of one `HorizontalPagerScaffold`, and the
voice surfaces are `SwipeToDismissBox` leaves — the live one entered from the
door's mic, the rehearsal from settings. Two axes, each meaning one thing:
**sideways is a peer, forward is a leaf.**

Voice stays a leaf rather than becoming a third page for a reason that got
sharper in 0.6.0: arriving on it opens a live microphone, and on the live
surface that microphone can end with the garage door moving. A surface with that
effect must be entered deliberately, never brushed into by a stray horizontal
swipe.

Three things about this composition are load-bearing:

- **`PagerDefaults.gestureInclusion` (the default) is what keeps swipe-to-dismiss
  working.** It reserves the left edge on page 0 so a swipe starting there is
  handed to the enclosing `SwipeToDismissBox` rather than eaten as a page change.
  Verified: a left-edge swipe on the door still leaves the app.
- **Rotary is explicitly `null` on the pager.** The crown belongs to whatever
  scrolls — the settings list — and a crown that paged instead would leave the
  app's one genuinely scrollable surface unreachable by the watch's main input.
- **The door is page 0** because it is why the app exists. Opening the garage
  must never cost a swipe, and a cold launch has to land on it every time.

### The list, and the crown

Settings is a `TransformingLazyColumn`, not a `Column` + `verticalScroll`. Two
reasons, and the second one was invisible:

1. A centred column is the wrong growth model for something meant to accumulate
   settings — items arrange outward from the middle, so anything added moves
   everything already there, and once it outgrows the screen the top is simply
   gone with nothing to suggest it. A list grows downward; adding a setting is
   one `item { }`.
2. **`Modifier.verticalScroll` has no rotary support.** On the app's *only*
   scrollable surface, the crown did nothing at all — and that is invisible in
   testing, because touch scrolling works fine. `TransformingLazyColumn` wires
   rotary itself, including the hierarchical focus rotary needs to reach it.

Confirmed empirically rather than by reading the API: inject `REL_WHEEL` on the
emulator's rotary node and watch the list move.

```bash
adb shell getevent -pl | grep -A4 rotary       # find the node (event12 here)
adb shell 'sendevent /dev/input/event12 2 8 -1; sendevent /dev/input/event12 0 0 0'
```

Components that wire rotary for you: `ScalingLazyColumn`,
`TransformingLazyColumn`, `Picker`, `AlertDialog`. Components that do **not**:
`Column` + `verticalScroll`, and `ScreenScaffold` (it draws the scroll indicator,
not the input handling).

**`EdgeButton` does not work on a pager page.** It is the Material 3 component
for a screen's single action and was the obvious choice for **Check for update**,
but the pager's page indicator owns the same bottom arc and does **not** fade
while the page is idle — measured on a 454px round emulator, still drawn nine
seconds after the swipe. They overlap. The action is a plain list row instead.

**Every list surface needs `SurfaceTransformation` + `transformedHeight`.** Wear
Material 3 applies neither for you, and without them a full-width row stays
rectangular and is cut by the round mask instead of shaping to it. Forgetting it
is a silent visual bug, not a compile error.

**Do not reach for `PaddingDefaults` to fix apparent edge crowding — it is
`internal`, and the premise is usually wrong.** `ScreenScaffold` already insets
about 15dp horizontally, which is more than the list spec asks for. The angled
ends a row grows near the top and bottom of the screen are the surface
transformation working, not clipping.

**The end of a list needs bottom content padding, or the last row parks on the
bottom arc.** Without it a list stops scrolling as soon as the last item is
technically on screen — which is against the bottom of the circle, where there
is least width, so the curve takes the ends off whatever rests there. This is
the one real "content hidden by the rounded corners" failure on a scrolling
Wear screen; the middle of the screen is full width and is fine.

**Use `Modifier.minimumVerticalContentPadding`, and take the value from the
component's own `*Defaults`.** This is the library's answer, and its KDoc names
the bug: the modifier exists *"to ensure that, when a list item is at the top or
bottom of the list, the distance from the item to the screen edge is sufficient
(such as to avoid the item being clipped by edges of a round screen)"*, with the
values *"expected to be provided by design systems, such as the recommended
values in Material3 `ButtonDefaults`, `CardDefaults`, `ListHeaderDefaults`"*.

| Component | Recommended value |
|---|---|
| `ButtonDefaults.minimumVerticalListContentPadding` | 0.23 × screen height |
| `ListHeaderDefaults.minimumTopListContentPadding` | 0.13 × screen height |
| `ListHeaderDefaults.minimumBottomListContentPadding` | 0.23 × screen height |

**It is per-item, not a bottom inset on the list, and that is deliberate.** The
list takes `max(its own contentPadding, what the edge item asked for)` — so it is
still one list-level padding, merely *computed* from whichever item is at the
edge. Three consequences: the amount can follow the component (a full-width
`Button` needs nearly twice a `ListHeader`), items in the middle cost nothing,
and because it is a *minimum* it composes with global screen insets instead of
fighting them — either side can raise the floor without knowing about the other.
It also survives the last item changing, which happens here whenever the
store-unavailable caption appears and displaces the button.

History, so it is not re-derived: `ScalingLazyColumn` solved this with
**`AutoCenteringParams`**; `TransformingLazyColumn` dropped that in favour of the
per-item modifier above. A hand-rolled `BoxWithConstraints` + fraction (what
0.5.3 shipped, at 0.35 of screen height) works but picks a number the design
system already specifies, and misses the top edge entirely.

**The failure is at the END, so do not "fix" it by narrowing rows.** Constraining
every row's width to survive the corners makes the common case worse — a
full-width email wraps mid-word in the *widest* part of the screen — to address
something that only happens at rest at the very bottom. Tried and reverted in
0.5.3; the regenerated gallery's top-of-list PNG being byte-identical is the
check that an end-of-list fix changed only the end.

The gallery has a **`settings_bottom`** stage for the same class of reason the
`bloom` stage exists: a settle-then-capture fixture only ever opens at scroll
position 0, so the screen's one action sits below the fold and would appear in no
screenshot at all. `WearSettingsScreen` takes an `initialAnchorItemIndex` that
production never passes.

## Telling the phone which build is on the wrist (0.5.2 + phone 2.23.6)

The phone's Settings → Watch row names the watch app's version ("Version 0.5.2
on your watch"), not just that it is installed.

**Nothing in the Wearable API reports another node's app version.** The
capability the watch advertises (`WATCH_APP_CAPABILITY`) is a boolean and carries
nothing else, so the watch has to volunteer the version. It writes a retained
`DataItem`; the phone reads its own local replica during the Settings status
poll it already runs.

**A DataItem, not a `MessageClient` RPC** — the opposite shape from the auth
relay, and for a specific reason. The relay asks a live question only a running
phone can answer. This is a fact that rarely changes, which the phone wants to
read at moments when the watch app is usually *not* running. The Data Layer
retains and replicates it, so the read works with the watch asleep or out of
range, and it costs no round trip. Data items are also owned by the app that
wrote them, so uninstalling the watch app removes this one — the phone cannot
end up naming a version that is gone.

**JSON bytes in `:data` commonMain, not a `DataMap`.** `DataMap` is an Android
type and would strand the protocol in `androidMain`, leaving writer and reader
free to disagree about key names. `WearAppInfoProtocol` is one codec both sides
must go through, the same reason `WearAuthRelayProtocol` is shaped that way.

**`WatchAppStatus.InstalledOnWatch.versionName` is nullable, and must stay
nullable.** A watch older than 0.5.2 publishes nothing, and the replica may
simply not have arrived yet. Both are "installed, version unknown" — a different
claim from any particular version — and the row falls back to "The app is on your
watch". Two Settings previews cover the two branches so they cannot drift into
looking interchangeable.

**Verifying a silently-failing Data Layer read.** `wear://*/…` (the wildcard
authority) returns an empty buffer rather than an error when it is wrong, so it
is worth proving rather than assuming. Temporarily add a read-back next to the
write, using the *reader's* exact URI construction, run it on the emulator, and
delete it:

```
WearAppInfo: published 0.5.1 (1000000)
WearAppInfo: TEMP-VERIFY read back count=1 value=WearAppInfo(versionName=0.5.1, versionCode=1000000)
```

That covers everything except the cross-node sync itself, which needs a real
phone+watch pairing.

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
  visual state, add a stage** — and add its one-line entry to
  `stage_description()` in the script, which refuses to write a gallery
  for a stage it cannot describe.
- **The settings fixture's `versionName` is PINNED** (`"0.6.0"` as of
  wear/23), not read from `BuildConfig`, for the same reason the emulator
  clock is pinned to 10:10: a live value would churn every settings PNG on
  every release. The cost is that it goes stale silently — it sat at
  `"0.5.0"` for three releases, so the store screenshots advertised the
  wrong version. **Bump it by hand at feature releases**; do not "fix" it
  into a live read.
- **Backticks inside a double-quoted `echo` are command substitution.** A
  gallery description containing `` `bloom` `` made bash run a command
  called `bloom`, print `command not found`, and silently drop the phrase
  from the generated README (fixed in #1184). Same silent-corruption family
  as the Konsist `file.name` and POSIX-ERE `\b` traps: the output looks
  plausible, so nothing draws attention to it.
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
   physical door.** Since 0.6.0 that applies to the **live voice surface**
   too — speaking a confident command into the door screen's mic and not
   cancelling within three seconds moves the real door. The signed-out app
   is inert on both paths (`PushRemoteButtonUseCase` gates on
   `Authenticated` before any network call, and the mic chip is
   signed-in-only), so signed-out exploration is always safe, as is
   Settings → Simulated voice at any time.
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
   Since 0.6.0 this also bounds the live voice gate: `LiveVoiceDoor` passes
   `isCheckInStale = false` because the watch has no staleness signal to
   pass, so voice inherits exactly the exposure the hold-to-confirm button
   already has — a door whose last known position is clean but whose device
   has stopped reporting. Wiring staleness fixes both at once.
7. **Hoist the duplicated `FirebaseAuthBridge`** (phone + wear copies) into
   a shared Android library module.
8. **True standalone auth** (no phone dependency). The per-call phone
   relay (shipped 0.1.3) requires the paired phone reachable at press
   time. A server-minted Firebase custom token could give the watch its
   own session after a one-time phone-assisted bootstrap — needs a new
   authenticated server endpoint; write the design (ADR) before building.
