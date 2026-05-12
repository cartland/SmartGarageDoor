---
category: plan
status: active
last_verified: 2026-05-12
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

1. **`android/241+1` (PR #810) / 2.16.28 — Settings polish: Nav rail "Set default" button, no preselected Snooze radio, consistent Diagnostics buttons.** To verify on a real device:
   - **Nav rail bottom sheet**: Settings → Developer → tap "Nav rail". Each section header now shows a `Set default` text button on the right (replaces the prior `↺` icon). Tap each — section snaps to its default. Item-position list shows `Top-aligned` on top, `Centered vertically` below (default first).
   - **Snooze bottom sheet**: Settings → tap Snooze row. Sheet opens with **no radio selected**. Save button is disabled. Tap any radio (including "Don't snooze") → Save enables. Save commits the choice and dismisses the sheet.
   - **Diagnostics buttons**: Settings → About → Diagnostics. All three buttons (`Copy auth token`, `Export CSV`, `Clear all diagnostics`) are now outlined with consistent styling. Only `Clear all` shows the destructive red tint. No filled primary button.
2. **`android/242` / 2.16.29 — Spacing rule unification: container owns the gap.** Pure visual tidy-up. To verify on a real device:
   - **Home with no alerts**: the `STATUS` section header sits at a comfortable distance below the TopAppBar (16 dp). Open and close the door (or wait for an alert to dismiss) until alerts are cleared.
   - **Home with an alert**: trigger a permission/staleness alert if available, OR observe in any state where an `HomeAlertCard` is the first item. The gap between the alert card and the `STATUS` header is the same as the gap between any two sections (16 dp). The `STATUS` header should NOT visibly jump up/down relative to the screen when alerts appear/disappear — it should sit at the same y in both cases (because the `safeListContentPadding.top` 16 dp + the spacedBy 16 dp + the card height = consistent rhythm).
   - **Settings**: scroll through. Inter-section spacing feels uniform; first section header sits a comfortable distance below the TopAppBar. No section's header sits visibly tighter or looser than the others.
   - **History**: same expectation — uniform 16 dp between day sections.
   - **Diagnostics**: gap between the counters surface and the action button stack is now larger (16 dp instead of 8 dp). Should feel less crowded.
   - **Function list**: gap between every button row is now 16 dp instead of 8 dp. List is taller; flicks more open. Same content, more breathable.
   - If any screen feels visibly tighter than the others or any section's header sits at an inconsistent y, file an issue — the rule is uniform 16 dp on every screen-level scrollable.
   - **Reference PNGs are stale on main** — local Layoutlib regen produced blank PNGs on this Mac. Smoke is the verification gate; PNGs will be regenerated on a working environment in a follow-up PR.

## Open follow-ups (release-related but not smoke-test items)

> **None.** Feature follow-ups live in [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md). The Play Store whatsnew file is rolling and refreshed by the `bump-android-version` skill on every minor/major — staleness during a patch series is expected and self-resolves on the next minor.
