---
category: plan
status: active
---

# Voice commands for the door ("open the door" / "close the door")

Design exploration for tap-to-talk voice control on the phone (and later
the watch): the API choice, the matching strategy, the safety gate, and
the phasing.

**Status: shipped.** The design below is no longer a proposal — the
phone's Home surface has pressed the real door since 2.23.0 (behind a
per-user developer flag) and the watch ships a deliberate simulation.
Sections are annotated with what actually shipped where they diverge
from the original sketch; the earlier "nothing here is implemented"
framing was left behind by the 2.22.x–2.23.x rollout.

## The safety principle (drives every decision below)

> It is okay to incorrectly ignore commands. It is not okay to
> incorrectly execute a command.

Voice is a new *input* path to an existing physical action (the remote
button). Every layer is therefore deny-by-default: an utterance must pass
an exact imperative grammar, the door must be in the one terminal state
that matches the command's direction, the check-in data must be fresh,
and the user must be authenticated. Any failure at any layer is a
friendly visual rejection, never a retry-with-looser-rules.

## Decision 1: platform speech recognizers returning text, never raw audio

| Option | Verdict |
| --- | --- |
| Android `SpeechRecognizer` (in-app listening, text + confidence out) | **Phone choice.** Supports the wanted UX: small in-app mic button, live "listening" indicator (RMS callback), no system dialog. Needs `RECORD_AUDIO` runtime permission. Prefer on-device recognition where available. |
| `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (system speech screen) | **Watch choice** (phase 3). Watch-idiomatic full-screen system UI, no audio permission handling, returns top results as text. On the phone it fights the "small UI" requirement. |
| Raw audio (`AudioRecord`) + own/cloud ASR | **Rejected.** Enormous complexity, privacy, and battery cost with zero accuracy win for a two-command grammar. We never want the audio; we want two intents. |
| Assistant / App Actions integration | **Rejected.** Deprecated/shifting surface, no in-app tap-to-talk UX, and it moves the confidence decision outside the app — the opposite of the safety principle. |

## Decision 2: deterministic imperative grammar, no AI matching

The confidence requirement is the argument *for* determinism: an
allowlisted imperative grammar is the most conservative possible
confidence gate, is fully unit-testable, works offline, and can never
drift. AI fuzzy matching adds nondeterminism exactly where the spec says
"only act if confident" — it makes us less sure, not more.

Parser (`VoiceCommandParser`, pure Kotlin in `:usecase` commonMain):

1. Normalize: lowercase, strip punctuation, collapse whitespace.
2. Match the WHOLE utterance against:
   `^(please )?(open|close) the (garage door|garage|door)( please)?$`
3. Anything else returns `NotUnderstood`. No keyword spotting, ever.

Only the recognizer's **top-1** result is parsed (acting on alternative
N is acting on something the recognizer believes you probably didn't
say), and when the recognizer supplies confidence scores, a top-1 score
below threshold is `NotUnderstood` even if the text matches.

Accept/reject table (pinned by a table-driven unit test):

| Utterance | Result | Why |
| --- | --- | --- |
| "open the door" | OpenDoor | exact imperative |
| "please open the door" | OpenDoor | optional courtesy prefix |
| "close the garage" | CloseDoor | allowed object form |
| "close the garage door please" | CloseDoor | optional courtesy suffix |
| "can you open the door" | rejected | interrogative, not imperative — extra leading token fails the whole-utterance match |
| "don't open the door" | rejected | negation never matches because the grammar is exact-match, not contains-"open" |
| "open" | rejected | too short to be unambiguous |
| "open the door now" | rejected | trailing token; strictness is the feature |
| "open the pod bay doors" | rejected | wrong object |
| (silence / babble) | NoInput / NotUnderstood | recognizer timeout or no grammar match |

Question intonation is not visible in ASR text, which is why the design
rejects by *structure* (exact imperative match) rather than trying to
detect questions. If fuzzy matching is ever wanted, it must sit behind
this same parser contract with a calibrated confidence gate — the
deterministic grammar remains the floor. Not recommended now.

## Decision 3: the state gate lives in a shared UseCase

`HandleVoiceDoorCommandUseCase` (`:usecase` commonMain) receives the
parsed command and decides, reading the current door event and check-in
staleness at decision time:

| Current `DoorPosition` | "open the door" | "close the door" |
| --- | --- | --- |
| `CLOSED` | **Accept** | Reject: already closed |
| `OPEN`, `OPEN_MISALIGNED` | Reject: already open | **Accept** |
| `OPENING`, `CLOSING` | Reject: door is moving | Reject: door is moving |
| `OPENING_TOO_LONG`, `CLOSING_TOO_LONG` | Reject: door is stuck | **Accept** |
| `ERROR_SENSOR_CONFLICT`, `UNKNOWN`, no event | Reject: can't confirm door state | Reject: can't confirm door state |

`OPEN_MISALIGNED` moved out of the reject row in 2.23.2. It reads like an
anomaly but is not one: the server emits it only when the **closed sensor
reads NOT-closed** and the open sensor dropped out inside
`TOO_SHORT_DURATION_SECONDS` (`EventInterpreter.ts`), so the door is
definitively not closed — a confident Open with a flaky sensor. Rejecting it
meant a misaligned door could not be closed by voice, which is the case where
closing matters most. The wrong-direction hazard cannot arise (CLOSE is the
correct direction; OPEN is refused as already-open), it cannot mask a door that
has since closed (the server transitions straight to `Closed` the moment the
closed sensor trips, and the gate re-checks at commit), and every other surface
already treated it as Open — the status label, the hold hint, the door art.

The stuck transits became close-only in 2.23.8, by the same argument. They had
rejected both directions on the reasoning that a door which is mid-travel and
overdue has no honest terminal answer. True, but beside the point: a door
stopped partway is exactly when you want to send a press, because it may be
obstructed and need another go, and it is sitting open to the street while it
waits. `EventInterpreter.ts` reaches `OpeningTooLong` / `ClosingTooLong` only
after `Closed`, `Open`, and `ErrorSensorConflict` have each been ruled out — so
the closed sensor reads NOT-closed and the open sensor is not OPEN, and the
door is definitively partway. CLOSE is therefore a well-defined direction and
the wrong-direction hazard cannot arise. OPEN stays rejected: the door already
tried to open and did not get there, so a press is not "more open", and the
standing bias of every surface here is toward closed. `ERROR_SENSOR_CONFLICT`
keeps rejecting both — its sensors actively disagree, so unlike a stuck transit
there is no position to reason from at all.

These two states are the only ones where the gate's answer depends on the
direction for a reason other than "you asked for where it already is", which is
why they get their own `VoiceDoorState.STUCK` rather than borrowing `OPEN`.

### The server judges the same table, and the client asks it (`server/35`+)

`httpDoorCommand` (`FirebaseServer/src/functions/http/DoorCommand.ts`) answers
the same question server-side. It exists **specifically for voice**, because a
spoken sentence is the only input in this app that names a direction. It is
verdict-only: it has no import of the command collection the device polls, so
it cannot move the door.

**Since Android 2.23.9 / Wear 0.7.1 the voice loop consults it as a third
gate**, in this order:

1. local gate at arm time
2. local re-check when the cancel window elapses
3. **the server's verdict**
4. press, via the unchanged `addRemoteButtonCommand` path

It is strictly additive. `VoiceCommandEnvironment.confirmWithServer` returns a
refusal or `null`; there is no return value that turns a locally-refused command
into a permitted one, so asking can only ever refuse. A locally-refused command
short-circuits and never makes the round trip.

**An unreachable server refuses** (`SERVER_UNREACHABLE`). That costs little:
the press targets the same backend a moment later, so a server we cannot ask is
a press we could not have delivered. The check runs while the state is still
`Armed`, so it remains cancellable rather than adding a fourth visible state.

**Being signed out proceeds past this gate deliberately.** It is not a fact
about the door, and reporting it as one would send the user to look at the
garage instead of their account. The press path's own auth gate refuses without
any network call and words it as a failed send.

**The watch gains the most.** The server judges check-in staleness, which the
watch has never been able to judge for itself (`LiveVoiceDoor` passes
`isCheckInStale = false` because no such signal exists there). A watch with a
stale check-in now refuses commands that previously went through — the intended
effect, and the one user-visible behavior change.

The simulated surface implements the same method **locally and never touches
the network**, so the rehearsal keeps its no-route-to-the-garage property while
still refusing what the live surface would refuse.

**The button is not moving there, and should not.** The remote is a toggle —
one press, no direction — so the two-tap confirmation and the watch's hold have
nothing for a direction gate to judge. Routing them through it would refuse
valid presses: with the door open a tap is fine (it closes), while `OPEN` as a
*command* is correctly refused as already-open. Tapping stays on
`addRemoteButtonCommand` and remains the primary way to work the door.

The decision table is shared rather than copied:
`wire-contracts/doorCommand/verdict_table.json` is asserted by the server today,
and pointing this Kotlin gate's tests at the same file is the intended next
step — that is what would keep the two implementations honest. Note the server
also judges check-in staleness, which is the gap the watch cannot close for
itself (see below).

Additional gates, all typed rejections:

- **Staleness**: if `CheckInStalenessManager` says the sensor data is
  stale, reject ("can't confirm the door state right now") — a stale
  `CLOSED` might be a long-open door.
- **Auth**: acceptance funnels into the existing auth-gated press path;
  signed-out is inert exactly like the button.
- **TOCTOU**: the gate re-reads the current event immediately before
  submitting. The residual window (door starts moving between gate and
  server press) is the same one the manual two-tap flow has; the server
  press is a toggle either way, and the voice gate strictly shrinks the
  existing exposure.

On accept, the same `ButtonStateMachine` drives the press so the normal
in-flight/success/failure UI takes over. The machine gains one explicit
programmatic transition (`Ready → SendingToServer` via a
confirmed-submit event, unit-tested) — a spoken imperative *is* the
confirmation, so the two-tap arm/confirm is not synthesized. The watch's
stricter hold-to-confirm is about accidental *touches*; an exact spoken
imperative sentence has no comparable accidental trigger.

## UI (small, three states)

- **Idle**: a small mic icon button on the Home screen near the remote
  button (watch: a small mic chip on the hero screen, phase 3).
- **Listening**: pulsing mic (RMS-driven ring on the phone).
- **Outcome** (auto-dismiss ~2s, sealed `VoiceSessionState` in the
  screen's ViewModel per ADR-026):
  - Accepted: "Opening the door" / "Closing the door" → normal button UI
  - Already there: "The door is already open" / "already closed"
  - Moving: "The door is moving. Try again when it stops."
  - Unknown/stale: "Can't confirm the door state right now"
  - Not understood: "Didn't catch that. Try 'open the door'."
  - No input: "No speech heard"
  - Permission denied → standard permission prompt path

The speech capture itself is a platform bridge (`SpeechInputBridge`
interface in `:data`, Android impl over `SpeechRecognizer`, fake in
`:test-common`) so every decision layer is CLI-testable; only raw
recognition quality is device-only, and that is Google's code, not ours.

## Phasing

1. **V1 — shared logic, no UI**: `VoiceCommandParser` +
   `HandleVoiceDoorCommandUseCase` + `ButtonStateMachine`
   confirmed-submit transition, all with exhaustive tests. Shippable
   silently.
2. **V2 — phone UI** behind a per-user feature flag
   (`featureVoiceControl`, existing allowlist pattern in
   `docs/FEATURE_FLAGS.md`): mic button, listening indicator, outcome
   states, `RECORD_AUDIO` permission flow.
3. **V3 — watch**: `RecognizerIntent` from the hero screen, reusing the
   same shared parser + gate. **Shipped as a SIMULATION in Wear 0.3.0**: a
   mic chip beside the door opens a dedicated demo screen driving the real
   `VoiceCommandController` / classifier / gate against
   `SimulatedVoiceCommandEnvironment`, so it names the action it would take
   ("Would open the door") and then states that nothing was sent. It cannot
   reach the remote button, and that is structural — the ViewModel has no
   remote-button dependency, and the only environment in the Wear graph is
   the simulated one (three tests plus two compile-time barriers; see
   [`docs/WEAR_OS.md`](../../docs/WEAR_OS.md) § Voice demo). Promoting it to
   the real door is the same environment swap the phone already did
   (playground → `RemoteButtonVoiceCommandEnvironment` in 2.23.0).
4. **Later, maybe**: iOS via `SFSpeechRecognizer` behind the same
   bridge; fuzzy matching behind the parser contract if strict matching
   proves too brittle in practice (revisit only with real missed-command
   data, and keep the imperative-only rule).

## Command UX: cancel-window loop (confirmed design; simulated playground shipped)

The interaction design for the action loop was settled 2026-07-24 and
supersedes the sketches above where they differ (notably: the cancel
window replaces the `ButtonStateMachine` confirmed-submit idea as the
confirmation step, and v1 capture is `RecognizerIntent`, not a
`SpeechRecognizer` bridge). Confirmed decisions:

- **One button is the whole interface.** A tap always means "listen to
  me now": it starts over from any pre-commit state, cancelling a
  pending command and immediately re-listening (the correction flow —
  "close… no wait, open" — is one tap + re-speak).
- **Capture is `RecognizerIntent` for v1** (system dialog = the
  recording state; no mic permission). An in-app `SpeechRecognizer`
  icon-morph is a later polish option.
- **Only HIGH arms.** MEDIUM/UNKNOWN/no-speech/gate-blocked render a
  ~4s transient explanation chip (tap-to-copy the structured verdict,
  feeding the eval corpus), then auto-return to Ready.
- **The door-state gate runs twice**: at arm time (open only when
  closed, close only when open; moving/unknown refuse) and again when
  the cancel window elapses — a door that moved mid-countdown aborts.
- **The cancel window** is a fixed 3s on every shipped surface. It
  renders as a filling ring around the button with a countdown line
  ("Opening in 3 · Tap to cancel"). Ring completion commits; the ~1s
  Sending state is not cancellable (a press cannot be unsent) and the
  button disables. (2.22.7–2.23.2 exposed a 0.5–3s stepper in the
  playground for tuning; it was removed in 2.23.3 along with the
  playground itself, once the window was settled.)
- **Nothing commits off-screen**: lifecycle stop or sheet dismissal
  cancels a pending (Armed) command.

Implementation (shipped 2.22.6): the state machine is
`VoiceCommandController` in `:usecase` (`Ready → Listening → Armed →
Sending → Sent/Failed/Ignored`), acting on a `VoiceCommandEnvironment`
(door state + `pressButton`). Promoting the Home surface to the real
thing (2.23.0) was exactly the planned environment swap — the
controller, gate, and tests carried over unchanged.

**Simulated surface (Settings → Developer → Simulated voice; replaced
the two playgrounds in 2.23.3):** a rehearsal of the Home feature, not
an imitation of it. `SimulatedVoiceBottomSheet` renders literally the
same `VoiceControlCard` composable and the same `VoiceRecognizerEffects`
plumbing that Home does, driven by the same controller, classifier,
gate and 3s window. Exactly one thing differs: `DefaultProfileViewModel`
hands the controller a `SimulatedVoiceCommandEnvironment` (in-memory
door, pretend button, 10s fake transit) instead of the remote-button
one, so a commit moves only the pretend door. Sharing the card is what
keeps the two from drifting — there is only one of them.

That the sheet *cannot* reach the real door is structural, not a
convention: `DefaultProfileViewModel` has no `PushRemoteButtonUseCase`
at all, pinned by `SimulatedVoiceSafetyTest` in `:androidApp` (constructor
reflection, mirroring the watch's `cannotReachTheRealRemoteButton`).

Below the card sits the **verdict panel**: what the classifier made of
the last capture (transcript, intent, confidence, engine, outcome),
tap-to-copy in the structured format the eval corpus wants. It is
deliberately outside the card and below it — a developer tool, not part
of the feature being rehearsed, and it must never appear on the live
Home card.

The verdict is **latched** in the ViewModel (`VoiceVerdict`,
`ProfileViewModel.lastVoiceVerdict`) rather than derived from the live
command state, so it outlives the ~4s refusal flash. Deciding a
transcript is worth keeping takes longer than the flash lasts; the old
playground tied its copy affordance to the auto-dismissing `Ignored`
state, which made harvesting a race. It latches on both `Armed` and
`Ignored`, and reclassifies the transcript rather than reading those
states' own fields so the two yield the same shape (`Armed` carries no
confidence; `Ignored`'s classification is null for a no-speech capture).
The classifier is pure, so this cannot disagree with the verdict the
gate acted on. Pinned by
`ProfileViewModelTest.theVerdictOutlivesTheRefusalItExplains` and
`anArmedCommandAlsoProducesACopyableVerdict`.

What 2.23.3 removed, and why it is not a loss of coverage:
- **The transcription-only "Voice input" sheet.** Its whole job — see
  what the recognizer heard and how the classifier scored it — is now
  done by the verdict panel above, on the surface where you are already
  exercising commands. `RuleBasedVoiceIntentEvalTest` is where
  classifier accuracy is actually measured.
- **The playground's door-placement selector.** The pretend door reacts
  to commands, so the refusals stay reachable by using it: open it and
  ask again for `DOOR_ALREADY_OPEN`, speak mid-transit for `DOOR_MOVING`
  (`ProfileViewModelTest.thePretendDoorRefusesACommandItHasAlreadySatisfied`
  walks the first). The one path this cannot reach is
  `DOOR_STATE_UNKNOWN`, which the live Home surface reaches naturally
  via a stale check-in and which `VoiceCommandControllerTest` covers
  directly.
- **The cancel-window stepper**, per the settled fixed 3s above.

**Home surface (shipped 2.22.9 in shadow mode; LIVE on the real door
since 2.23.0; developer-flag-gated):** the Home tab renders the voice
card (mic + countdown ring + stable two-line status) behind the same
per-user flag as Settings → Developer, signed-in only, fixed 3s window.
The gate reads the REAL observed door state — projected by
`VoiceDoorStateMapper` (clean terminals → actionable, **misaligned →
OPEN** since 2.23.2, clean transits → MOVING, every genuine anomaly
[stuck too long, sensor conflict] and a **stale check-in** → UNKNOWN →
refuse) — so refusals always match the status card above. The projection is the safety mapping that closes the
wrong-direction hazard (stale cache says closed, door actually open,
"open" would really close). Since 2.23.0 a committed command presses
the REAL remote garage button: `RemoteButtonVoiceCommandEnvironment`
routes the press through `PushRemoteButtonUseCase` (the same auth-gated
path as the manual two-tap button) and mints a fresh ack token per
press, tagged `-voice` in the appVersion slot so server logs can tell
voice presses from manual ones (the server compares the token only for
ack equality — the format is opaque to it). The interim
`ShadowVoiceCommandEnvironment` (real state in, no-op press out) was
deleted with the promotion; end-to-end `HomeViewModelTest` coverage
pins that a commit presses the button with a voice-tagged token and
that refusals and signed-out commits never do.

## Testing plan

- Parser: table-driven accept/reject test mirroring the table above.
- Gate: exhaustive `DoorPosition` × command matrix (sealed `when`, no
  `else`, so a new enum value forces a decision).
- State machine: confirmed-submit transition tests alongside the
  existing `ButtonStateMachineTest` timer-slot properties.
- VM: fake `SpeechInputBridge` drives Listening → outcome transitions.
- Device gap: recognizer quality only; all accept/execute decisions are
  CLI-verified.

## Eval framework (shipped with the classifier, `:usecase` commonTest)

Beyond the grammar unit-test table, classifier engines are scored
against a gold corpus on a **strictness axis** — correctness here is
asymmetric, not pass/fail:

- **EXACT** (best) — the predicted tier equals what the gold label
  expects (UNKNOWN → ignored; recognizable-but-not-actionable → MEDIUM
  with the right direction; actionable → HIGH with the right direction).
- **STRICTER** (okay) — the engine under-committed: right direction at
  a lower tier, or ignored something recognizable. Costs recall, never
  safety.
- **LESS_STRICT** (bad) — the engine over-committed: a higher tier than
  the gold justifies, any direction claim on a gold-UNKNOWN utterance,
  or a wrong direction at any tier. The subset at HIGH is a **safety
  violation** and gates at ZERO — that assertion is never relaxed.

Plus **action precision** (of HIGH predictions, fraction that should
act — must stay 1.0) and **action recall** (of gold-actionable
utterances, fraction acted on — the price of strictness, reported so
grammar changes show their cost).

Pieces (all in `MobileGarage/usecase/src/commonTest/`):

- `VoiceIntentEval` — engine-agnostic harness; run any
  `VoiceIntentClassifier` against any corpus, get a `VoiceEvalReport`.
- `VoiceEvalCorpus` — 176 gold-labeled ASR-style transcripts across
  twelve lenses. Round 1 (2026-07-24, 87 cases): imperative, question,
  negation, asrnoise, smalltalk, indirect. Round 2 (same day, 89
  cases): a red-team pass ARMED WITH the engine internals —
  redteamnegation (negations outside the token list), wrongdoor (car/
  front/bedroom doors), reported (quoted speech), future (self-plans),
  highboundary (HIGH-grammar probes), asrnoise2. Both rounds: generator
  lenses, then two independent judge agents labeled every case from
  scratch; cases kept only with 2-of-3 consensus, judge consensus
  overriding the generator. Grow it freely — real device transcripts are
  the best source of new cases. Harvest them from the **verdict panel**
  under Settings → Developer → Simulated voice: speak at the pretend
  door, then tap the verdict to copy
  `input/intent/confidence/engine/outcome` straight into a corpus entry.
  Since 2.23.3 it is latched, so it waits for you instead of vanishing
  with the refusal flash.
- `RuleBasedVoiceIntentEvalTest` — runs Rules v2 over the corpus;
  hard-gates safety violations at 0 and pins the exact/stricter/
  lessStrict counts as baselines so any rule change's corpus effect is
  a deliberate, reviewed baseline update in the same PR.

Rules v3 baseline (2026-07-24, post red-team round 2): 148/28/0 of 176
(84%/16%/0%), safety 0, action precision 100%, action recall 35.4%.
Round 2 found one true safety violation in Rules v2 — "open my door"
matched the HIGH grammar via the possessive article but is not
unambiguously the garage door — plus 64 MEDIUM over-commitments
(wrong doors, reported speech, self-plans, no-token negations,
idioms). Rules v3 fixed all of them: possessives only with garage
objects in the HIGH grammar; expanded negation tokens (no/nobody/
cancel/quit/hold); reported-speech markers (third-person forms only,
"i want you to..." stays recognizable); self-plan markers (ill/gonna/
about/need); verb-follower, door-qualifier, and garage-follower
phrase checks. Zero lessStrict remain. The recall ceiling (35.4%) is
the deliberate strictness cost: all 28 STRICTER cases are compound/
preamble/adverbial imperatives held at MEDIUM — the ranked menu for
any future promote-to-HIGH decision.
