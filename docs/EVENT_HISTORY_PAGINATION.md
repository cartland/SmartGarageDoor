---
category: reference
status: active
last_verified: 2026-06-10
---

# Event-history pagination (server contract)

How the `eventHistory` HTTP endpoint windows + paginates door events, and the
two load-bearing design decisions behind it. The Android consumer's side is
ADR-028 in [`AndroidGarage/docs/DECISIONS.md`](../AndroidGarage/docs/DECISIONS.md).

Shipped in `server/28` (PR #867) + Android PRs #869 (data) / #871 (UI).

## Behavior

- **Universal default:** every non-cursor request returns the **last 7 days of
  events, capped at 100** (newest first). This applies to *all* clients — on
  deploy, an un-updated app's history view windows to ~1 week. Wire-compatible:
  the response keeps every legacy key.
- **Cursor pagination into the past:** the response carries `nextPageToken`
  (older), `prevPageToken` (newer), and `hasMore`. The client passes
  `nextPageToken` back to fetch the next older page. A **null `nextPageToken`**
  is the end-of-history signal — it covers both "no events at all" and "you've
  reached the oldest event."
- **Page size:** `pageSize` (clamped to `[1, 100]`). The client also sends the
  legacy `eventHistoryMaxCount` as an alias so an old server still applies the
  limit (forward/backward-compatible deploy in any order).

## Request decision tree

In `handleEventHistory` (after the existing `buildTimestamp` 400 guard):

```
pageToken present + valid + scoped to this buildTimestamp
    -> CURSOR PAGE: startAfter(token), no time window, direction from token
pageSize / eventHistoryMaxCount present, no token
    -> WINDOWED PAGE: sinceTimestamp = now - 7d, limit = clamp(.. ,1,100)
else
    -> WINDOWED PAGE with the default page size (100)
```

`now` is injected from the thin wrapper (`Date.now()`) so the window is
deterministic in tests. Malformed or foreign-`buildTimestamp` tokens fall back
**leniently** to a fresh windowed first page — never a 400/500.

## Decision 1 — opaque token format

`base64url(JSON)` of:

```
{ v: 1, bt: <buildTimestamp>, s: <seconds>, n: <nanoseconds>, d: "older" | "newer" }
```

- `s` + `n` are the boundary doc's `FIRESTORE_databaseTimestamp` (a Firestore
  `Timestamp`, sub-second precision) — **not** the seconds-only field — so two
  events written in the same wall-clock second are never skipped at a page edge.
- `bt` scopes the token to one `buildTimestamp`; a token from another build is
  ignored (lenient fallback).
- `d` lets the same token mechanism page both directions. `nextPageToken`
  encodes the oldest returned doc with `d:"older"`; `prevPageToken` encodes the
  newest with `d:"newer"`.
- The client treats the whole string as **opaque** — internals can change under
  a version bump (`v`) without a client release.
- **No doc-id tiebreaker for v1.** Nanosecond collisions within one build's
  partition don't occur at this write rate. If they ever do, the fix is to add
  `__name__` to the `orderBy` + a composite index + the token — documented in
  `TimeSeriesDatabase.getPageForBuildTimestamp`.

## Decision 2 — no new index for the core path (Option B)

The 7-day window is expressed as a **range on the same field as the sort**:

```
where('buildTimestamp','==',bt)
  .where('FIRESTORE_databaseTimestamp','>=', cutoff)   // the window
  .orderBy('FIRESTORE_databaseTimestamp','desc')
```

Because the inequality field equals the first `orderBy` field, Firestore accepts
it and the **existing** `eventsAll: buildTimestamp ASC + FIRESTORE_databaseTimestamp DESC`
composite index serves the default + older-cursor + under-fill-probe queries.
**No new index, no index-before-deploy hazard** for the shipped feature.

`hasMore` is computed by fetching `limit + 1`; when a windowed first page
under-fills (or is empty) a cheap 1-doc `< cutoff` probe decides whether older
events exist beyond the window so the end signal is correct.

### One index IS added — for the `newer` direction

The `newer` (backward-toward-present) direction orders **ascending**, which the
DESC-only index doesn't cover. A composite index's sort direction is fixed at
build time: an `ASC` `orderBy` can't be served by a `DESC` index, so each
direction needs its own. `firestore.indexes.json` adds
`eventsAll: buildTimestamp ASC + FIRESTORE_databaseTimestamp ASC` for it
(alongside the long-existing `... DESC` index the older/default path reuses).

**Status (2026-06-10): the ASC index is DEPLOYED and the `newer` direction is
verified working in production** (returns 200 + newer events; `prevPageToken: null`
correctly signals "reached the present"). It stays **dormant on the client** —
the UI only scrolls older today; only `prevPageToken` ships as contract.

Indexes are NOT deployed by the functions release (`release-firebase.sh` runs
`firebase deploy --only functions`). If you change `firestore.indexes.json`
again, deploy the index separately — and note Firestore builds composite indexes
**asynchronously** (the deploy command returns before the build finishes; queries
needing a still-`CREATING` index return 500 until it's `READY`):

```
firebase deploy --only firestore:indexes
gcloud firestore indexes composite list --project escape-echo   # check CREATING vs READY
```

Creating an index never breaks existing queries, so this deploy is safe at any
time and independent of the functions deploy.

## Tests + fixtures

Shared wire-contract fixtures live in [`wire-contracts/eventHistory/`](../wire-contracts/eventHistory/)
(`response_first_page`, `response_last_page`, `response_empty`). Both the server
Mocha suite (`HttpEventsTest.ts`, `deep.equal`) and the Android Ktor test
(`KtorNetworkDoorDataSourceTest.kt`) load the same files, so a unilateral
key rename breaks at least one side. The fixture tokens are real
`encodePageToken` output, so a token-format change must regenerate them.
