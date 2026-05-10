---
category: plan
status: active
last_verified: 2026-05-10
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

## Cumulative queue (8 items, last updated 2026-05-10 after `android/225` / 2.16.11)

Recommend testing all of these in one device session — most can be exercised in a few minutes by rotating, tapping, and watching the network log.

1. **Camera-cutout safe area (2.16.4 / `android/218`)** — content stays clear of side cutouts in landscape on a hole-punch phone.
2. **Loading-flicker fixes (2.16.5 / `android/219`)** — cold-launch and tab-nav both show the actual state immediately; no UNKNOWN→actual door animation, no Checking pill flash, no empty-list flash.
3. **Button-health 1-min cadence + OFFLINE label (2.16.6 / `android/220` + `server/25`)** — unplug ESP32 button device. Within ~1 min the OFFLINE FCM should arrive and the pill should read "Unavailable · last seen X ago". Plug back in: pill recovers within ~2 sec via the firestore trigger.
4. **Pull-to-refresh on Home (2.16.6 / `android/220`)** — pull down on Home and verify BOTH door event AND button-health pill re-fetch (Cloud Logs should show both endpoints hit on the same gesture).
5. **3-pane threshold (2.16.7 / `android/221`)** — Pixel 9 Pro in landscape (~916dp) should now render 2-pane Wide. Tablet in landscape (≥1280dp) still renders 3-pane.
6. **Info bottom sheets (2.16.8 / `android/222` + 2.16.9 / `android/223` strings)** — tap Status pill: opens "Door status" sheet (lowercase 's', 2 paragraphs, no em dash). Tap Remote control pill: opens "Remote control" sheet (2 paragraphs, no em dash). Outside-tap and drag-down both dismiss. Sheet scrolls if viewport is short (landscape phone, large font scale).
7. **Navigation rail in Wide mode (2.16.10 / `android/224`)** — rotate Pixel 9 Pro to landscape (~916dp): bottom bar disappears, `NavigationRail` appears on the start edge with Home + Settings tabs, Home highlighted. Tap Settings: rail item highlights, content swaps. Rotate back to portrait: rail disappears, bottom bar returns. Verify on a hole-punch phone in landscape: no double-padding on the rail-side cutout (rail items shifted inward, content not padded again).
8. **Rail items vertically centered (2.16.11 / `android/225`)** — in landscape, the Home + Settings rail items should sit at the rail's vertical midpoint, not at the top. The bottom-bar version (Compact / portrait) is unaffected and still shows tabs anchored at the bottom.

## Open follow-ups (release-related but not smoke-test items)

- **Whatsnew accuracy.** `AndroidGarage/distribution/whatsnew/whatsnew-en-US` is currently triple-stale as of 2.16.11:
  1. Says "side by side at 840dp+" but the threshold has been 1200dp since 2.16.7.
  2. Doesn't mention the left-rail in Wide mode (added 2.16.10).
  3. Doesn't mention the rail items being centered (changed 2.16.11).
  Per the `bump-android-version` skill, **patches don't touch whatsnew** — patches roll up into the next minor/major. Stays out of sync until a 2.17.x bump or until the user explicitly OKs an exception.
- **Developer allowlist flag** — see `~/.claude/projects/.../memory/project_developer_allowlist_pending.md`. Server + Android work, separate from the 2.16.x sequence.
- **Home permission banner copy revision** — see `~/.claude/projects/.../memory/project_home_permission_banner_copy.md`.
