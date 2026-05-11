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

1. **`android/240` / 2.16.26 — Settings → Developer → "Nav rail" consolidated row + bottom sheet, defaults Top-aligned + 8 dp.** Supersedes the in-progress smoke entries for 2.16.23 (item-position picker) and 2.16.25 (top-padding stepper) — those inline UX iterations rolled into a single bottom sheet here. To verify on a real device:
   - **Fresh install (no prior overrides), Wide layout (≥600dp, <1200dp)**: rail items align with body's first content row out of the box. Specifically, the **selected item's indicator pill top edge** (the rounded blue background drawn behind the icon when an item is selected) sits at the same y as the body's first content row's top (e.g. the "ACCOUNT" section header on Settings, "Status" on Home). No user action needed — defaults are `Top-aligned` + `8 dp`.
   - **Settings menu length**: Settings → Developer shows ONE "Nav rail" row (not 3 rows). Subtitle reads e.g. `Top-aligned · 8 dp`.
   - **Bottom sheet**: tap the row → modal sheet opens with two sections (Item position / Top padding), each with its own `↺` reset icon in the section header.
   - **Item position**: changing between "Centered vertically" and "Top-aligned" updates the rail immediately. Reset (`↺`) on the position section snaps to `Top-aligned`.
   - **Top padding**: `−` and `+` step by 1 dp (range 0–64); `−` disabled at 0, `+` disabled at 64. Reset (`↺`) on the padding section snaps to `8 dp`.
   - **Reset isolation**: each reset only affects its own section.
   - **Persistence**: values survive kill-and-relaunch; existing users upgrading from 2.16.25 (or earlier) keep any explicitly-set values through the upgrade and DO NOT reset to the new defaults.
   - **Other layout modes**: Compact (bottom bar, no rail) and Expanded (1200dp+, no nav chrome) unaffected by any combination of values.
2. **`android/241` / 2.16.27 — Module-extraction refactor (`:viewmodel`).** Pure refactor — no user-visible change expected. The 5 screen-scoped ViewModels (Home / DoorHistory / Profile / FunctionList / Diagnostics) moved from `:usecase` to a new `:viewmodel` Gradle module. The risk surface is DI wiring + KSP code generation in `AppComponent` (kotlin-inject) — `ComponentGraphTest` instrumented test covers it in CI but a real-device smoke is the final gate. To verify: (a) install over 2.16.26 (or fresh) → app launches, **no crash on cold start** (a missing DI binding would manifest as `IllegalStateException` from `InjectAppComponent` during `MainActivity.onCreate`); (b) navigate to all 5 tabs / sub-screens — Home, History, Settings, Diagnostics (Settings → Developer → Nav rail still works from 2.16.26), Function list — and confirm each renders content without a blank/error state; (c) trigger one user action per screen that exercises the VM (Home: tap remote button; History: pull-to-refresh; Settings: open Snooze sheet then save; Diagnostics: tap Export CSV; Function list: tap a function row). If any screen blanks or crashes on first open, that's a missed DI binding — file an issue and revert to `android/240`.

## Open follow-ups (release-related but not smoke-test items)

> **None.** Feature follow-ups live in [`PENDING_FOLLOWUPS.md`](./PENDING_FOLLOWUPS.md). The Play Store whatsnew file is rolling and refreshed by the `bump-android-version` skill on every minor/major — staleness during a patch series is expected and self-resolves on the next minor.
