---
category: reference
status: active
last_verified: 2026-06-11
---

# Play Store listing assets

Garage-door identity for the Google Play listing and the on-device launcher
icon. The mark is the **app's own closed garage door** — the same art drawn in
Compose (`androidApp/.../ui/GarageDoorCanvas.kt`, `doorOffset = CLOSED_POSITION`)
— in its green (`closedFresh`, `#226B43`) on a light-green ground.

This directory is the **curated set we keep in sync with the live store**. The
generators do NOT write here — they write to the committed generated dir
(`AndroidGarage/screenshots/store/`) and you copy the images you want into this
directory by hand, PR them, then upload them manually in the Play Console. Both
dirs are committed. **The full procedure is the `play-store-assets` skill**
(`/play-store-assets`).

These are **manual uploads** — the release workflow (`release-android.yml`) only
ships the AAB + `whatsnew/`, it does not push store graphics.

## Curated files (what's live in the store)

| File | Play Console field | Spec |
| --- | --- | --- |
| `icon-512.png` | App icon (hi-res) | 512×512 PNG, 32-bit |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 PNG/JPG |
| `screenshots/phoneScreenshots/*.png` | Phone screenshots | 2–8, 9:16 |
| `screenshots/sevenInchScreenshots/*.png` | 7-inch tablet | ≤8, 16:9 |
| `screenshots/tenInchScreenshots/*.png` | 10-inch tablet | ≤8, 16:9 |

Populate `screenshots/` by copying from staging when you update the store; only
`icon-512.png` + `feature-graphic-1024x500.png` are committed today.

## Sources & regeneration

Everything is generated from the sources in `src/` — never hand-edit the PNGs.

- `src/icon.svg` — a faithful port of `GarageDoorCanvas.kt`: the U-frame, four
  gradient panels, and handle in the Canvas 300-unit design space, scaled into
  the 512 canvas. Also the source of truth for the in-app adaptive launcher icon
  (`ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` re-express the
  same geometry at the 108dp viewport via `v -> v*0.2333 + 19`).
- `src/feature_graphic.py` — draws the 1024×500 banner (gradient + icon chip +
  wordmark) with Pillow.

```bash
# macOS only (uses Quick Look + sips + Pillow). Run from repo root.
bash AndroidGarage/distribution/playstore/generate.sh
```

`generate.sh` writes `icon-512.png` + `feature-graphic-1024x500.png` into the
committed generated dir (`AndroidGarage/screenshots/store/`), and regenerates the
in-app launcher mipmaps in `androidApp/.../res/mipmap-*/ic_launcher*.png` (those
are app code — committed and shipped in the AAB, not store uploads).

**Screenshots regenerate automatically** with the normal screenshot update
(`./scripts/generate-android-screenshots.sh`, i.e. the `update-android-screenshots`
flow), which now runs `scripts/generate-store-screenshots.py` after framing. So
the generated `screenshots/store/` set stays fresh on its own; copying the subset
you want into this directory is the only manual step (see the `play-store-assets`
skill).

### Keeping the icon in sync with the app

The icon art is a **hand port** of `GarageDoorCanvas.kt`, so a change to the
Canvas drawing won't auto-update the icon. The screenshot fixture
`AppIconClosedDoorPreviewTest` (in `android-screenshot-tests/`) renders the real
`GarageDoorCanvas` closed door at the icon framing — diff it against `icon-512.png`
to catch drift, and re-port + re-run `generate.sh` if the Canvas changes.

## Why the launcher icon lives in two places

`minSdk = 26`, so on every supported device the **adaptive icon** in
`mipmap-anydpi-v26/` renders (background `ic_launcher_background` = light green
`#D7E8CE`, foreground `ic_launcher_foreground`, themed `ic_launcher_monochrome`).
The raster `mipmap-*/ic_launcher*.png` files are a dead-code fallback for
API < 26 — regenerated for hygiene only. (They previously still held the stock
Android Studio robot template, which is why nobody noticed.)

## Notes

- **Screenshots** (phone + 7"/10" tablet) are staged by
  `scripts/generate-store-screenshots.py` from the committed framed shots +
  tablet reference renders. See the `play-store-assets` skill for the full flow.
- **Refreshing the screenshot *sources*** (re-rendering the app UI) needs a
  working environment / CI — local screenshot regeneration renders blank on this
  machine (root `CLAUDE.md`). The staging generator only re-composes the
  currently-committed sources.
- **`AppIconClosedDoorPreviewTest` reference PNG**: same local-render limitation
  — its reference image is generated on a working environment / in CI, not here.
