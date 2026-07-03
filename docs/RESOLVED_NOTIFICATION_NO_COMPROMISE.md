---
category: plan
status: active
last_verified: 2026-07-02
---

# Open-door notification: is a no-compromise single-card design possible?

Status: analysis and recommendation. Not yet shipped. Supersedes nothing; extends
[`RESOLVED_NOTIFICATION_PLAN.md`](RESOLVED_NOTIFICATION_PLAN.md) (Phase 1 live) and
[`NOTIFICATION_RELIABILITY.md`](NOTIFICATION_RELIABILITY.md) (R1-R6, final architecture).

Produced by a multi-agent analysis (ground-truth readers + web research on FCM/Android
mechanics, four independent candidate designs, twelve adversarial verifications on three
lenses, two opposed proof attempts, one synthesis). Cross-checked against `FcmTopic.ts`,
`OldDataFCM.ts`, `EventFCM.ts`, `ResolvedNotificationFCM.ts`, and `DoorNotificationPresenter.kt`.

## 1. Verdict

**A strictly no-compromise solution (constraints A through E holding with a true single card
in every scenario) is IMPOSSIBLE. It is achievable for the well-defined scope "the app process
runs at least once at or after the door closes" (foreground and backgrounded-but-woken, the
common-to-occasional case); it provably cannot hold in the "app never runs between the warning
and the close" corner, which is itself a common state. The recommended action closes the common
backgrounded-woken two-card gap with zero cost to old apps, state-sync, warning reliability, or
revert, and accepts the irreducible never-woken corner as a cosmetic two-card outcome that
always fails safe.**

The two independent proof attempts agree on the technical content. One labels the answer
"partial" (no-compromise holds for a definable scope), the other "no" (no-compromise does not
hold across all scenarios). Both describe the same boundary. This document keeps both truths
explicit: yes for the app-alive-at-close scope, no across all scenarios. The "no" framing is
the safer headline because the never-woken corner is common, not an edge.

**The impossibility is driven entirely by constraint A (zero old-app change).** If A is relaxed
to A-prime — *old-app open-door warnings must not be degraded (delivery, visibility, and alerting
all preserved), but a neutral-or-improving behavior change is acceptable* — then the ideal
new-app experience becomes achievable in **every case where a message is physically deliverable**,
and the only residual is the force-stopped / never-delivered device (Wall 2), which no design can
beat and which fails safe. This is the maintainer's actual requirement. See **section 9**, which
is the go-forward recommendation.

## 2. The no-compromise spec (A through E) and the scenario matrix

A candidate is "no-compromise" only if it holds all of these in every realistic scenario.

- **A. Zero old-app change.** For every build < 2.19.0 (frozen, un-updatable, subscribes only
  to `door_open-<bt>`), the byte set published to `door_open-` and the OS rendering of it is
  identical with the feature on vs off. No forced new subscription, no renamed key, no
  delivery-timing change. Checkable by diffing the `door_open-` publish set flag-on vs flag-off.
- **B. Single card.** For one open-door episode on a new app, at no instant do two tray cards
  attributable to that episode coexist; the warning transitions in place to "Resolved: open for
  X min," with heads-up on first surface, a silent replace of a still-showing warning, and
  tap-to-open.
- **C. Warning reliability.** The warning surfaces even if the app process never runs between
  open and close (Doze, force-stop, background-restricted, throttle), at no worse than today's
  OS-rendered reliability. This forces the warning to be a notification-payload on an
  already-subscribed topic.
- **D. State-sync safety.** The primary door-state-sync DATA path gains zero added silent-loss
  risk: never moved to a new topic, never gated on a new subscription, never dual-send-dependent,
  never reordered so a race can drop it.
- **E. Additive, instant server revert.** Purely additive (new topic or payload only, no rename),
  revertible server-side instantly via one config flag, with the client flag-agnostic. Flag-off
  equals today byte-for-byte.

Plus three cross-cutting invariants any design must also keep:

- **Resurface-always.** After a warned episode the user always learns the door closed, even if
  they swiped the warning away first. The resolved is never suppressed merely because the warning
  was dismissed.
