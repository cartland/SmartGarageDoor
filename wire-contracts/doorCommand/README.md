---
category: reference
status: active
last_verified: 2026-08-10
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
one document per response shape, pinned by
`FirebaseServer/test/functions/http/HttpDoorCommandTest.ts`. They lock the
`{ verdict, executed }` envelope the mobile clients decode.

**`verdict_table.json`** is not a response. It is the endpoint's *decision
table* — every door state crossed with every command, and the answer. It is here
rather than in the server's test directory because the rule it describes is
implemented twice: once in TypeScript (`controller/DoorCommandGate.ts`) and once
in Kotlin (`VoiceDoorStateMapper` + `VoiceCommandController.gateReason`). Two
implementations of one rule is exactly the situation this directory exists to
police, so the table is shared even though it never travels over the wire.

Today only the server asserts against it. Pointing the Kotlin gate's tests at
the same file is the obvious next step and the reason the fixture is shaped as
data rather than as prose.

## `executed` is always false

The endpoint reports a verdict and does not touch the door — see the file
comment on `FirebaseServer/src/functions/http/DoorCommand.ts`. If you are here
because you are adding execution, `executed: true` is a new fixture, not an edit
to an existing one: old clients must keep decoding the shape they were built
against.
