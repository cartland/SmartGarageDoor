---
category: plan
status: active
last_verified: 2026-06-15
---

# Resolved-on-close notification — Phase 1 (additive) implementation plan

**Status: PLAN (not yet built).** Scope confirmed by the user 2026-06-15:
**Phase 1 only** — the additive resolved-on-close notification. The app-built
*warning* (Phase 2) is deliberately deferred.

This plan is the durable record of a multi-agent design audit (5 parallel agents:
topology, delivery-matrix, subscription-migration, server-send-path, deploy-ordering).
Read it before touching any file below.

## Goal

When the garage door **closes after an open-door warning was actually sent**,
the new app build shows a user-visible **"Resolved: garage door closed — it was
open for X minutes"** notification. Controlled by a **server-config flag** for
**instant on/off with no deploy**. **Backwards compatible** with old app builds.

Finalized copy (user-approved 2026-06-15):
- Title: `Resolved: garage door closed`
- Body:  `It was open for 14 minutes (2:00-2:14 PM).`

## The one structural constraint that defined the scope

Old builds and new builds share the `door_open-<buildTimestamp>` topic, which
carries **both** the door-state-sync (the PRIMARY push-data path) **and** the
open-door warning. A notification-payload warning is rendered by the OS in the
background before app code runs, so it **cannot be suppressed for only the new
build** while the new build is still subscribed to that topic.

- An app-built **warning** therefore forces the new build to **leave**
  `door_open-` entirely, dragging the **primary push-data path** onto a new
  topic (server must dual-send state-sync; if not live-and-verified before the
  client subscribes, the new build goes **silently dark on door state**). That
  is Phase 2's risk and is out of scope here.
- The app-built **resolved** is **purely additive**: a new data-only message on
  a new topic the new build *also* subscribes to. **Zero** change to
  `door_open-`, **zero** change to state-sync, old builds never see it.

**Rejected during the audit:** dual-subscribing to two topics for *warnings*
(guaranteed double notification: OS-tray + app-built). Not relevant to Phase 1
(v2 carries no warning in Phase 1) but recorded so Phase 2 doesn't revisit it.

## Design (Phase 1)

```
door_open-<bt>     : UNCHANGED. state-sync (data) + warning (notification-payload).
                     old AND new builds. No risk, byte-for-byte today's behavior.
door_open_v2-<bt>  : NEW. data-only "resolved" message only.
                     new build subscribes additively (separate manager).

Server flag body.resolvedOnCloseEnabled (default false / absent = off):
  false -> nothing new happens. today's behavior exactly.
  true  -> on a Closed transition, if a warning was sent this episode,
           send a data-only resolved to door_open_v2- and consume the marker.
Revert -> set the flag false in the Firestore console. Instant. No deploy.
```

### Non-negotiable safety constraints (verified by the audit)

1. **The client is FLAG-AGNOSTIC.** It never reads `resolvedOnCloseEnabled`. It
   renders whatever data-only resolved payload arrives on `door_open_v2-`. This
   is what makes revert instant — the client only re-reads server config at cold
   start, far too slow to be a kill-switch. Revert is purely server-side.
2. **Flag off = today's behavior byte-for-byte.** When the flag is off the server
   does NOTHING new (no marker read, no consume, no send) on a close.
3. **Resolved fires only if a warning was actually sent this episode**, decided
   by the existing `NotificationsDatabase` dedup marker — which we now **consume
   (delete) on close** so it is single-use. Without consume-on-close, a later
   quick open/close (<15 min, no warning) reads a stale marker and fires a bogus
   "open for 6 hours" resolved (audit finding F3).
4. **Stale-marker guard.** Even with consume-on-close, a marker can survive a
   close that happened while the flag was off. So when the flag is on and a close
   reads a marker, compute `duration = closeTs - marker.event.timestampSeconds`
   and if `duration <= 0` or `duration > STALE_CAP` (7 days), **delete the marker
   without sending** (silent stale cleanup). Real warned episodes are 15 min–hours.
5. **Resolved payload is data-only**, shares the warning's `collapse_key`
   (`door_not_closed`) so an offline device coalesces a stale warning + its
   resolution, **never** the heartbeat key (`sensor_event_update`) which would let
   the constant check-in heartbeat collapse the resolved away (audit finding D5).
   **Normal priority, no `notification` block** — never a heads-up (so it can't
   violate a snooze; audit finding F4).
