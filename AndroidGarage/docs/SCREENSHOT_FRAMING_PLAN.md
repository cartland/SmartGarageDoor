---
category: plan
status: active
---

# Screenshot Framing Plan

## TL;DR

`scripts/frame-screenshot.py` wraps Compose `@Preview` screenshots in a programmatic Pixel-style bezel for README / docs use. Six framed shots ship today (Home / History / DoorHistory × Light / Dark). README swap (Issue 4) is the only remaining work item.

## Background

Until 2026-04-25 the README's screenshots (`AndroidGarage/screenshots/home_closed.png`, `history.png`, `user.png`) were one-off real-device captures. They were checked in once, never refreshed, and silently drifted from the live app as the UI changed (~4 months stale by 2026-04-25).

The screenshot test pipeline already produces fresh `@Preview` PNGs on every `./scripts/generate-android-screenshots.sh` run. The framing step layers a bezel + status bar onto a curated subset of those PNGs and writes them to `AndroidGarage/screenshots/framed/`. The eventual goal is for the READMEs to point at the framed copies so they auto-update with the app.

For implementation detail (how the framing step fits into the pipeline, how to add or remove a preview, how to install Pillow) see `.claude/skills/update-android-screenshots/SKILL.md` and the inline docstring in `scripts/frame-screenshot.py`.

## Current state

Allowlist: `scripts/framed-screenshots.txt`. Six entries:

| Output | Source preview |
|---|---|
| `home_tab_light.png` | `HomeTabPreviewTest_Light` |
| `home_tab_dark.png` | `HomeTabPreviewTest_Dark` |
| `history_tab_light.png` | `HistoryTabPreviewTest_Light` |
| `history_tab_dark.png` | `HistoryTabPreviewTest_Dark` |
| `door_history_light.png` | `DoorHistoryScreenPreviewTest_Light` |
| `door_history_dark.png` | `DoorHistoryScreenPreviewTest_Dark` |

READMEs still point at the legacy manual mockups. Issue 4 (README swap) is now unblocked.

## Goal state

1. ✅ **Cover every primary tab** with a framed preview that has visible content. Done: Home + History.
2. ✅ **Both light and dark variants** for every framed tab.
3. ⏳ **READMEs reference the framed set.** Pending — Issue 4.
4. ✅ **Bezel + status bar overlay are good-enough fidelity.**
5. ✅ **Pipeline is one command.** `./scripts/generate-android-screenshots.sh`

## Shipped

### Issue 1 — Dark divider in `DoorHistoryScreenPreviewTest_Dark` ✅ #559

The 8.dp `Arrangement.spacedBy` gaps in the LazyColumn revealed the white `@Preview(showBackground = true)` backdrop instead of the dark theme surface. Fix: wrap `DoorHistoryContentPreview` body in `Surface(Modifier.fillMaxSize())` so the theme paints the background. Same pattern applied to `HomeContentPreview` and `ProfileContentPreview`.

### Issue 2 — `HomeTabPreviewTest` body empty ✅ #560

Same root cause as the `DoorStatusCardPreviewTest` 1×1 (Issue 3). `HomeContent` embeds `DoorStatusCard`; when DoorStatusCard's render crashed, the entire HomeTab body collapsed. Fixed by the icon workaround in #560.

### Issue 3 — Five 1×1 broken previews ✅ #559 + #560 + #562

Two of five (Profile + Settings light) were fixed by adding Surface wrapping in #559. The remaining three (DoorStatusCard×2 + HomeScreen×2) all collapsed because of a `painterResource` failure on `clock_icon` and `calendar_icon`. Initially worked around by `PreviewSafeIcon` in #560 (tinted placeholder Box) and #562 (Material `ImageVector` fallback). Fully resolved by switching production to Material Icons (`Icons.Filled.Timer` + `Icons.Filled.CalendarMonth`) so previews and production share the same render path — `painterResource` is no longer used in `DoorStatusCard`. The custom `clock_icon.xml` / `calendar_icon.xml` drawables were deleted with this change.

