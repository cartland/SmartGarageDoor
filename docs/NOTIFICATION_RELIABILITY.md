---
category: reference
status: active
last_verified: 2026-06-21
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
| **R6** ✅ | Med | Open-door notif | **Fixed in Android `2.20.0` (merged, not yet released).** Was a foreground drop: a notification-payload warning arriving while the app was foregrounded was only logged. Now `FCMService.onMessageReceived` renders it via `DoorNotificationPresenter.showWarning(...)` on the app-owned "Garage door" channel/slot. | `AndroidGarage/androidApp/.../fcm/FCMService.kt` (notification block) |
| **R1** | Med | Push data | Missed-push recovery is manual: staleness is auto-detected but only raises a banner; nothing auto-refetches. | `AndroidGarage/usecase/.../CheckInStalenessManager.kt:54-104` |
| **R2** | Med | Push data | Runtime topic change unhandled: `FcmRegistrationManager.restart()` exists but nothing calls it (explicit `TODO`). | `AndroidGarage/usecase/.../FcmRegistrationManager.kt:78-90` |
| **M4** ✅ | Low | Open-door notif | **Fixed in Android `2.20.0` (merged, not yet released).** The warning no longer lands in FCM's "Miscellaneous" channel: manifest `default_notification_channel_id`/`default_notification_icon` route the OS-rendered background warning to an app-owned "Garage door" HIGH channel + garage icon (created eagerly at startup); the foreground warning + resolved render on the same channel. Channel id is a shared string resource (no manifest/code drift). | `AndroidManifest.xml` + `DoorNotificationPresenter.kt` + `GarageApplication.kt` |
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

## Failure-mode posture: miss vs. duplicate (the design asymmetry)

The two failure modes are **not** equal in stakes, and the right bias **flips**
between the two notification types. This is the principle behind why the warning
and the resolved are built differently.

- **Open-door warning = safety alert → bias toward *sending*.** A silent miss means
  "your garage is open and you don't know" (theft / weather / safety) — far worse
  than a duplicate. That's why R5 changed it from at-most-once to **at-least-once
  with retry**. *But* over-notifying has a tail risk: alert fatigue → the user mutes
  the channel → then misses a *real* future warning. So the target is "deliver
  reliably, then dedup/collapse to ~one per episode," not spam.
- **Resolved = reassurance → bias toward *not firing when unsure*.** Both outcomes
  are low-stakes (the door is safely closed either way), but a **false/spurious
  resolved** ("open for 9 hours" when nothing happened) is worse than a missed one,
  because it **erodes trust in the warning that actually matters**. Hence Phase 1's
  heavy "only when certain" guards: fire only if a warning was sent, consume the
  marker on close (single-use), 7-day stale-marker cap.
- **They're coupled through trust.** Notifications are a trust channel; cry wolf and
  the user silences *everything*, including the safety-critical warning. So the
  correctness of the *informational* resolved protects the reliability of the
  *critical* warning.
- **Push data** (silent state-sync, not user-visible): a miss → stale door state.
  Posture: **fail *visible*** — the staleness banner flags it rather than silently
  showing wrong state (auto-recovery is R1, still manual). Duplicates are harmless
  (idempotent upsert; only wasted wake-ups — R3/battery).

Net rule of thumb: **warning** accepts a rare duplicate to never miss; **resolved**
accepts a rare miss to never show a false one.

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

Priority order for the still-proposed fixes (R1, R2), with the reasoning worked
out during the 2026-06 examination. None is urgent — the receive path and the
open-door gating are already solid (see "What's already solid"). Both are
**Android-only** (no server deploy; they ride the next `android/N`).

**Done — R6 + M4 (Android `2.20.0`, merged, not yet released).** The two
compounding open-door-warning gaps (foreground drop + no app channel) were fixed
together: `FCMService.onMessageReceived` now renders a foreground warning via
`DoorNotificationPresenter.showWarning(...)`, and manifest
`default_notification_channel_id`/`default_notification_icon` + an
eagerly-created HIGH "Garage door" channel move both the background and
foreground warning (and the resolved) onto one app-owned channel + status-bar
icon. Channel id is a shared string resource (`door_notification_channel_id`) so
the manifest and the presenter can't drift. **Residual (Phase 2):** the
*background* warning is still OS-rendered with FCM's own notification tag, so the
resolved replaces an in-place *foreground* warning but coexists with a
background-rendered one — closing that needs an app-built warning (move the
warning to data-only). Notification *display* remains device-only behavior;
verification reuses the on-device-proven app-built presenter (2.18.0 sandbox)
plus build-time resource/manifest validation in `validate.sh`.