- **No false resolved.** A resolved fires only for an episode where a warning was actually sent
  (marker present, un-consumed, duration > 0 and <= 7 days). Snooze, quick open/close, and stale
  markers yield zero cards.
- **No silent loss (weighted highest).** No scenario ends with a warning or a door-state update
  silently lost. A dropped resolved must degrade to a still-informative state (the warning card
  remains), never to "door looks closed but was never confirmed."

Scenario matrix (11 scenarios). "Ideal" = single-card B plus its invariants.

| # | Scenario | Likelihood | App alive at close? |
|---|----------|-----------|---------------------|
| 1 | Foreground throughout, warning left up | occasional | yes |
| 2 | Foreground, warning dismissed before close | occasional | yes |
| 3 | Backgrounded, process warm at close, warning left up | common | yes |
| 4 | Backgrounded, never runs, resolved delivered and wakes app | common | yes (woken by resolved) |
| 5 | Backgrounded, never runs, resolved dropped/throttled or app force-stopped | common | no |
| 6 | Backgrounded never runs, warning dismissed, resolved arrives later | occasional | yes (woken) |
| 7 | Rapid re-open: multiple warned episodes in minutes | rare | varies |
| 8 | Snoozed episode (no warning) | common | n/a (zero cards) |
| 9 | Quick open/close under 15 min (never warned) | common | n/a (zero cards) |
| 10 | Flag reverted off mid-episode | rare | varies |
| 11 | Error-state close (ErrorSensorConflict before real Closed) | rare | varies |

The hardest scenario is **#5**. It is the single state where A, C, D, and E are all
simultaneously satisfiable but B provably cannot be, and it is common, which is what makes it
fatal to strict no-compromise.

## 3. Candidate designs and adversarial findings

Four designs, each attacked from three lenses: old-app zero-change, silent-dark (state-sync or
warning loss), and the never-woken corner.

### 3.1 Cancel-on-close (client-only enumerate + cancel + atomic post-resolved)

Mechanism. Pure client change; server byte-identical to shipped Phase 1. Warning stays a tag-less
notification-payload on `door_open-`. Resolved stays data-only on `door_open_v2-`. When a resolved
arrives and the app is running, the handler (a) posts the resolved to the shared
(tag `garage_door`, id 7001) slot first, then (b) enumerates `getActiveNotifications()` and cancels
any door-channel warning it finds, reading the real (tag, id) off the `StatusBarNotification`
(no dependence on the undocumented FCM id=0).

- Old-app lens: does not break. The `door_open-` publish set is untouched; the cancel logic runs
  only on updated devices and cannot reach a frozen device. The only candidate that keeps A
  perfectly airtight.