6. **Duration anchor = the marker's stored warned-event timestamp**, NEVER
   `previousEvent` (which for `Open→Closing→Closed` is when closing began, not
   when the door opened). Known ~60s understatement on `Opening→OpeningTooLong`
   promotion episodes — acceptable, documented.
7. **Client formats the human strings in LOCAL time.** Server sends raw
   `openTimestampSeconds` + `closeTimestampSeconds`; the client computes the
   duration and formats `"14 minutes (2:00-2:14 PM)"` in the device timezone.
   The server cannot know the device's timezone, so it must not pre-format local
   times.
8. **The v2 subscription can NEVER touch `door_open-`.** A separate
   button-health-style manager owns only `door_open_v2-`, with a `requireOwnTopic`
   prefix guard, records on confirmed success only (M1 lesson). A v2 failure can
   never harm state-sync.

### Resolved trigger point (server)

`EventUpdates.updateWithParams`, in the **`Closed` transition** branch, right
after the existing `EventFCMService.sendFCMForSensorEvent(...)` state-sync send.
Event-driven = immediate (vs the 5-min pubsub). `Closed` is reliably produced by
`getNewEventOrNull` from every warnable prior state (Open, Opening, OpeningTooLong,
Closing, ClosingTooLong, Error, Unknown, OpenMisaligned — audit finding F2). One
caveat: if the physical close lands in a sensor-conflict reading the door goes to
`ErrorSensorConflict` first → resolved fires when a real `Closed` arrives later
(delayed, not lost).

### Payload contract (data-only, on `door_open_v2-`)

```json
{
  "kind": "open_door_resolved",
  "openTimestampSeconds": "1748800000",
  "closeTimestampSeconds": "1748800840"
}
```

`kind` is the notification-intent discriminator (distinct from the door-event
`type` key). Forward-compatible: Phase 2 adds `"open_door_warning"`. The client
owns a fixed notification `tag` (one door-alert slot) so a future warning and the
resolved share the slot for inline replacement.

## File checklist

### Server (FirebaseServer/)
- [ ] `src/model/FcmTopic.ts` — add `buildTimestampToFcmTopicV2()` → `door_open_v2-` + sanitized bt. + parity unit test.
- [ ] `src/controller/ConfigAccessors.ts` — add `getResolvedOnCloseEnabled(config): boolean`, fail-safe: anything non-`true` → `false` (do NOT throw, unlike `getBuildTimestamp`).
- [ ] `src/controller/fcm/ResolvedNotificationFCM.ts` (NEW) — `sendFCMForResolvedDoor(buildTimestamp, closedEvent)`: read config flag (live) → if off, return null; read marker → no marker → null; stale-cap guard → delete + null; else build data-only resolved (kind/open/close), send to `door_open_v2-`, consume (delete) marker.
- [ ] `src/database/NotificationsDatabase.ts` — add a `clear(buildTimestamp)` / delete (consume the marker). Verify whether TimeSeriesDatabase exposes delete; else save a consumed sentinel.
- [ ] `src/controller/EventUpdates.ts` — on `Closed` transition, after state-sync send, `await ResolvedNotificationFCMService.sendFCMForResolvedDoor(...)`. Gate the CALL on `newEvent.type === Closed` so config is only read on actual closes (not every heartbeat).
- [ ] Tests: `ResolvedNotificationFCM` (warned→close→sent+consumed; no-warning→close→none; stale-marker→none+cleanup; flag-off→nothing; duration math; data-only shape; correct topic). `FcmTopic` v2 parity.
- [ ] `wire-contracts/openDoorResolved/payload_resolved.json` — pin the payload shape; load it in the server test.
- [ ] `CHANGELOG.md` — `## server/N` entry.

