# Play Store listing assets

Brand-green garage-door identity for the Google Play listing and the on-device
launcher icon. These are **manual uploads** in the Play Console — the release
workflow (`release-android.yml`) only ships the AAB + `whatsnew/`, it does not
push store graphics.

## Files

| File | Play Console field | Spec |
| --- | --- | --- |
| `icon-512.png` | App icon (hi-res) | 512×512 PNG, 32-bit |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 PNG/JPG |

Screenshots are not generated here yet (see _Follow-ups_).

## Sources & regeneration

Everything is generated from the sources in `src/` — never hand-edit the PNGs.

- `src/icon.svg` — the garage-door mark (cream slatted door + window row on the
  brand green `#466730`). This is also the source of truth for the in-app
  adaptive launcher icon; the Android vector drawables
  (`androidApp/.../res/drawable/ic_launcher_foreground.xml` and
  `ic_launcher_monochrome.xml`) are the same art re-expressed at the 108dp
  adaptive viewport with safe-zone padding.
- `src/feature_graphic.py` — draws the 1024×500 banner (gradient + icon chip +
  wordmark) with Pillow.

```bash
# macOS only (uses Quick Look + sips + Pillow)
./generate.sh
```

`generate.sh` rewrites `icon-512.png`, `feature-graphic-1024x500.png`, and the
legacy raster launcher mipmaps (`androidApp/.../res/mipmap-*/ic_launcher*.png`).

## Why the launcher icon lives in two places

`minSdk = 26`, so on every supported device the **adaptive icon** in
`mipmap-anydpi-v26/` renders (background `ic_launcher_background` = flat
`#466730`, foreground `ic_launcher_foreground`, themed `ic_launcher_monochrome`).
The raster `mipmap-*/ic_launcher*.png` files are a dead-code fallback for
API < 26 — regenerated for hygiene only. (They previously still held the stock
Android Studio robot template, which is why nobody noticed.)

## Follow-ups

- **Screenshots**: the listing also wants 2–8 phone screenshots. The framed
  device shots in `AndroidGarage/screenshots/framed/` are the natural source,
  but local screenshot regeneration renders blank on this machine (see the
  root `CLAUDE.md` screenshot note), so capturing store screenshots is deferred
  to a working environment / CI artifact.