- Backgrounded-woken (#3, #4): strict improvement over Phase 1 — turns two coexisting cards into one.
- Never-woken (#5): fatal to B. The resolved is data-only, not OS-rendered; both the render and the
  cancel live in a handler that never runs. Result is a lone stale warning, identical to Phase 1's
  floor. No regression, no improvement here.
- Silent-dark lens: breaks **as originally specified**. The proposed over-cancel guard compares
  `StatusBarNotification.getPostTime()` (device clock) against `closeTimestampSeconds` (server clock).
  Under rapid re-open plus negative device skew, this can cancel a currently-open later episode's
  valid warning and leave a stale resolved card showing — a false all-clear on an open door. That is
  a new silent-loss in the scariest class Phase 1 does not have. Also, `notify()` returning void
  does not prove the card rendered (rate-limiter shedding), so post-then-cancel is not atomic.

Verdict: the best strict improvement over Phase 1 for the app-alive-at-close scope, but it must be
hardened (section 4) before shipping. Its remaining gap is exactly the never-woken corner.

### 3.2 server-tag-collision (tag the `door_open-` warning; resolved OS-rendered on v2 sharing the tag)

Add `android.notification.tag = "garage_door"` to the warning on `door_open-`, and convert the
resolved to a combined notification+data message on `door_open_v2-` sharing that tag. Same-tag
OS-to-OS replacement then collapses the two into one card with zero app code, even under Doze.

- Never-woken deliverable sub-case: this is the design's genuine win (single card, no app wake).
- Old-app lens: **breaks A, fatally.** The tag sits on the warning, broadcast byte-identically to
  frozen apps. Today the untagged warning gets a distinct OS-assigned tag per message, so multiple
  undismissed warnings stack; with a fixed tag a newer warning replaces an older still-showing one.
  Reachable within one stuck-door incident (Opening then OpeningTooLong at 60s = new event timestamp
  past `OldDataFCM`'s timestamp-keyed dedup). Frozen apps see one card where they saw a stack.
- Still not strict no-compromise: the force-stop sub-case of #5 delivers nothing, so B fails there
  anyway. It pays a real old-app change and takes on undocumented cross-topic same-tag id-derivation
  risk for only a partial B gain. Also loses client-side local-time formatting and can re-buzz on
  replace (`setOnlyAlertOnce` is app-only).

Not recommended: crosses the inviolable zero-old-app-change line and leans on undocumented behavior.

### 3.3 Cross-topic OS-to-OS twin

Same mechanism as 3.2, framed as a two-file server change with an explicit foreground app-render
path. Same finding: a background single card in the common Doze deliverable case (a real win over
Phase 1), but it requires tagging the shared `door_open-` warning (breaks A, verified reachable),
re-alerts on replace (breaks B-alerting), depends on undocumented FCM cross-topic id derivation, and
cannot beat the force-stop floor. Not recommended.

### 3.4 Topic-migration-hardened Phase 2 (new build migrates fully to `door_open_v3-`)

A fresh `door_open_v3-` topic only the new build subscribes to. State-sync, warning, and resolved
all dual-sent: unchanged to the legacy topics, and additively to v3 as tagged notification-payloads.
The new build unsubscribes the legacy topics after subscribing v3, so OS-to-OS same-tag replacement
collapses to one card even when the app never wakes.

- Old-app lens: does not break (legacy topics genuinely untouched).
- Never-woken deliverable (#4/#5a): single card with no app wake — the best B coverage of any candidate.
- Silent-dark lens: **fatal.** FCM's subscribe Task completes on request acceptance, not on effective
  fan-out propagation. There is a window where `unsubscribe(door_open-)` has propagated but
  `subscribe(v3)` has not; a door transition in that window fires state-sync AND (if open > 15 min)
  the warning to `door_open-`, and neither reaches the device. The warning is sent exactly once per
  marker and never regenerated client-side, so it is silently lost with no resurface. This correlates
  state-sync and the safety-critical warning onto one freshly-subscribed topic, so a subscription
  drift takes both dark together, and a never-woken device cannot self-heal.
- Revert: worse than Phase 1. The topic migration is a one-way client commitment no server flag can
  reverse.

Rejected: it moves the compromise into the scariest class (silent state-sync plus warning loss) and
loses instant revert.

### Reconciling the two proof attempts

They do not disagree. Both prove the same contradiction in the same corner (#5), identify the same
key constraint conflict, recommend the same minimal compromise (relax B only, cosmetically, in the
never-woken corner), and name the same best surviving design (hardened cancel-on-close) with the
same critical caveat about the cross-clock-domain over-cancel guard. The only difference is the
top-line label: "partial" (no-compromise holds for the app-alive-at-close scope) vs "no"
(no-compromise does not hold across all scenarios). Both truths are kept explicit. The "no" framing
is the safer headline because the never-woken corner is common.

## 4. Recommended design (for the achievable scope)

Recommendation: keep shipped Phase 1 + R6/M4 as the safe baseline, and ship **hardened
cancel-on-close** to close the common backgrounded-woken two-card gap. This is the largest
single-card improvement reachable with zero cost to A, C, D, or E and no undocumented-platform
dependence. If the team judges the two device-verifiable assumptions or the residual client risk
not worth the cosmetic win, the fallback is to ship nothing and keep Phase 1 — already the safest
end state.

### 4.1 Server changes

**None.** The server stays byte-identical to shipped Phase 1. This is the property that makes A
airtight and revert instant.

- Warning: unchanged notification-payload on `door_open-` (`OldDataFCM.getDoorNotClosedMessageFromEvent`, no `data`, no tag).
- State-sync: unchanged data-only on `door_open-` (`EventFCM`).
- Resolved: unchanged data-only on `door_open_v2-`, flag-gated on `resolvedOnCloseEnabled`
  (`ResolvedNotificationFCM.getResolvedMessage`, `kind: open_door_resolved`, `collapse_key door_not_closed`, HIGH, no notification block).

### 4.2 Client changes (new build only)

In the resolved handler path (`FCMService.onMessageReceived` -> `FcmMessageHandler` ->
`DoorNotificationPresenter.show`), when a `kind == open_door_resolved` message is handled:

1. **Post the resolved first.** Build and `notify(TAG = "garage_door", ID = 7001, resolved)` exactly
   as today. This preserves resurface-always structurally: the resolved is always posted before any
   cancel, so a dismissed warning simply means there is nothing to cancel and the resolved posts
   fresh. In the foreground case this also replaces the app-built warning in the shared slot (R6).
2. **Gate the cancel on locally-confirmed current door state.** Read the app's current door state
   from the independent state-sync cache. Only proceed to cancel if the local state is Closed. If a
   later episode has already re-opened, local state is not Closed, so skip the cancel: the real open
   warning survives and you correctly show the true state, never a false all-clear. This replaces the
   unsafe cross-clock-domain `getPostTime()` vs `closeTimestampSeconds` comparison entirely.
3. **Enumerate and cancel by real (tag, id).** Call `getActiveNotifications()` (API 23, universal at
   minSdk 26), and for each `StatusBarNotification` whose channelId equals the "Garage door" channel
   and whose (tag, id) is not our just-posted (`garage_door`, 7001), read its real tag/id and
   `cancel(tag, id)`. The background FCM warning is posted under the app's own package, so it is
   returned by this enumeration and cancellable without relying on the undocumented FCM id=0.

Bind cancel only to the resolved message. Do not hook the state-sync Closed message as a cancel
trigger — state-sync carries no duration and cannot build a resolved, so cancelling there would erase
the warning with no replacement whenever the separate resolved drops.

### 4.3 Topic, tag, and slot mechanics

- Foreground warning and resolved: app-built, shared (`garage_door`, 7001) slot,
  `setOnlyAlertOnce(true)` so the all-clear does not re-buzz, `setContentIntent(launchAppIntent())`
  so tap opens the app.
- Background warning: OS-rendered under FCM's own (tag, id) on the "Garage door" channel (via manifest
  `default_notification_channel_id`, M4). The app cannot claim that slot in place; it can only discover
  and cancel it via enumeration when it runs.
- Resolved: data-only on `door_open_v2-`, rendered by the app, formatted with device-local start/end
  times (`DoorResolvedNotificationText.body(...)`). This local-time formatting is a benefit the
  server-tag-collision designs would lose.

### 4.4 Revert model

Unchanged from Phase 1 and fully instant. Flip `resolvedOnCloseEnabled = false` in Firestore
`configCurrent/current`. The server sends no resolved, the client never receives one, and the
enumerate+cancel path never runs. Client stays flag-agnostic.

### 4.5 Guarantee boundary

Covered (single card, A/C/D/E all perfect): **#1** foreground; **#2** foreground dismissed (resolved
posts fresh); **#3** backgrounded warm at close (the everyday case Phase 1 shows as two cards); **#4**
backgrounded, resolved wakes app (contingent on the resolved waking the app — because it posts a
user-facing card it escapes FCM's "no user-facing notification, deprioritize" rule that would afflict
a cancel-only message); **#6** dismissed then resolved; **#7** rapid re-open (false-all-clear risk
removed by the local-Closed-state gate); **#8/#9** zero cards; **#10** flag-off byte-identical; **#11**
error-state close (resolved fires on the genuine Closed, delayed not lost).

Not covered (the irreducible corner): **#5** backgrounded, app never runs between warning and close,
and the resolved is dropped or the app is force-stopped. The enumerate+cancel never runs and no
resolved posts. The OS warning remains as a lone stale "door open" card with no all-clear. This is a
**resolved miss, not a state-sync or warning loss**: the warning is still visible and the in-app door
status is still correct via the independent state-sync path, so opening the app shows the true Closed
state. This equals Phase 1's floor, and per section 5 no design closes it without relaxing A, C, or D.

### 4.6 Empirical unknowns and the sandbox tests that gate them

Three assumptions are device-verifiable and should be confirmed before shipping. Extend
`scripts/send-test-notification.sh` with a `--notification-payload` mode (+ `--tag`) so the background
warning is reproducible without touching production door topics.

1. `getActiveNotifications()` returns the OS/FCM-rendered background warning under the app's package.
   Test: send a notification-payload message (title/body, no `data`, HIGH, `notification_priority` MAX)
   while the app is backgrounded; foreground the app and log `getActiveNotifications()`; confirm the
   warning is present with a readable tag and id.
2. The returned SBN's channelId equals the "Garage door" channel (relies on the manifest
   `default_notification_channel_id` and `OldDataFCM` not setting `android.notification.channel_id` —
   verified in source that it does not). Confirm on at least one aggressive OEM skin.
3. `cancel(tag, id)` with the SBN's real tag/id removes the OS-rendered warning.

A fourth test applies only if the team ever reconsiders the tag-collision family (not recommended):
send two notification-payloads sharing `android.notification.tag = "garage_door"` from two different
topics to the same backgrounded device and observe whether the OS collapses them and whether the
second re-buzzes — exercising the undocumented cross-topic same-tag id derivation.

## 5. Impossibility proof for the never-woken corner

Claim. In the corner where the app runs zero code between the warning firing and the door closing, no
design satisfies A, B, C, D, and E simultaneously.

Proof. Assume such a design exists. Two independent walls each yield a contradiction.

**Wall 1 — "never two coexisting cards" with no app running.** With no app code, the only tray
operation at message-post time is the OS rendering a notification-payload (which adds a card, or
replaces one iff the new message shares the same package+tag+id). There is no OS-side "cancel or merge
on data-message receipt" primitive. Therefore collapsing warning and resolved into one card with no
app code requires OS same-tag replacement, which requires the resolved to be an OS-rendered
notification-payload sharing the warning's tag, which requires a deterministic tag on the warning the
new app renders. By C, that warning is OS-rendered from an already-subscribed topic. Case split:

- Case alpha (warning on `door_open-`): `door_open-` is broadcast byte-identically to frozen apps
  (both `EventFCM` and `OldDataFCM` publish via `buildTimestampToFcmTopic()` with no per-version
  branch). Today the untagged warning gets an OS-assigned distinct tag per message, so multiple
  undismissed warnings stack; `OldDataFCM`'s dedup is timestamp-keyed, so a multi-state stuck-door
  incident, an at-least-once retry, or a stale prior-episode card produces distinct warnings. A fixed
  tag makes a newer warning replace an older still-showing one. Frozen apps observe one card where they
  saw a stack. This violates **A**.
- Case beta (warning on a new topic v_new): to avoid the new app also OS-rendering the un-suppressible
  `door_open-` warning (a background notification-payload cannot be intercepted), the new app must not
  subscribe to `door_open-`. But `door_open-` also carries state-sync, so state-sync must be dual-sent
  to a new topic — an independent publish that can be dropped, refactored away, or propagation-raced
  independently, a new silent-loss vector for the primary state path; a one-way non-revertible client
  migration; and a subscribe-before-unsubscribe window that silently drops the warning and state-sync
  together. This violates **D** (and E, and momentarily C). Keeping the new app on `door_open-` for
  state-sync while adding v_new for the warning instead yields two warnings, violating **B**.

Every placement violates A or D+E+B.

**Wall 2 — "resolved actually appears," independent of Wall 1.** In the force-stop or restricted-bucket
sub-corner, the OS delivers no FCM message of any kind to a stopped process and the app runs no code.
Therefore no resolved card can be produced on that device during the window, for any design and any
values of A through E. This is a platform property of push to a stopped process, not a consequence of
the frozen constraints.

Either wall refutes the assumed design. Hence strict A through E for all scenarios is impossible. QED.

**Key constraint contradiction (one sentence).** A single card with no app running requires OS
same-tag replacement of two notification-payloads, which requires a deterministic tag on the
OS-rendered warning; but that warning is frozen onto the shared `door_open-` topic that un-updatable
old apps also receive byte-identically (so tagging it breaks A), and the only way to move it off
`door_open-` drags the primary state-sync onto a new topic (so it breaks D and E); and in the
force-stop sub-case the collapsing message is never delivered, so B fails regardless of A and D.

### Minimal compromise

Relax **B**, and only B, and only in the never-woken corner. Accept: a device that never runs between
the warning and the close may show a lone stale "open" warning (a resolved miss) until it next wakes,
or the OS auto-cancels, or the user swipes; and where two cards do briefly coexist they share the
"Garage door" channel, icon, and heads-up so they read as one feature.

Why this is the correct minimal relaxation:

- It is purely cosmetic. Zero delivery reliability cost; A, C, D, E and instant server revert stay
  perfect; no undocumented-platform reliance. It fails in the safe direction (shows open, never a false
  closed) and never in the scariest silent-loss class — state-sync independently keeps the in-app door
  status correct.
- Every alternative relaxation is strictly worse-placed. Relaxing A (tag the frozen warning) does not
  even buy strict no-compromise (the force-stop sub-case still breaks B), so it pays a real old-app
  change plus undocumented cross-topic reliance for only a partial B gain. Relaxing C (app-built
  data-only warning) or D and E (migrate or dual-send state-sync) each surrenders a hard reliability
  guarantee in the scariest silent-loss class.

Orthogonally, the common backgrounded-woken two-card gap (inside the achievable scope, not the
impossible corner) is closed by hardened cancel-on-close with no constraint relaxation at all — an
improvement over Phase 1, not a compromise.

## 6. Recommendation vs the parked Phase 2

| Axis | Recommended (Phase 1 + hardened cancel-on-close) | Parked Phase 2 (migrate warning to app-built data-only) |
|------|--------------------------------------------------|---------------------------------------------------------|
| Old-app safety (A) | Perfect. Server byte-identical; client change is device-local. | Perfect on paper, but only by moving the new build off `door_open-`. |
| Warning reliability (C) | Unchanged. OS-rendered notification-payload on an already-subscribed topic; fires with no app code. | Regresses. Warning becomes a wake-the-app data message subject to high-priority throttling. |
| State-sync safety (D) | Perfect. State-sync never referenced, moved, or gated. | At risk. Migrating off `door_open-` drags state-sync onto a new topic (dual-send race; silent-dark). |
| Silent-dark risk | None added. Never-woken degrades to a visible stale warning, not a hidden state. | High. Subscribe-before-unsubscribe window silently drops warning and state-sync together. |
| Single-card coverage (B) | Foreground + backgrounded-woken: single card. Never-woken: cosmetic two cards / lone warning. | More background cases single-card, but at the reliability and revert costs above. |
| Revert (E) | Instant server flag flip; client flag-agnostic. | Partial. Topic migration is a one-way client commitment no flag can revert. |

Net: the recommendation improves single-card coverage in the common backgrounded-woken case while
keeping every hard safety guarantee Phase 2 would trade away. Phase 2 stays parked.

## 7. Resurface-always product decision

The settled decision (`NOTIFICATION_RELIABILITY.md`) is resurface-always: the user must always learn
the door closed, even if they swiped the warning away. That decision rejects a `getActiveNotifications()`
gate that would suppress the resolved when the warning was dismissed. It does not forbid cancelling a
still-showing warning while always posting the resolved.

The recommendation respects this by construction and ordering: it always posts the resolved first
(step 1), unconditionally, before any enumeration; it only additionally cancels a still-showing warning
(step 3), and only after confirming local door state is Closed (step 2). It never suppresses the
resolved. Any future change to the enumerate+cancel logic must preserve the "post resolved first,
unconditionally" ordering as the invariant that keeps resurface-always structural, not incidental.

## 8. Open questions and sign-offs needed

1. **Product sign-off on the never-woken cosmetic compromise.** Confirm that a lone stale "open"
   warning (until the device next wakes) in the force-stop / never-delivered sub-case is acceptable,
   given it always fails safe and the in-app state is correct.
2. **Product sign-off on the enumerate+cancel use of `getActiveNotifications()`.** Confirm the "post
   resolved, then cancel a still-showing warning, gated on local Closed state" behavior, and that it
   must never suppress the resolved.
3. **Device verification gate.** The three tests in section 4.6 must pass, including on at least one
   aggressive OEM skin, before the client change ships. Until then, ship nothing beyond Phase 1.
4. **Rapid-re-open hardening review.** Confirm the local-Closed-state gate reads from the state-sync
   cache authoritative at handler time, and decide whether an episode-id match on the resolved payload
   should be added as belt-and-suspenders.
5. **Scope decision.** Ship hardened cancel-on-close now (closes the common backgrounded-woken gap)
   or hold at Phase 1. The recommendation is to ship it after the device gate passes; holding at
   Phase 1 is already the safest end state and carries no risk.

## 9. If constraint A is relaxed (the go-forward recommendation)

Sections 1 through 8 assume strict A (zero observable old-app change). The maintainer's actual
requirement is weaker and this is the constraint that should govern the design:

- **A-prime (governing constraint).** Old-app open-door warnings must **not be degraded**: every
  warning that is delivered today is still delivered, still OS-rendered on the same channel with the
  same title, body, sound, and priority, still with no app code required. A behavior change that is
  **neutral or an improvement** for old apps is acceptable; a degradation (a missed warning, a lost
  alert, less information, reduced delivery reliability) is not.

Under A-prime the blocked never-woken solution (the tag-collision family, section 3.2 / 3.3) becomes
viable, and the impossibility collapses to only the force-stop / non-delivery floor (Wall 2), which
is unbeatable by any design and fails safe.

### 9.1 Design (tag-collision + combined resolved)

- **Warning.** Unchanged notification-payload on `door_open-`, plus one added field:
  `android.notification.tag = "garage_door"` (a deterministic, app-namespaced tag not otherwise used).
  Same topic, same title/body/sound/priority/channel, same delivery. Reaches old and new apps.
- **Resolved.** A **combined notification+data** message on `door_open_v2-` carrying the **same tag**:
  - Background / never-woken: the OS renders the notification part and same-tag-replaces the warning
    in the tray with **no app code** — this is what closes the never-woken deliverable corner that
    strict A made impossible.
  - Foreground / app alive: `onMessageReceived` fires with the data part, and the app builds the rich
    resolved (device-local start/end times, `setOnlyAlertOnce`, tap-to-open) and replaces the warning
    in the shared slot. The combined message gives the OS-render floor and the app-built ceiling in one
    message.
- **State-sync.** Untouched on `door_open-`. **Constraint D stays perfect** — no migration, no
  dual-send, no silent-dark risk. This is why relaxing A (not D) is the correct trade.

### 9.2 New-app experience under A-prime

| Scenario | Result |
|---|---|
| Foreground | single rich card |
| Backgrounded, app woken | single card |
| **Never-woken but message deliverable (Doze)** | **single card (OS same-tag replace) — newly unlocked** |
| Force-stopped / message never delivered | lone stale warning (Wall 2, unbeatable, fails safe) |

The ideal single-card experience holds in **every case where FCM can deliver a message.** The only
residual is a device that receives nothing — where no design can post anything and the delivered
warning simply stays visible.

### 9.3 Old-app impact — why it is non-degrading

Adding the tag changes exactly one thing for a frozen app: multiple undismissed open-door warnings
**collapse to the latest (replace-in-place) instead of stacking.** Everything the requirement
protects is preserved:

- Delivery: identical (same topic, same payload delivery). Every warning still reaches old apps.
- Visibility: identical (OS-rendered notification-payload, same channel, same title/body).
- Alerting: a replacing warning re-alerts just as a stacking warning does today; no downgrade.
- Information: for a single door the latest warning is the current truth; a superseded older warning
  carries no information the user still needs. No missed warning, no lost alert.
- No cross-feature collision: `garage_door` is app-namespaced and previously unused; it cannot clash
  with any other notification an old app posts.
- Resolved: old apps still receive no resolved (it is on `door_open_v2-`, which they do not subscribe
  to) — unchanged from today.

Net for old apps: one current "door open" card instead of a stack of stale ones. This is neutral to
an improvement, which A-prime permits. This claim is the one the adversarial pass must confirm before
building — the crux is whether any scenario turns stacking-to-replace into information loss.

Optional further improvement (bigger change, product call): also send the resolved to `door_open-`,
so old apps' stale warning auto-clears and they learn the door closed. This is an improvement, not a
degradation, but it is a larger change; keep it out of the minimal design and revisit only if wanted.

### 9.4 Gates before this is safe to build

> **Gate results — Pixel, 2026-07-02 (PASS).** Run via `send-test-notification.sh --mode
> notification|combined --tag garage_door` with the app backgrounded. (1) A same-tag warning
> replacement **re-alerted** (buzzed) — the A-prime result: tagging the warning does not degrade old
> apps. (2) A `--mode notification` warning followed by a `--mode combined` resolved with the same tag
> **collapsed to one card** — cross-topic same-tag replacement works, so the never-woken single-card
> holds. (3) The resolved **buzzed** despite `PRIORITY_LOW` (see #2 below). Warning-tag is cleared to
> enable on Pixel; the OEM-variable re-alert should be re-run on a Samsung/Xiaomi if available.

1. **Undocumented FCM id behavior — VERIFIED on Pixel.** The never-woken win depends on two same-tagged
   notification-payloads — even across `door_open-` and `door_open_v2-` — replacing rather than
   stacking. Confirmed on Pixel via the sandbox (`--mode notification` then `--mode combined`, same
   tag, backgrounded → one card). Re-run on another OEM if the fleet includes Samsung/Xiaomi. If some
   OEM assigns per-message ids, cross-topic replacement degrades to two cards there (cosmetic, safe).
2. **Re-buzz on OS replace — confirmed on-device 2026-07-02; buzz accepted.** The OS-rendered resolved
   cannot set `setOnlyAlertOnce`. The Pixel gate confirmed that lowering `notification_priority`
   (PRIORITY_LOW) does **not** quiet it: on Android 8+ the HIGH "Garage door" channel importance
   overrides any per-notification priority, so the all-clear heads-up / buzzes. The maintainer
   **accepted the buzzing all-clear**, so the combined resolved uses **HIGH** delivery priority (like
   the warning) for reliable never-woken Doze delivery and sets no `notification_priority`. A silent
   all-clear would require a dedicated lower-importance channel (app-created + `channel_id` on the
   payload) — deferred, not built. The foreground app-built path is still silent (`setOnlyAlertOnce`).
3. **Time formatting in the background.** The OS-rendered resolved text is server-provided and cannot
   know the device timezone, so drop the local clock ("2:00-2:14 PM") from the background variant and
   keep the timezone-independent duration ("open for 14 minutes"). The foreground app-built path keeps
   the full local-time format.

### 9.5 Why relax A, not D

The two candidate constraints to trade are A (old-app cosmetics) and D (state-sync safety, which
Phase 2 relaxes). Relaxing A costs a neutral-or-improving stacking change with **zero reliability
loss**. Relaxing D risks **silently losing door state and warnings** and loses instant revert.
Relaxing A is strictly the better trade, and under A-prime it is not even a real cost. Phase 2 stays
parked.