### Android (AndroidGarage/)
- [ ] `domain/.../model/DoorFcmModel.kt` (or new file) — add the `door_open_v2-` builder (mirror server, byte-identical).
- [ ] `domain/.../repository/` + `data/.../repository/` — `DoorResolvedFcmRepository` interface + `FirebaseDoorResolvedFcmRepository` (clone `FirebaseButtonHealthFcmRepository`: subscribe records on confirmed success, `requireOwnTopic` guard for `door_open_v2-`, unsubscribeAll). NOT auth-gated (door isn't).
- [ ] `usecase/.../DoorResolvedFcmSubscriptionManager.kt` — clone `ButtonHealthFcmSubscriptionManager` (sans auth gate). Idempotent `start()`.
- [ ] `usecase/.../AppStartup.kt` — call `.start()`.
- [ ] `androidApp/.../fcm/DoorNotificationPresenter.kt` (NEW, Android-only) — generalize `TestNotificationPresenter`: app-owned HIGH-importance channel ("Garage door"), build NotificationCompat from payload, `notify(FIXED_TAG, ID)`, setAutoCancel/setOnlyAlertOnce, POST_NOTIFICATIONS guard. Formats duration + local start/end times client-side.
- [ ] `androidApp/.../fcm/DoorResolvedPayload.kt` (NEW) — lenient pure parser (kind/open/close ts).
- [ ] `androidApp/.../fcm/FcmMessageHandler.kt` — add `topic.startsWith("door_open_v2-")` branch (BEFORE the door `else`); `door_open-` still falls through to `handleDoorMessage` unchanged.
- [ ] `androidApp/.../fcm/FCMService.kt` — wire `DoorNotificationPresenter` into the handler.
- [ ] `androidApp/.../di/AppComponent.kt` AND `iosFramework/.../NativeComponent.kt` — provide the repo + manager (shared modules → BOTH DI components, same PR; validate.sh is Android-only and misses iOS DI breakage). Presenter is Android-only (no NativeComponent entry).
- [ ] `ComponentGraphTest` — singleton assertSame for the new manager/repo if `@Singleton`.
- [ ] Tests: `FcmMessageHandler` routing for `door_open_v2-`; `DoorResolvedPayload` parser; `FcmTopic` v2 parity (Android side); wire-contract strict-decode test against the same fixture.
- [ ] `CHANGELOG.md` — `## X.Y.Z` (minor bump: new user-facing capability) + `distribution/whatsnew/`.

## Deploy + flip + revert (operational)

1. **Server first.** Land + `server/N` deploy. Flag absent → off → today's behavior
   byte-for-byte. `door_open_v2-` has no subscribers → harmless.
2. **Android.** Land + `android/N` to internal track. New build subscribes
   `door_open_v2-`. Flag still off → server sends nothing to v2 → nothing happens.
   Verify nothing breaks (state-sync + warning unchanged).
3. **Flip on.** Firestore console → `configCurrent/current` → `body` → set
   `resolvedOnCloseEnabled = true`. **In-place single-field edit — NEVER a scripted
   whole-doc `httpServerConfigUpdate` POST**, which replaces the whole doc and would
   clobber `buildTimestamp`/allowlists (`docs/FIREBASE_CONFIG_AUTHORITY.md`). Test:
   open door >15 min (warning fires OS-tray as today) → close → app-built resolved
   on the new build; old builds: warning as today, no resolved.
4. **Revert.** Set `resolvedOnCloseEnabled = false` (or delete the field). Instant,
   server-side only.

## Residual edge cases (acceptable for Phase 1, internal track)

- **~60s duration understatement** on `Opening→OpeningTooLong` promotion episodes
  (marker anchor is the promotion time). Minor.
- **Error-state close** → resolved delayed until a real `Closed` arrives; if the
  warned event was an error type, "door was open" wording is imprecise (optionally
  branch copy on the marker's stored event `type`).
- **At-least-once double-close** → the same `tag` inline-replace collapses two
  resolved sends to one visible notification; consume-on-close dedups the
  sequential case. A Firestore transaction would close the concurrent race (same
  call as the R5 tradeoff) — out of scope.
- **Flag flipped on with a stale marker from a flag-off close** → caught by the
  stale-cap guard (constraint #4).

## Phase 2 (deferred — do NOT build now)

App-built *warning*: new build leaves `door_open-`, lives entirely on
`door_open_v2-`; server dual-sends state-sync to both topics (server-first +
verify ordering is mandatory — silent push-data loss otherwise); warning becomes
data-only app-built (fixes R6 foreground-drop + M4 channel); true inline
warning→resolved replace. Cancel the app-built warning on the always-on
state-sync close, NOT on the flag-gated resolved, so a revert never strands a
posted notification. Full rationale in this file's git history and the audit.
