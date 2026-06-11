# Play Store listing assets

Garage-door identity for the Google Play listing and the on-device launcher
icon. The mark is the **app's own closed garage door** — the same art drawn in
Compose (`androidApp/.../ui/GarageDoorCanvas.kt`, `doorOffset = CLOSED_POSITION`)
— in its green (`closedFresh`, `#226B43`) on a light-green ground.

These are **manual uploads** in the Play Console — the release workflow
(`release-android.yml`) only ships the AAB + `whatsnew/`, it does not push store
graphics.

## Files

| File | Play Console field | Spec |
| --- | --- | --- |
| `icon-512.png` | App icon (hi-res) | 512×512 PNG, 32-bit |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 PNG/JPG |

Screenshots are not generated here yet (see _Follow-ups_).

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
# macOS only (uses Quick Look + sips + Pillow)
./generate.sh
```

`generate.sh` rewrites `icon-512.png`, `feature-graphic-1024x500.png`, and the
legacy raster launcher mipmaps (`androidApp/.../res/mipmap-*/ic_launcher*.png`).

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

## Follow-ups

- **Screenshots**: the listing also wants 2–8 phone screenshots. The framed
  device shots in `AndroidGarage/screenshots/framed/` are the natural source,
  but local screenshot regeneration renders blank on this machine (see the
  root `CLAUDE.md` screenshot note), so capturing store screenshots is deferred
  to a working environment / CI artifact.
- **`AppIconClosedDoorPreviewTest` reference PNG**: same local-render limitation
  — its reference image is generated on a working environment / in CI, not here.
