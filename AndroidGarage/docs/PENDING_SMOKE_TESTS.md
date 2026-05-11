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

1. **`android/237` / 2.16.23 — Settings → Developer → "Nav rail items" picker.** New developer-only picker chooses where the Wide-mode (≥600dp, <1200dp) NavigationRail's tab items sit vertically. Two options: `Centered vertically` (default — current production behavior) and `Top-aligned`. Persists in DataStore. To verify on a real device: (a) the picker row + 2 RadioButton choices appear inside the Developer section only when developer-allowlisted; (b) selection is immediate AND persists across app restart (kill app → reopen → state preserved); (c) `Centered vertically` matches pre-2.16.23 behavior; (d) `Top-aligned` — toggle and visually compare: the rail items' first visible pixel should land in the same horizontal band as the body's first content row (e.g. the "ACCOUNT" section header on Settings, or the "Status" header on Home). The pixel-alignment claim is the whole point of shipping the option — this is the verification gap the screenshot fixture intentionally doesn't try to cover (Layoutlib's text rendering doesn't match a device closely enough); (e) the setting only affects Wide mode — confirm Compact (bottom bar visible, no rail) and Expanded (1200dp+, no nav chrome) are unchanged.

## Open follow-ups (release-related but not smoke-test items)

> **None.** Feature follow-ups live in [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md). The Play Store whatsnew file is rolling and refreshed by the `bump-android-version` skill on every minor/major — staleness during a patch series is expected and self-resolves on the next minor.
