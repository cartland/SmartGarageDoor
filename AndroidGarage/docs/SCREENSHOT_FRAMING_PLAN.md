---
category: plan
status: active
---

# Screenshot Framing Plan

## TL;DR

`scripts/frame-screenshot.py` wraps Compose `@Preview` screenshots in a programmatic Pixel-style bezel for README / docs use. It currently ships a curated 3-shot set; the goal is broader coverage but several preview-side issues block additions. This doc tracks the open issues, the goal state, and the order to attack them.

## Background

Until 2026-04-25 the README's screenshots (`AndroidGarage/screenshots/home_closed.png`, `history.png`, `user.png`) were one-off real-device captures. They were checked in once, never refreshed, and silently drifted from the live app as the UI changed (~4 months stale by 2026-04-25).

The screenshot test pipeline already produces fresh `@Preview` PNGs on every `./scripts/generate-android-screenshots.sh` run. The framing step layers a bezel + status bar onto a curated subset of those PNGs and writes them to `AndroidGarage/screenshots/framed/`. The eventual goal is for the READMEs to point at the framed copies so they auto-update with the app.

For implementation detail (how the framing step fits into the pipeline, how to add or remove a preview, how to install Pillow) see `.claude/skills/update-android-screenshots/SKILL.md` and the inline docstring in `scripts/frame-screenshot.py`.

## Current state

Allowlist: `scripts/framed-screenshots.txt`. Three entries:

| Output | Source preview | Status |
|---|---|---|
| `door_history_light.png` | `DoorHistoryScreenPreviewTest_Light` | Shipping |
| `history_tab_light.png` | `HistoryTabPreviewTest_Light` | Shipping |
| `history_tab_dark.png` | `HistoryTabPreviewTest_Dark` | Shipping |

READMEs still point at the legacy manual mockups. They will not be switched to the framed set until the open issues below are resolved enough to give the README a coherent before/after pair (a Home shot + a History shot).

## Goal state

1. **Cover every primary tab** with a framed preview that has visible content. Today: only History. Missing: Home, Settings, Profile (if present), and the standalone DoorHistoryScreen.
2. **Both light and dark variants** for every tab. Today: only History has both.
3. **READMEs reference the framed set.** Today: still legacy manual mockups.
4. **Bezel + status bar overlay are good-enough fidelity.** Today: rendered programmatically; readable but not a pixel-perfect Pixel render. Probably stays at "good enough" — don't over-invest.
5. **Pipeline is one command.** Today: `./scripts/generate-android-screenshots.sh` already runs framing as a step. ✓ Already met.

## Open issues to investigate

### Issue 1 — `DoorHistoryScreenPreviewTest_Dark` shows white separators between cards

**Symptom.** The raw 1080×2400 dark-mode preview renders white horizontal strips between cards (lum=250 at y=305, 631, 957, …). The framed output preserves them, since the framing script does not modify pixel content.

**Hypothesis.** A divider, list separator, or container background in `DoorHistoryScreen` is using a hard-coded color (likely `Color.White` or the light-theme surface) instead of `MaterialTheme.colorScheme.surface` / `colorScheme.outline`. The bug is in the screen composable, not the preview test or the framing script.

**Investigation.**
1. Open `DoorHistoryScreen` (find via `grep -rn "DoorHistoryScreen" AndroidGarage/`).
2. Look for hard-coded `Color(0xFF...)`, `Color.White`, `Modifier.background(Color...)`. Anything not flowing from `MaterialTheme.colorScheme` is a candidate.
3. Verify on a physical/emulator device in dark mode whether real users see the same white strips, or whether it's preview-only (e.g. a default `Surface` color flowing in only in preview rendering).

**Resolution gates this allowlist entry.** Add `DoorHistoryScreenPreviewTest_Dark_*.png  door_history_dark.png` back to `scripts/framed-screenshots.txt` once the dark variant looks right.

### Issue 2 — `HomeTabPreviewTest` body is empty (no door + button + diagram)