1. **R1 — auto-recover from a missed push (highest value).** This is the
   *primary* goal's (push-data) weak link: staleness is already detected
   (`CheckInStalenessManager`) but recovery is manual (the user must tap the
   stale-data banner). **Measure before building it:** turn on FCM delivery data
   (Firebase console → Cloud Messaging reporting, or the BigQuery delivery
   export) and reconcile against a client receive-log to learn the *actual*
   delivery rate. ~99.9% → R1 is polish; lower → R1 is urgent. The fix itself is
   a medium Android change: wire `isCheckInStale` to one debounced re-fetch with
   backoff, keeping the banner as the fallback.

2. **R2 — runtime topic change (defer).** Low likelihood: only when the device
   `buildTimestamp` changes while the app process stays alive (cold-start
   re-subscribes anyway). Wire the existing `FcmRegistrationManager.restart()`
   to a server-config `buildTimestamp` change when convenient.

---

## Resolved-on-close notification — design goal (validated in the sandbox)

> **Status (2026-06-20): Phase 1 BUILT + MERGED, deploy PARKED.** The additive
> Phase 1 (resolved on its own `door_open_v2-` topic) shipped to `main` (server
> `server/31` #903 + Android `2.19.0` #904) but is **unreleased and the flag is
> never flipped** — nothing is live. **The inline-replace described below is the
> Phase 2 goal, NOT what Phase 1 delivers.** In Phase 1 the production warning is
> still OS-tray (different `(tag,id)`), so the resolved **coexists** with it (two
> cards). The clean replacement needs Phase 2 (app-built warning). Limitations +
> the parked product decision: [`RESOLVED_NOTIFICATION_PLAN.md`](RESOLVED_NOTIFICATION_PLAN.md).

When the door **closes** after a "too long open" warning was sent, the app shows
a **"Resolved"** notification. Validated end-to-end on a real released build
(`android/251`, 2.18.0) via the Test Notification Sandbox: an app-built
notification on a dedicated channel + icon, with `tag`-based inline replace.

**Display behavior (decided 2026-06-15 — this is the GOAL, not a bug):**
- Warning **still showing** → the resolved **replaces it in place** (same
  `(tag, id)` via `NotificationManagerCompat.notify`).
- Warning **already dismissed** → the resolved **appears as a new notification**.
  This is **intentional**: the user should always learn the door closed, even if
  they swiped the warning away. A stale "door open" alert quietly vanishing
  without confirmation is worse than an informative re-ping.
- **Do NOT** add a `getActiveNotifications()` "suppress if dismissed" gate. This
  deliberately **reverses** the "don't resurrect a dismissed warning" concern
  raised during the feasibility investigation — the settled product decision is
  **resurface-always**.
- `setOnlyAlertOnce(true)` is fine (silent update when replacing an active one);
  it does not affect the dismissed-then-resurfaced case.

**Finalized copy (template):**
- **Title:** `Resolved: garage door closed` (static)
- **Body:** `It was open for {duration} ({startTime}-{endTime}).`
- Sentence case, no em dashes (repo string rules).

**Duration / time source:** the open-episode **start** is the timestamp the
warning measured from — the `NotificationsDatabase` marker's
`notificationCurrentEvent.timestampSeconds`, **not** the immediate `previousEvent`
(which understates in a multi-step `Open → Closing → Closed`). End = the close
event timestamp. Format in the device's locale/timezone.

**Trigger gate:** fire **only if a warning was actually sent** for that open
episode (the marker exists; it is absent when snoozed, or when the door closed
before the 15-min threshold).

**Built on:** the app-built notification infrastructure (R6 foreground display +
M4 dedicated channel), now shipped in Android `2.20.0` (merged, unreleased). The
warning and the resolved already share one HIGH "Garage door" channel + (tag, id)
slot, so a *foreground* warning is replaced in place by the resolved today; the
remaining Phase 2 work is making the *background* warning app-built (data-only)
so it shares the slot too. The Test Notification Sandbox
([`docs/TEST_NOTIFICATION_SANDBOX_PLAN.md`](TEST_NOTIFICATION_SANDBOX_PLAN.md))
is the isolated, shipped prototype of that infra, proven on a real device.
