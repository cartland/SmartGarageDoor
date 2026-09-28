---
category: reference
status: active
last_verified: 2026-09-27
---
# doorCommand fixtures

## Who this endpoint is for

**Voice.** A spoken sentence names a direction — "open the garage door" is not
"close the garage door" — and that direction is the thing these fixtures judge.

**Not screen taps.** The remote button is a toggle: one press, no direction. The
phone's tap-to-confirm and the watch's press-and-hold mean "act on the door" and
keep going through `addRemoteButtonCommand`. They are not migrating here, and
the table below would refuse valid presses if they did: with the door open, a
tap is fine (it closes), while `OPEN` as a *command* is correctly refused as
already-open.

Two kinds of file live here, which is unusual for this directory — read the
distinction before adding a third.

**`response_*.json`** are ordinary wire fixtures, exactly like every other slug:
one document per response shape, pinned on the server by
`FirebaseServer/test/functions/http/HttpDoorCommandTest.ts` and on the client by
`MobileGarage/data/.../repository/DoorCommandWireContractTest.kt`, which feeds
the same bytes through a Ktor `MockEngine`. They lock the
`{ verdict, executed }` envelope the mobile clients decode.

Note the client test does **not** decode these strictly. The fixtures carry
`command`, `checkInAgeSeconds` and `sensorEventType`, which the client
deliberately ignores, so rejecting unknown keys would fail on the fixture rather
than on a rename. It pins decoded VALUES instead — and `response_accepted.json`
is the one that matters most, because every field of the client DTO has a
default, so a renamed `accepted` decodes to `false` and looks exactly like an
ordinary refusal.

**`verdict_table.json`** is not a response. It is the endpoint's *decision
table* — every door state crossed with every command, and the answer. It is here
rather than in the server's test directory because the rule it describes is
implemented twice: once in TypeScript (`controller/DoorCommandGate.ts`) and once
in Kotlin (`VoiceDoorStateMapper` + `VoiceCommandController.gateReason`). Two
implementations of one rule is exactly the situation this directory exists to
police, so the table is shared even though it never travels over the wire.

Both sides now assert against it — the server in `DoorCommandGateTest.ts`, the
Kotlin gate in `MobileGarage/usecase/.../VoiceGateVerdictTableTest.kt`. A third
reader was added later for a different question: `DoorCommandWireContractTest`
takes the rows' per-direction KEYS as the command vocabulary and asserts that
what the client sends is exactly that set. The watch relies on the server to
judge check-in staleness, which only holds if the request states its direction
in a word the server parses; that assertion is what keeps the two vocabularies
from drifting apart silently.

## `executed` is always false

The endpoint reports a verdict and does not touch the door — see the file
comment on `FirebaseServer/src/functions/http/DoorCommand.ts`. If you are here
because you are adding execution, `executed: true` is a new fixture, not an edit
to an existing one: old clients must keep decoding the shape they were built
against.
