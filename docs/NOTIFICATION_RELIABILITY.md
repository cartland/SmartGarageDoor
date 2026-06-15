---
category: reference
status: active
last_verified: 2026-06-14
---

# Notification & push-data reliability

Reliability examination of the two FCM-backed features that matter most:

1. **Push data delivery** (primary goal) — the silent data FCMs that keep the
   app's door / button-health state in sync.
2. **Open-door notification** (secondary goal) — the user-visible alert that
   fires when the door has been open too long and isn't snoozed.

This doc is the canonical home for the **reliability findings and their proposed
fixes**. It does **not** restate the FCM architecture (topic format, payload
keys, data-only vs notification-payload split) — that lives in `CLAUDE.md`
§ "Safety Rules → FCM Push Notifications" and the `wire-contracts/` fixtures.
Read those first for *how it works*; this doc is *where it's fragile*.

All fixes below are **proposed, not yet implemented**. When one ships, move its
row to "Resolved" and bump `last_verified`.

## Findings summary

Severity reflects impact on the two goals above, not generic best practice.
Cosmetic best-practice gaps (channel naming, status-bar icon) are only listed
where they affect *delivery/surfacing* reliability.

| ID | Sev | Feature | Finding | Source |
|----|-----|---------|---------|--------|
| **R5** | High | Open-door notif | At-most-once: a single dropped/failed send is never retried (dedup marker saved before send; send error swallowed). | `FirebaseServer/src/controller/fcm/OldDataFCM.ts:63-87` |
| **R6** | Med | Open-door notif | Foreground drop: a notification-payload message that arrives while the app is foregrounded is only logged, never shown. | `AndroidGarage/androidApp/.../fcm/FCMService.kt:62-64` |
| **R1** | Med | Push data | Missed-push recovery is manual: staleness is auto-detected but only raises a banner; nothing auto-refetches. | `AndroidGarage/usecase/.../CheckInStalenessManager.kt:54-104` |
| **R2** | Med | Push data | Runtime topic change unhandled: `FcmRegistrationManager.restart()` exists but nothing calls it (explicit `TODO`). | `AndroidGarage/usecase/.../FcmRegistrationManager.kt:78-90` |
| **M4** | Low | Open-door notif | No app-owned notification channel: the alert lands in FCM's fallback "Miscellaneous" channel, which the user can disable. | `AndroidGarage/androidApp/src/main/AndroidManifest.xml` (no `default_notification_channel_id`) |
| **M3** | Low | Open-door notif | Threshold constant `TOO_LONG_OPEN_SECONDS = 15*60` duplicated in two files; can silently diverge. | `EventInterpreter.ts:28` + `OldDataFCM.ts:91` |
| **M1** | Low | Push data | `getFcmTopic()` never returns null (default is `""` wrapped in `DoorFcmTopic`), so `fetchStatus()` reports an unregistered device as `Registered`; downstream null-guards are partly dead. | `FirebaseDoorFcmRepository.kt:96-101`, default at `DataStoreAppSettings.kt:44` |
| **R3** | Info | Push data | Freshness rides a HIGH-priority per-check-in heartbeat FCM (not transition-only). Powers R1's detection, but is a battery cost and risks Android's high-priority background quota. Fails safe (staleness banner). | `EventUpdates.ts:91` |
| **R4** | Info | Push data | `onNewToken` only logs; doesn't re-subscribe. Usually fine (FCM migrates topic subs; cold start re-subscribes) but not guaranteed. | `FCMService.kt:41-43` |

### What's already solid (do not "fix")

- **Receive path fails safe.** Missing/malformed required fields → parser
  returns null, handler logs and drops (`FcmPayloadParser.kt:42,49,51`;
  `FcmMessageHandler.kt:67-70`). Unknown door `type` coerces to `UNKNOWN`
  (forward-compatible, `FcmPayloadParser.kt:43-47`). Empty payloads
  short-circuit (`FcmMessageHandler.kt:55`).
- **Persistence survives the service lifecycle** — FCM-arrived events insert on
  `externalScope`, not the cancellable `serviceScope` (`ReceiveFcmDoorEventUseCase.kt:51-56`).
- **Cold-start reconciliation** is idempotent and once-per-process
  (`InitialDoorFetchManager`).
- **Open-door gating is correct and tested** — live snooze read at send time
  (`OldDataFCM.ts:93-116`); guard clauses fail safe to "don't send"; covered by
  `OldDataFCMTest.ts`, `OldDataFCMFakeTest.ts`, `PubsubOpenDoorsJobTest.ts`.

---

## R5 — open-door alert is delivered at most once (worked example)

### What the feature is supposed to do

Every 5 minutes (`pubsubCheckForOpenDoorsJob`, `OpenDoor.ts:44`), the server
checks: is the door open > 15 min and not snoozed? If yes, send **one**
user-visible notification per open-door *episode* — deduplicated on the door
event's `timestampSeconds` so it doesn't re-fire every 5 minutes.

### The bug: order of operations in `sendFCMForOldData`