**Symptom.** Framed output shows only the title bar at top and the bottom navigation; the entire middle is blank.

**Hypothesis.** The Home tab's body composable depends on backend state (`DoorViewModel`, `RemoteButtonViewModel`, current door event, FCM-derived state) and the preview wrapper doesn't inject any of it. The preview likely calls a stateful `HomeTab()` composable that defaults to no-data-yet rendering when no ViewModel is present.

**Investigation.**
1. Find `HomeTabPreviewTest` (in `AndroidGarage/android-screenshot-tests/src/screenshotTest/kotlin/com/chriscartland/garage/screenshottests/`).
2. Compare with `DoorHistoryScreenPreviewTest` — that one **does** render content because it injects fake events. Use the same pattern: introduce a stateless `HomeTabContent(state, lambdas)` and feed it a fixture of door events + FCM status + button state.
3. Confirm `HomeTab()` already has a stateless overload in production code; if not, extracting one is itself a small refactor.

**Resolution gates the Home-tab framed shots.** Once the body renders, add back to the allowlist.

### Issue 3 — `HomeScreenPreviewTest` (and ~4 others) render at 1×1

**Symptom.** Five entries in `AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference/**/*.png` come out as 1×1 PNG. Pre-existing — not introduced by this work.

**Hypothesis.** Most likely cause per `.claude/skills/update-android-screenshots/SKILL.md`: previews that depend on `rememberAppComponent()` render blank in screenshot tests. The fix is to use stateless overloads (same pattern as Issue 2).

**Investigation.**
1. List the five 1×1 previews:
   ```bash
   python3 -c "
   from PIL import Image
   import glob
   for p in glob.glob('AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference/**/*.png', recursive=True):
       if Image.open(p).size == (1, 1):
           print(p)
   "
   ```
2. For each, check whether the preview test calls `rememberAppComponent()` or a similar DI-graph dependency.
3. Replace with stateless overloads + fake state.

**Lower priority** than 1 and 2 because the framing pipeline doesn't currently depend on these — they're not in the allowlist. Worth fixing for screenshot-tracking purposes regardless.

### Issue 4 — Switch READMEs to the framed set

Once Issue 1 + Issue 2 are resolved, switch:

- `README.md` line 16
- `AndroidGarage/README.md` line 11

…from `screenshots/home_closed.png` + `screenshots/history.png` to `screenshots/framed/<home>.png` + `screenshots/framed/door_history_light.png` (or whatever the curated pair becomes). Optionally, delete the now-superseded manual mockups (`home_closed.png`, `history.png`, `user.png`) since they no longer match the live app and `git log` preserves history.

## Out of scope

These were considered and intentionally deferred:

- **Camera punch-hole / notch.** Adds ~10 lines to `frame-screenshot.py`. Skipped because the bezel already reads as "phone" and the notch adds clutter without conveying information.
- **Real Pixel device-frame asset.** Could swap programmatic drawing for a transparent PNG of a real Pixel chassis. Higher visual fidelity, costs a binary asset + sourcing decision. Revisit if "good enough" stops being good enough.
- **Status-bar fidelity.** Hard-coded `9:41` + 4 signal bars + Wi-Fi + ~80% battery. No reason to make it dynamic for README screenshots.
- **Animations.** Compose previews that depend on `rememberInfiniteTransition` / `Animatable` already render at an arbitrary frame; SKILL.md documents the `static = true` workaround. Unrelated to framing.

## Working agreement

When picking up an issue from this doc:

1. Open a small focused PR per issue. Don't bundle the dark-divider fix with the Home-tab content fix.
2. After resolving Issue 1 or Issue 2, update `scripts/framed-screenshots.txt` to add the corresponding entry, regenerate the framed PNG, and commit it alongside the fix.
3. Strike the issue from this doc once shipped (move it to the bottom under a `## Shipped` section, with PR number, rather than deleting — preserves the trail).
