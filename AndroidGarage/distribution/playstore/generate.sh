#!/usr/bin/env bash
#
# Regenerate Play Store + launcher raster assets from the SVG sources in
# distribution/playstore/src/. Deterministic: edit the SVG, re-run this.
#
# Requires macOS (uses Quick Look `qlmanage` to rasterize SVG, then `sips`).
# Outputs:
#   distribution/playstore/icon-512.png               (Play hi-res icon)
#   distribution/playstore/feature-graphic-1024x500.png (Play feature graphic)
#   androidApp/src/main/res/mipmap-*/ic_launcher.png      (legacy raster, square)
#   androidApp/src/main/res/mipmap-*/ic_launcher_round.png (legacy raster, round)
#
# On API 26+ (this app's minSdk) the adaptive icon in mipmap-anydpi-v26/ is
# what renders; the raster mipmaps are a hygiene fallback only.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/src"
RES="$HERE/../../androidApp/src/main/res"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

render() { # svg out_png size
  qlmanage -t -s "$3" -o "$TMP" "$1" >/dev/null 2>&1
  sips -z "$3" "$3" "$TMP/$(basename "$1").png" --out "$2" >/dev/null
}

echo "==> Play Store hi-res icon (512x512)"
render "$SRC/icon.svg" "$HERE/icon-512.png" 512

echo "==> Play Store feature graphic (1024x500)"
# Pillow, not qlmanage: qlmanage rasterizes SVG onto a square canvas, which
# letterboxes + squashes a 1024x500 design. feature_graphic.py draws at exact
# target dimensions and reuses the icon-512 we just rendered.
python3 "$SRC/feature_graphic.py" "$HERE/icon-512.png" "$HERE/feature-graphic-1024x500.png"

echo "==> Launcher raster mipmaps (square + round)"
# density -> px (launcher icon base 48dp)
sizes=("mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192")
# master square render at high res, then downscale per density
qlmanage -t -s 512 -o "$TMP" "$SRC/icon.svg" >/dev/null 2>&1
MASTER="$TMP/icon.svg.png"
# build a circular-masked master for the round variant
python3 - "$MASTER" "$TMP/round.png" <<'PY'
import sys
try:
    from PIL import Image, ImageDraw
except Exception:
    # Pillow not available: fall back to square for round too
    import shutil; shutil.copy(sys.argv[1], sys.argv[2]); sys.exit(0)
src = Image.open(sys.argv[1]).convert("RGBA")
w, h = src.size
mask = Image.new("L", (w, h), 0)
ImageDraw.Draw(mask).ellipse((0, 0, w, h), fill=255)
out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
out.paste(src, (0, 0), mask)
out.save(sys.argv[2])
PY
for entry in "${sizes[@]}"; do
  set -- $entry; dens="$1"; px="$2"
  mkdir -p "$RES/mipmap-$dens"
  sips -z "$px" "$px" "$MASTER" --out "$RES/mipmap-$dens/ic_launcher.png" >/dev/null
  sips -z "$px" "$px" "$TMP/round.png" --out "$RES/mipmap-$dens/ic_launcher_round.png" >/dev/null
done

echo "Done. Review the PNGs, then commit."