`OldDataFCM.ts:63-87` does three things in this order:

```
1. read last-notified record, compare timestamps          (:63-73)
   → if already notified for THIS event timestamp, return  (don't resend)
2. save "we notified for timestamp T"                      (:77)   ← marker committed
3. send the FCM                                            (:79)   ← .catch only logs; error swallowed (:84-86)
```

The dedup marker (step 2) is committed **before** the send (step 3) is
confirmed, and the send's failure is swallowed (`.catch` logs and the `await`
resolves normally regardless). So the record says "done" even when nothing was
delivered.

### Failure timeline

- **12:00** — door opens. Event timestamp `T`.
- **12:15** — tick fires; open 15 min, not snoozed → decide to send →
  **save marker(T)** → `send()` → FCM drops it (transient: connectivity, quota,
  server hiccup) → `.catch` logs, swallowed.
- **12:20** — next tick; door still open, same event, timestamp still `T` →
  dedup check sees marker(T) == current event `T` →
  *"Not sending duplicate notification"* → returns. **No retry.**
- **12:25, 12:30, …** — same. The marker permanently suppresses it.

The user is never told the door is open, and the 5-minute loop *looks* like it
would retry but the dedup defeats it. Effective reliability of this feature =
FCM's **single-attempt** rate, with zero recovery. It also compounds with
**R6**: even a delivered message is dropped if the app is foregrounded, and the
marker still blocks a resend.

### Proposed fix

Reorder so the marker is committed **only after a confirmed send**, and stop
swallowing the error:

```ts
try {
  const response = await firebase.messaging().send(message);
  console.log('Successfully sent message:', JSON.stringify(response));
  await NotificationsDatabase.save(buildTimestamp, data); // save ONLY on success
  return message;
} catch (error) {
  console.error('Error sending message:', JSON.stringify(error));
  return null; // no marker written → the next 5-min tick retries
}
```

The existing every-5-minute job then becomes a real retry loop: a failed send
leaves no marker, so the next tick tries again until one succeeds — while still
delivering only one notification per episode.

## R5 — tradeoff / risks of this fix

The reorder is not free. It trades one failure mode for another; decide
consciously which you prefer.

### 1. It swaps "silent miss" for "possible spam" (the core tradeoff)

The two orderings each have a failure mode:

| Ordering | Failure mode on error | Safe against |
|---|---|---|
| Save-before-send (current) | failed send → **under-notify** (silent miss) | spam |
| Save-after-send (proposed) | failed **save** → **over-notify** | silence |

The new failure mode: `send()` succeeds (user gets the alert) but the
subsequent `NotificationsDatabase.save()` throws → no marker → the next tick
sends **again** → repeats every 5 minutes until the door closes. Firestore
writes are highly reliable, so this is low-probability — but it's a *new*
failure mode, and an alert every 5 minutes is more visible than the bug being
fixed. `collapse_key: 'door_not_closed'` only partly mitigates it: it coalesces
messages while the device is **offline**, not repeated heads-up notifications to
an online device.

For a "your garage is open" safety alert, over-notifying is arguably the better
failure mode than silence — but that is a judgment call, not a free win.

### 2. "Success" means FCM *accepted* the message, not that the device received it

`firebase.messaging().send()` resolves with a message ID when FCM accepts the
message for delivery — it does **not** wait for the phone. So the retry loop
recovers only from *send-acceptance* failures (network to FCM, quota, malformed
message — exactly the swallowed-throw cases). It does **not** recover from FCM
accepting the message and then failing to deliver it (genuine best-effort
delivery loss). R5's fix is therefore narrower than it looks: it stops us
marking "done" when we never handed the message off, but it cannot guarantee the
user saw it. That residual gap is R6 / best-effort territory.

### 3. The marker update is still not atomic (pre-existing; not fixed, not worsened)

Scheduled pubsub triggers are at-least-once, so the job can occasionally
double-fire; two invocations can both read "no marker," both send, both save →
two notifications. Both orderings have this read-modify-write race today; the fix
neither introduces nor closes it. A fully robust version would do the read+write
in a Firestore transaction — out of scope for R5.

### 4. Mechanical follow-through (low risk, just work)

- **Return contract:** today a swallowed send-failure still returns `message`
  (looks like success). The fix returns `null` on failure so callers
  (e.g. `HttpCheckForOpenDoors`) don't report a failed send as sent.
- **Tests:** `OldDataFCMTest.ts`, `OldDataFCMFakeTest.ts`, and
  `PubsubOpenDoorsJobTest.ts` pin the current order. Flipping it requires
  rewriting those assertions and adding one for the new path
  (send-fails → no-save → next-tick-sends).

### Recommendation

Still worth doing — silent-miss on a safety alert is the worse failure for this
app. Frame it honestly as "we prefer a rare duplicate over a silent miss," not
"we made it reliable." If the spam risk (#1) is a concern, bound the downside
without returning to silence: cap retries per episode, or loudly log when
save-after-a-successful-send fails so the rare case is noticed.
