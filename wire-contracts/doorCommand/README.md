---
category: reference
status: active
last_verified: 2026-08-10
---
# doorCommand fixtures

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
