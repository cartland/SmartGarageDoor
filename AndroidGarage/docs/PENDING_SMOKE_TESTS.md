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

1. **`android/230` / 2.16.16 — Home notification-permission banner copy.** When the app starts in a state where notification permission has not been granted, the Home banner now reads *"Turn on notifications to get alerted when the door is left open."* (was *"Please turn on notifications to be notified when the door is left open."*). Verify: (a) banner is 2 lines, not 3; (b) tapping the banner still launches the system permission prompt; (c) at attempt-count 3+ the appended hint *"You can manage permissions in the Android system settings."* still reads naturally below the new lead. To trigger: deny notification permission (or freshly install on Android 13+ before granting), open the app — banner is on Home above the door card.

## Open follow-ups (release-related but not smoke-test items)

- **Whatsnew accuracy.** `AndroidGarage/distribution/whatsnew/whatsnew-en-US` is currently triple-stale as of 2.16.11+:
  1. Says "side by side at 840dp+" but the threshold has been 1200dp since 2.16.7.
  2. Doesn't mention the left-rail in Wide mode (added 2.16.10).
  3. Doesn't mention the rail items being centered (changed 2.16.11).
  Per the `bump-android-version` skill, **patches don't touch whatsnew** — patches roll up into the next minor/major. Stays out of sync until a 2.17.x bump or until the user explicitly OKs an exception.
- **Other open follow-ups (Developer allowlist, user-visible-string migration)** are tracked in [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md).
