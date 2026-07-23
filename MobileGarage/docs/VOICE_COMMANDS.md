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

## Testing plan

- Parser: table-driven accept/reject test mirroring the table above.
- Gate: exhaustive `DoorPosition` × command matrix (sealed `when`, no
  `else`, so a new enum value forces a decision).
- State machine: confirmed-submit transition tests alongside the
  existing `ButtonStateMachineTest` timer-slot properties.
- VM: fake `SpeechInputBridge` drives Listening → outcome transitions.
- Device gap: recognizer quality only; all accept/execute decisions are
  CLI-verified.
