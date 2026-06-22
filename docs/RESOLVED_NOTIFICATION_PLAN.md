---
category: plan
status: active
last_verified: 2026-06-22
---

# Resolved-on-close notification — Phase 1 (additive) implementation plan

**Status: Phase 1 BUILT + MERGED, but DEPLOY HELD / PARKED (2026-06-20).**
Code shipped to `main` — server `server/31` (#903) + Android `2.19.0` (#904) +
config backup recipe (#905) — and is fully CLI-validated, but **nothing is live**:
`server/31` and `2.19.0` are unreleased and the off-by-default
`resolvedOnCloseEnabled` flag has never been flipped. The user **parked** it (did
not deploy) on a deliberate product call — see "Why this is parked" below. The
app-built *warning* (Phase 2) remains deferred.

> ⚠️ **Read "Phase 1 as-shipped — known limitations" before deploying or
> resuming.** Phase 1 does **not** deliver the clean inline warning→resolved
> replacement; it adds a resolved notification that **coexists** with the existing
> OS-tray warning. That, plus the parked decision, is the load-bearing context.

This plan is the durable record of a multi-agent design audit (5 parallel agents:
topology, delivery-matrix, subscription-migration, server-send-path, deploy-ordering).
Read it before touching any file below.

## Phase 1 as-shipped — known limitations (review before deploying)

> **Updated 2026-06-21 — Android `2.20.0` (R6 + M4, merged, unreleased) closed most
> of limitation #1+#2.** The warning and resolved now share **one** app-owned
> "Garage door" channel (HIGH), the **same** `ic_notification_garage` status-bar
> icon, and the **same** `(tag="garage_door", id=7001)` slot. So the
> "two different features" divergence and the "backwards alerting" below are
> **fixed**; what remains is a narrower coexistence case (background warning not
> *replaced*) that still needs Phase 2. The original (pre-2.20.0) text is preserved
> in italics for provenance.

1. **Inline replace works for a FOREGROUND warning; a BACKGROUND warning still
   coexists (not replaced).** With R6+M4 (2.20.0): a warning that arrives while the
   app is **foregrounded** is app-built on `(tag="garage_door", id 7001)` → the
   resolved **replaces it in place**. A warning shown while **backgrounded** is still
   **OS-rendered** with FCM's own tag (the manifest `default_notification_channel_id`
   routes it to the same "Garage door" channel + garage icon, but cannot set the
   `tag`), so the resolved **coexists** with it — two cards. Crucially they now look
   like **one feature**: same channel, same garage icon, same heads-up importance.

   | | Warning | Resolved |
   |---|---|---|
   | Channel | app-owned **"Garage door"** (manifest default; was "Miscellaneous") | app-owned **"Garage door"** |
   | Icon | **`ic_notification_garage`** (manifest default; was launcher icon) | **`ic_notification_garage`** (was `ic_dialog_info`) |
   | Importance | **HIGH → heads-up + sound** (channel; was DEFAULT) | **HIGH → heads-up + sound** |
   | Inline replace by resolved | **foreground: yes** / background: no (FCM tag) | n/a |

   The remaining gap — making the *background* warning share the slot so the resolved
   replaces it too — requires **Phase 2** (move the warning to an app-built data-only
   message). *Original (pre-2.20.0): the warning was the server's OS-tray
   notification-payload on FCM's "Miscellaneous" channel with the launcher icon and
   DEFAULT importance; the resolved was the only app-built card, on "Garage door" with
   `ic_dialog_info` at HIGH — so the two read as different features and the resolved
   out-shouted the warning.* *Exceptions to "two cards": only 1 if you dismissed the
   warning before closing, or (now) if the warning arrived in the foreground and was
   replaced in place.*
2. **~~Resolved out-shouts the warning~~ — FIXED in 2.20.0.** *Original: channel
   `garage_door` is `IMPORTANCE_HIGH`, so the resolved heads-up/buzzed while the
   warning (Miscellaneous/DEFAULT) did not — backwards alerting.* M4 resolved this by
   moving the warning onto the same HIGH "Garage door" channel, so the warning
   now heads-up/buzzes like the resolved. (When the resolved *replaces* an
   already-showing foreground warning in the shared slot, that update is
   intentionally silent — `setOnlyAlertOnce` — so the all-clear doesn't re-buzz.)
3. **Copy says "open" for non-Open warned states.** A warning can fire for
   `OpeningTooLong` or a sensor-error state; the body still reads "It was open for X,"
   and the duration anchors on the *promotion* time (~60s late) for the
   `Opening→OpeningTooLong` case. Branch the copy on the marker's stored event type if
   precision matters.
4. **End-to-end is unproven.** Everything is CLI-green and the *display mechanics*
   were device-validated via the sandbox, but the full chain (server fires on close →
   released app's v2 subscription receives → presenter renders) has never run. The
   flag flip **is** that test; there is no fixture short of it.

## Why this is parked (the product call, 2026-06-20)

The existing open-door warning has worked well for **months/years** — it is not
broken, and the resolved is an **additive nice-to-have, not a fix**. So the bar is
"is it worth disturbing a proven product?", and Phase 1 alone does not clearly clear
it: it adds a second card (limitation #1) that may *detract*, and the clean version
(Phase 2) touches the **primary push-data path** — the part you most want to protect.
With no problem to fix and no urgency, the decision was **restraint**: leave the
merged code dormant (zero cost, revertible-by-never-enabling) and make a deliberate
Phase-2-or-not decision later.

**Update (2026-06-21):** the lower-risk "strengthen what works" move that was
recommended over flipping the resolved flag — **R6 + M4** (show the existing warning
in the foreground; give it a real app-owned channel/icon) — has now **shipped** in
Android `2.20.0` (merged, unreleased). This hardens the proven warning without
enabling the resolved, and as a side effect makes the warning and resolved
*consistent* (same channel/icon/importance), which softens limitation #1 if the
resolved is ever enabled. The resolved flag itself remains parked.

## Isolation guarantee (risk stays on new builds)

Confirmed 2026-06-20. The feature's risk is isolated to **new** Android builds by
**construction**, not by being careful — the boundary is **app version → topic
subscription**:

- **Old builds (< 2.19.0) never receive the resolved.** They subscribe only to
  `door_open-`; the resolved is sent **only** to `door_open_v2-`, which only the
  new build (via `DoorResolvedFcmSubscriptionManager`) subscribes to. A message
  that never goes to `door_open-` can't reach them. Pinned by
  `ResolvedNotificationFCMFakeTest` § "old-app isolation."
- **What old builds DO receive is byte-for-byte unchanged.** Phase 1 doesn't touch
  the warning (`OldDataFCM` → `door_open-`) or the state-sync (`EventFCM` →
  `door_open-`); it only *appends* a resolved send on the `Closed` transition,
  after the state-sync, in a try/catch. So old builds' warning + live state are
  identical with the flag on or off.
- **`server/31` is inert until the flag flips.** With `resolvedOnCloseEnabled` off
  (default), the resolved path does one config read and returns — no send, no
  marker write. Pinned by the "flag off → no send, no save" test. So the global
  server deploy is a no-op for every user until you flip it.
- **Backwards + forward compatible.** No existing topic or payload key was renamed
  (only added) — satisfies the CLAUDE.md FCM-safety rule. The v2 channel is also
  forward-compatible: the `2.19.0` client gates on `kind == "open_door_resolved"`
  and ignores unknown kinds, so a future Phase-2 `open_door_warning` payload won't
  break today's build.

**Controls (mobile):** there is **no per-user opt-in** — the gates are app version
(must be `2.19.0+`) + the **global** server flag. The kill switch is global and
instant: flip `resolvedOnCloseEnabled` off → the flag-agnostic client stops
rendering immediately, no app update. A per-user opt-in/kill would require adding a
`featureXAllowedEmails`-style allowlist gating the v2 subscription (not built;
global was accepted as sufficient 2026-06-21).

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

**Decision reaffirmed 2026-06-22 — Phase 2 stays parked; the end state is "two
*consistent* cards."** After R6+M4 shipped (`2.20.0`) and the open/resolved flow
was validated on-device, the marginal benefit of Phase 2 was re-scoped precisely:
the clean single-card replace **already works** for the *foreground* warning (R6
renders it app-built into the shared slot) and was proven in the sandbox; Phase 2
only extends that to the *backgrounded* warning. That narrow gain is **not worth**
converting the OS-rendered (rock-solid, app-free) warning to a data-only message
the app must wake to render (subject to the same high-priority FCM throttling that
visibly dropped rapid test sends) **and** reworking the primary state-sync path.
The settled end state and the additive remaining steps (tap-to-open `2.20.1`,
optional resolved deploy, optional R1) are in
[`NOTIFICATION_RELIABILITY.md`](NOTIFICATION_RELIABILITY.md) § "Recommended final
architecture." The on-device testing also surfaced + fixed the **tap-to-open**
gap (app-built notifications had no `contentIntent`) in `2.20.1`.
