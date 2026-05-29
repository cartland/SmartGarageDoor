---
category: reference
status: active
last_verified: 2026-05-29
---

# Snooze Behavior

Cross-component reference: how snooze actually works on the server, why it appears unreliable during `OPENING`/`CLOSING`, and what that means for what users can and can't expect.

The active design **couples each snooze to a specific door event timestamp**. This file exists so future-you (and any new collaborator) doesn't have to re-derive that from `git blame` the next time someone asks "why didn't snooze suppress that notification?"

## What snooze does

Snooze suppresses the server's **"door open too long"** notification — `OldDataFCM.sendFCMForOldData` — for a window the user chooses (1h, 4h, 8h, 12h). That's the only user-visible notification path the app sends; `EventFCM` is data-only (no `notification` field), so there's no per-state-change notification for snooze to also suppress.

## How a snooze is stored

When the user picks a duration and taps Save, the client sends two key fields:

- `snoozeDuration` (e.g. `"1h"`)
- `snoozeEventTimestamp` — the `lastChangeTimeSeconds` of the door event the client is currently displaying (`ProfileViewModel.kt:224`, `FunctionListViewModel.kt:169`).

Server (`SnoozeNotifications.ts:152-158`):

1. Read the current door event from `SensorEventDatabase.getCurrent`.
2. If `snoozeEventTimestamp` ≠ `currentEvent.timestampSeconds` → **reject with HTTP 404** `"Snooze event timestamp does not match current event timestamp"`.
3. Otherwise store the snooze with `currentEventTimestampSeconds = currentEvent.timestampSeconds` and `snoozeEndTimeSeconds = now + duration`.

## How a snooze is checked

Server `getSnoozeStatus` (`SnoozeNotifications.ts:49-127`):

1. Read the current door event.
2. Read the latest snooze record.
3. If `snoozeResult.currentEventTimestampSeconds !== currentEvent.timestampSeconds` → return **`NONE`** (`:100-107`). The snooze is effectively void from this moment on.
4. Otherwise compare `now` vs `snoozeEndTimeSeconds`:
   - `now > end` → `EXPIRED`
   - else → `ACTIVE`

`OldDataFCM.shouldSendFcmForOpenDoor` calls `getSnoozeStatus`:
- `ACTIVE` → do not send.
- `EXPIRED` or `NONE` → fall through to the normal 15-minute stuck-open check.

## Why snooze appears to "not work" during `OPENING` / `CLOSING`

Two coupled mechanisms collide and the result is deterministic, not random:

### Mechanism 1 — Submit-time race (`:152-158`)

The client reads `lastChangeTimeSeconds` when the snooze sheet opens and sends that exact value on Save. During a transition (CLOSED→OPENING→OPEN; OPEN→CLOSING→CLOSED), the event timestamp rolls over every few seconds. By the time the user picks a duration and taps Save, the door has probably advanced past it. Server rejects with 404. The client maps that to `ActionError.NetworkFailed` (a generic error) — the user sees a vague failure rather than "the door state changed before your snooze could apply."

### Mechanism 2 — `EventInterpreter` 60-second promotion (`EventInterpreter.ts:26, 194-197`)

```typescript
const TOO_LONG_DURATION_SECONDS = 60;
...
case SensorEventType.Opening:
  ...
  if (oldEventDurationSeconds > TOO_LONG_DURATION_SECONDS) {
    return OpeningTooLong(timestampSeconds);   // NEW event, NEW timestamp
  }
```

The ESP32 polls sensor state every few seconds. The next poll after the 60-second threshold causes `getNewEventOrNull` to write a brand-new event of type `OpeningTooLong` (or `ClosingTooLong` from the closing branch) with a fresh timestamp. This happens with no user action and no UI indication — the door icon just changes from the up/down arrow to the warning glyph.

If the user snoozed during the first 60 seconds of an `Opening` state that ends up stuck, the snooze record's `currentEventTimestampSeconds` is the original `Opening` timestamp. Within ~60 seconds, the `OpeningTooLong` promotion silently voids it (Mechanism 1's status check returns `NONE` from then on).