## Open

### Issue 4 — Switch READMEs to the framed set

Now unblocked. Switch:
- `README.md` line 16
- `AndroidGarage/README.md` line 11

…from `screenshots/home_closed.png` + `screenshots/history.png` to a coherent pair from `screenshots/framed/`. Suggested: `home_tab_light.png` + `door_history_light.png`.

After the swap, optionally delete the now-superseded manual mockups (`home_closed.png`, `history.png`, `user.png`) — they no longer match the live app and `git log` preserves history.

## Known limitation (no longer affects this app)

**The `com.android.compose.screenshot:0.0.1-alpha12` plugin's renderer (Layoutlib in screenshot-test mode) cannot load XML vector drawables via `painterResource(R.drawable.X)`.** It throws `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP` even when the resource IS a valid VectorDrawable.

Investigation summary (2026-04-26):
- Replacing the failing icon's path data with a hand-crafted simple path: still fails.
- Copying the EXACT content of a known-rendering drawable (`ic_garage_simple_closed.xml`) into `clock_icon.xml`: still fails.
- Converting CRLF line endings to LF: no effect.
- The `ic_garage_simple_*` drawables don't actually go through `painterResource` — they're rendered via Compose Canvas drawing primitives in `GarageIcon.kt`. So we have no proof that *any* `painterResource(R.drawable.X)` call works in this plugin version.
- The other `painterResource` call sites in the app (`baseline_cell_tower_24`, `outline_signal_disconnected_24` inside `OldLastCheckInBanner`) are never rendered in any current preview, so we can't observe whether they'd fail too.

Conclusion: the bug is in the screenshot-test plugin, not in our resources. The XMLs render correctly in production, in IDE Preview, and on a real device.

**Resolution (PR #pending).** `DoorStatusCard` now uses Material `Icons.Filled.Timer` and `Icons.Filled.CalendarMonth` — pure Compose `ImageVector` definitions that bypass resource loading. Production and screenshot tests render identically; the `PreviewSafeIcon` helper and the custom `clock_icon.xml` / `calendar_icon.xml` drawables were deleted. If a future preview test or screen needs to render an XML vector drawable, the plugin bug will resurface — track the screenshot-test plugin's release notes for `painterResource` fixes before reintroducing that pattern.

## Reviewing framed shots with an LLM

The framed PNGs are **1294×2744** (taller than the 2000px many-image limit Claude enforces in conversations). When asking an LLM to look at several framed shots in one turn, downsize first or it'll be rejected with "exceeds the dimension limit for many-image requests (2000px)".

```bash
# Quick downscale for review (one-off)
sips -Z 2000 AndroidGarage/screenshots/framed/*.png --out /tmp/framed-review/
```

Don't change the canonical output dimensions to fit the review limit — README rendering wants the full size. The constraint only applies to inline image attachments in chat.

## Out of scope

- **Camera punch-hole / notch.** Adds ~10 lines to `frame-screenshot.py`. Skipped because the bezel already reads as "phone" and the notch adds clutter.
- **Real Pixel device-frame asset.** Could swap programmatic drawing for a transparent PNG of a real Pixel chassis. Higher visual fidelity, costs a binary asset + sourcing decision. Revisit only if "good enough" stops being good enough.
- **Status-bar fidelity.** Hard-coded `9:41` + 4 signal bars + Wi-Fi + ~80% battery.
- **Animations.** Compose previews that depend on `rememberInfiniteTransition` / `Animatable` already render at an arbitrary frame; SKILL.md documents the `static = true` workaround.

## Working agreement

When picking up an issue from this doc:

1. Open a small focused PR per issue.
2. After resolving an issue, update `scripts/framed-screenshots.txt` if a new shot is now available, regenerate the framed PNG, and commit it alongside the fix.
3. Move shipped issues to the `## Shipped` section with the PR number rather than deleting — preserves the trail.
