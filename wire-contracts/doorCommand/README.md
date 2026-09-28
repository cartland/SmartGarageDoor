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

## Compatibility across deploys

**The lanes are never in sync, and cannot be.** `server/N` reaches every caller
the moment it deploys; `android/N` / `ios/N` / `wear/N` are installs users take
whenever they take them. An installed watch talks to whatever server is live,
which may be older or newer than the app was built against. Skew here is not a
risk to be scheduled away — it is the permanent condition, so this endpoint is
designed to absorb it rather than to be released in lockstep. **There is no
version negotiation on this endpoint and none is wanted.**

**Every skew direction fails closed.**

| Skew | Path | Outcome |
| --- | --- | --- |
| Server does not recognise the command word | `parseDoorCommand` → `null` → **400** → `NetworkResult.HttpError` → `ActionError.NetworkFailed` → `SERVER_UNREACHABLE` | refused |
| Newer server invents a rejection reason | `KtorNetworkDoorCommandDataSource.parseRejection` → `DoorCommandRejection.UNRECOGNIZED` | refused |
| Newer server adds response fields | production `ignoreUnknownKeys = true` | decoded, extras ignored |
| Server unreachable, or not deployed yet | `NetworkResult.ConnectionFailed` → `SERVER_UNREACHABLE` | refused |

The command word itself is also case-insensitive on the server
(`parseDoorCommand` upper-cases before comparing), so `open` / `OPEN` / `Open`
are all accepted and a client that changed only the casing would not break.

**The property that makes this safe is additivity, not agreement.**
`VoiceCommandEnvironment.confirmWithServer` returns a refusal-or-`null` and has
no return value that can turn a locally-refused command into a permitted one
(see `RemoteButtonVoiceCommandEnvironment`). So a server this client does not
understand can only ever make it MORE conservative — never less.

The one branch that does not refuse is `ActionError.NotAuthenticated`, which
deliberately proceeds: being signed out is not a fact about the door, and the
press path's own auth gate refuses it without a network call. Pinned by
`HomeViewModelTest.voiceCommandCommitWhileSignedOutFailsWithoutPressing`.

### What the fixtures here do and do not prove

`verdict_table.json` is a test fixture read by both suites **in this repo at one
commit**. It proves the TypeScript and Kotlin implementations agree *as
authored*. It never travels over the wire, and it says nothing about a deployed
old client talking to a newer server.

Keep the two jobs separate when reasoning about a change:

- the fixtures stop you **authoring** a disagreement;
- fail-closed stops a disagreement **hurting** anyone.

Neither substitutes for the other. A green `DoorCommandWireContractTest` is not
evidence that a deployed client and a deployed server agree.

### Ordering rules for a change to this endpoint

- **A new direction is SERVER-FIRST.** Every older server 400s a command word it
  does not know, so a client sending one is refused until the server that
  understands it is live. Merge and *deploy* the server, then ship the client.
- **A new rejection reason is either order.** An older client maps it to
  `UNRECOGNIZED` and still refuses, worded generically — so the server may lead.
- **A rename is additive-then-retire, never a swap.** `DoorCommandWireContractTest`
  fails a one-commit rename, which is the intended outcome. If a rename is
  genuinely needed: add the new name, deploy a server accepting both, ship
  clients that send the new one, then retire the old name once no supported
  client sends it.
- **Never widen what is ACCEPTED to quiet a skew complaint.** The value of this
  endpoint is that it refuses; a permissive fallback for an unrecognised command
  would delete that value while leaving the tests green.
