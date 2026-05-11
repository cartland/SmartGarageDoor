---
category: plan
status: active
last_verified: 2026-05-11
---

# Pending Smoke Tests

User-facing changes that have shipped to the **internal Play Store track** but not yet been smoke-tested on a real device. Each item is one cohesive scenario to verify on hardware.

**Scope of this list:** Android-only. Server-side changes that ride along with an Android release (e.g. server pubsub cadence) are noted on the Android item that depends on them.

**Workflow:**

1. When a release ships to internal track, append a row here with the version, the scenario to verify, and a short reproduction.
2. When the user smoke-tests the device, they (or the agent on request) checks items off and removes them from this list.
3. If a smoke test fails, file a GitHub issue and reference it here; remove from this list once the issue is filed (don't dual-track).
4. Items roll up by user-facing version, not by every patch — if 2.16.10 and 2.16.11 ship two versions of the same scenario, list the latest one with a note covering both.

**Why this lives in the repo, not memory:** the smoke queue is project-specific TODO state. Agents should not write it to memory (see `feedback_dump_context_repo_first.md`); the user maintains it across sessions and across machines.

## Cumulative queue

> **Empty.** Queue cleared 2026-05-11 — items 1–7 (`android/230` 2.16.16 through `android/236` 2.16.22) all smoke-tested on real device with no regressions. Add new items as releases ship to the internal track.

## Open follow-ups (release-related but not smoke-test items)

- **Whatsnew accuracy.** `AndroidGarage/distribution/whatsnew/whatsnew-en-US` is currently triple-stale as of 2.16.11+:
  1. Says "side by side at 840dp+" but the threshold has been 1200dp since 2.16.7.
  2. Doesn't mention the left-rail in Wide mode (added 2.16.10).
  3. Doesn't mention the rail items being centered (changed 2.16.11).
  Per the `bump-android-version` skill, **patches don't touch whatsnew** — patches roll up into the next minor/major. Stays out of sync until a 2.17.x bump or until the user explicitly OKs an exception.
- **Other open follow-ups (user-visible-string migration)** are tracked in [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md). Both server (`server/26`, 2026-05-10) and Android (`android/235`, 2026-05-11) sides of the Developer-allowlist flag have shipped.
