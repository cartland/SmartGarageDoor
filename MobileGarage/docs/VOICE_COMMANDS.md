---
category: plan
status: active
---

# Voice commands for the door ("open the door" / "close the door")

Design exploration for tap-to-talk voice control on the phone (and later
the watch). Nothing here is implemented; this doc settles the API choice,
the matching strategy, the safety gate, and the phasing so implementation
can start from a reviewed baseline.

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
| `OPEN` | Reject: already open | **Accept** |
| `OPENING`, `CLOSING` | Reject: door is moving | Reject: door is moving |
| `OPENING_TOO_LONG`, `CLOSING_TOO_LONG` | Reject: door is moving | Reject: door is moving |
| `OPEN_MISALIGNED`, `ERROR_SENSOR_CONFLICT`, `UNKNOWN`, no event | Reject: can't confirm door state | Reject: can't confirm door state |

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
   same shared parser + gate.
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
- **The cancel window** (default 3s, adjustable 0.5–3s in 0.5s steps
  for playground experimentation) renders as a
  filling ring around the button with a countdown line ("Opening in 3 ·
  Tap to cancel"). Ring completion commits; the ~1s Sending state is
  not cancellable (a press cannot be unsent) and the button disables.
- **Nothing commits off-screen**: lifecycle stop or sheet dismissal
  cancels a pending (Armed) command.

Implementation (shipped 2.22.6): the state machine is
`VoiceCommandController` in `:usecase` (`Ready → Listening → Armed →
Sending → Sent/Failed/Ignored`), acting on a `VoiceCommandEnvironment`
(door state + `pressButton`). The Settings → Developer → **Voice
control** sheet (`VoiceControlBottomSheet`) wires the real controller to
`SimulatedVoiceCommandEnvironment` — an in-memory door with a pretend
button and fake transit, plus a segmented control to place the door in
any state to exercise every gate path. The playground never touches the
real door. Promoting the Home surface to the real thing (2.23.0) was
exactly the planned environment swap — the controller, gate, and tests
carried over unchanged.

**Home surface (shipped 2.22.9 in shadow mode; LIVE on the real door
since 2.23.0; developer-flag-gated):** the Home tab renders the voice
card (mic + countdown ring + stable two-line status) behind the same
per-user flag as Settings → Developer, signed-in only, fixed 3s window.
The gate reads the REAL observed door state — projected by
`VoiceDoorStateMapper` (clean terminals → actionable, clean transits →
MOVING, every anomaly [stuck too long, misaligned, sensor conflict] and
a **stale check-in** → UNKNOWN → refuse) — so refusals always match the
status card above. The projection is the safety mapping that closes the
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
  overriding the generator. Grow it freely — real device transcripts
  (copied from the playground) are the best source of new cases.
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
