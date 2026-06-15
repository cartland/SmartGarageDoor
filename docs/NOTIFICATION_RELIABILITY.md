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

Most fixes below are **proposed, not yet implemented**; rows marked ✅ have
landed in `main`, with per-row deploy status noted (server-side fixes reach
production only via a `server/N` deploy). When a fix ships, annotate its row
and bump `last_verified`.

## Findings summary

Severity reflects impact on the two goals above, not generic best practice.
Cosmetic best-practice gaps (channel naming, status-bar icon) are only listed
where they affect *delivery/surfacing* reliability.

| ID | Sev | Feature | Finding | Source |
|----|-----|---------|---------|--------|
| **R5** ✅ | High | Open-door notif | **Deployed in `server/30`.** Was at-most-once: a single dropped/failed send was never retried (dedup marker saved before send; send error swallowed). Now: send-before-save, so a failed send leaves no marker and the next 5-min tick retries. | `FirebaseServer/src/controller/fcm/OldDataFCM.ts` |
| **R6** | Med | Open-door notif | Foreground drop: a notification-payload message that arrives while the app is foregrounded is only logged, never shown. | `AndroidGarage/androidApp/.../fcm/FCMService.kt:62-64` |
| **R1** | Med | Push data | Missed-push recovery is manual: staleness is auto-detected but only raises a banner; nothing auto-refetches. | `AndroidGarage/usecase/.../CheckInStalenessManager.kt:54-104` |
| **R2** | Med | Push data | Runtime topic change unhandled: `FcmRegistrationManager.restart()` exists but nothing calls it (explicit `TODO`). | `AndroidGarage/usecase/.../FcmRegistrationManager.kt:78-90` |
| **M4** | Low | Open-door notif | No app-owned notification channel: the alert lands in FCM's fallback "Miscellaneous" channel, which the user can disable. | `AndroidGarage/androidApp/src/main/AndroidManifest.xml` (no `default_notification_channel_id`) |
| **M3** ✅ | Low | Open-door notif | **Fixed (dead code removed), deployed in `server/30`.** `TOO_LONG_OPEN_SECONDS` was duplicated, but EventInterpreter's copy was used only by `isEventOld`, which had **zero callers** — dead code. Removed the dead function + its constant; `OldDataFCM` keeps the single live copy. | `EventInterpreter.ts` |
| **M1** ✅ | Low | Push data | **Fixed.** `getFcmTopic()` wrapped the empty default in `DoorFcmTopic("")` and never returned null, so `fetchStatus()` reported an unregistered device as `Registered`. Now returns null for the unset default (also makes the misnamed `fetchStatusReturnsNotRegisteredWhenNoTopicSaved` test honest). Android-only — no deploy needed. | `FirebaseDoorFcmRepository.kt` |
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

**Status (2026-06-14):** implemented in `OldDataFCM.ts` — send-before-save
reorder, `return null` on send failure, plus a loud-log guard on the post-send
marker save (mitigation for tradeoff #1 below). Deployed to production in
`server/30`. The two failure-mode tests in `OldDataFCMFakeTest.ts` now lock
the new contract.

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

---

## Recommended sequence for the remaining fixes

Priority order for the still-proposed fixes (R6, R1, R2, M4), with the reasoning
worked out during the 2026-06 examination. None is urgent — the receive path and
the open-door gating are already solid (see "What's already solid"). All four are
**Android-only** (no server deploy; they ride the next `android/N`).

1. **R1 — auto-recover from a missed push (highest value).** This is the
   *primary* goal's (push-data) weak link: staleness is already detected
   (`CheckInStalenessManager`) but recovery is manual (the user must tap the
   stale-data banner). **Measure before building it:** turn on FCM delivery data
   (Firebase console → Cloud Messaging reporting, or the BigQuery delivery
   export) and reconcile against a client receive-log to learn the *actual*
   delivery rate. ~99.9% → R1 is polish; lower → R1 is urgent. The fix itself is
   a medium Android change: wire `isCheckInStale` to one debounced re-fetch with
   backoff, keeping the banner as the fallback.

2. **R6 + M4 together — foreground display + a dedicated channel.** They
   compound (a delivered alert is dropped in the foreground *and* lands in the
   user-silenceable "Miscellaneous" channel). Do both at once: build the
   notification in `FCMService.onMessageReceived` AND post it to an app-owned
   HIGH-importance channel (+ `default_notification_channel_id` / icon / color
   meta-data). This adds the most *new* code, so it ranks below R1. Notification
   display is **device-only** behavior — build a fixture-level verification
   signal per the "verify device-only behavior" rule in `CLAUDE.md` rather than
   deferring to a manual smoke.

3. **R2 — runtime topic change (defer).** Low likelihood: only when the device
   `buildTimestamp` changes while the app process stays alive (cold-start
   re-subscribes anyway). Wire the existing `FcmRegistrationManager.restart()`
   to a server-config `buildTimestamp` change when convenient.