### Combined consequence

**There is no path by which a user can meaningfully snooze a stuck-`OPENING` or stuck-`CLOSING` door** with the current model. Either:

- they snooze inside the first 60s and the promotion voids it shortly after, or
- they snooze after the 60s mark and the submit fails with 404 because the client is still sending the pre-promotion timestamp.

This is exactly the scenario reported in 2026-05-28 ("I was opening but the open signal never arrived, and snooze didn't hold"). Not a regression — the `currentEventTimestampSeconds` check has existed since `3e73f7384` (2024-11-18).

## Practical guidance

- **Snooze works reliably only when the door is in a stable state** (`OPEN`, `CLOSED`, `OPEN_MISALIGNED`) AND the door doesn't change state for the duration of the snooze.
- **The first state change after a snooze voids it.** Snooze-while-OPEN, then close the door — the next CLOSED event invalidates the snooze. There is no carry-over.
- **In-motion snooze (`OPENING`/`CLOSING`) is functionally broken** by the 60s `TooLong` promotion. The UI accepts the action; the resulting snooze is either rejected at submit or void within a minute.

## Why the model exists (preserved-quirk note, do not "fix" without re-litigating)

Original intent — snooze should apply to **this instance** of door-open, not to a blanket "next N hours regardless." Each open-then-close cycle should re-arm the "you should be aware" notification on the next cycle. Event-coupling is the implementation of that intent. Decoupling snooze from the event would be an architectural change with its own UX trade-offs (e.g., a snooze set hours ago could silently suppress a notification for an unrelated open cycle).

Any redesign discussion belongs in a new ADR; don't unilaterally remove the timestamp check.

## Source pointers

| File | Line | What |
|---|---|---|
| `FirebaseServer/src/controller/SnoozeNotifications.ts` | `:100-107` | The timestamp-match check that returns `NONE` on mismatch. |
| `FirebaseServer/src/controller/SnoozeNotifications.ts` | `:152-158` | Submit-time 404 when client timestamp doesn't match current event. |
| `FirebaseServer/src/controller/EventInterpreter.ts` | `:26` | `TOO_LONG_DURATION_SECONDS = 60`. |
| `FirebaseServer/src/controller/EventInterpreter.ts` | `:194-197` | `Opening → OpeningTooLong` promotion that writes a new event. |
| `FirebaseServer/src/controller/fcm/OldDataFCM.ts` | `:93-117` | The single notification path snooze actually suppresses. |
| `AndroidGarage/usecase/.../SnoozeNotificationsUseCase.kt` | `:54-60` | Client passes `lastChangeTimeSeconds` of the displayed event. |
| `AndroidGarage/viewmodel/.../ProfileViewModel.kt` | `:222-225` | Snooze action call-site on the Settings page. |

## Verification

| Layer | Pins | Where |
|---|---|---|
| Server unit | `ACTIVE` / `EXPIRED` / `NONE` paths, including **NONE-when-event-advanced** (the "auto-void" case) | `FirebaseServer/test/controller/SnoozeNotificationsTest.ts` |
| Server unit | Per-state `Opening → OpeningTooLong` promotion logic | `FirebaseServer/test/controller/EventInterpreterTest.ts` |
| Server HTTP | 404 path when submit timestamp doesn't match current event | `FirebaseServer/test/functions/http/HttpSnoozeNotificationsRequestTest.ts` |
| Cross-system | The interaction (60s promotion silently voids ACTIVE snooze) | **Not pinned by a single test.** Inferred from the two layers above + this doc. If this becomes load-bearing, add an integration test that wires `EventInterpreter` + `SnoozeNotificationsDatabase` + `getSnoozeStatus`. |

## When to update this doc

- Anyone touches `SnoozeNotifications.ts`, `EventInterpreter.ts:26`, or `OldDataFCM.ts:93-117`.
- The client changes which event timestamp it sends as `snoozeEventTimestamp`.
- A typed client error for "snooze rejected due to event change" replaces the generic `ActionError.NetworkFailed` mapping (would be the natural Tier 2 follow-up).
- A redesign ADR lands that changes the event-coupling.
