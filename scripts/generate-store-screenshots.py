#!/usr/bin/env python3
"""
Generate Play-Store-ready screenshots into a STAGING directory.

Mirrors the README framing flow (scripts/frame-screenshot.py): it composes
already-committed PNGs - it does NOT render, so it works on this machine even
though Layoutlib screenshot rendering is blank locally. Sources:

  * phone   -> the framed README shots in MobileGarage/screenshots/framed/
               (already wrapped in a Pixel bezel).
  * tablet  -> the CI-committed wide / 3-pane reference renders under the
               android-screenshot-tests reference dir.

Phone shots are flattened onto white at their native ~2.12:1 (Play accepted this
ratio, approved 2026-06-11 - do not pad). Tablet shots are wrapped in a
programmatic tablet bezel (frame_tablet) centered on a white 16:9 canvas, which
both polishes the shot and makes it Play-compliant.

Output goes to MobileGarage/screenshots/store/{phoneScreenshots,
sevenInchScreenshots,tenInchScreenshots}/. Copy the ones you want into
distribution/playstore/screenshots/ by hand when updating the store.

Usage: python3 scripts/generate-store-screenshots.py
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRAMED = os.path.join(REPO, "MobileGarage/screenshots/framed")
REF = os.path.join(
    REPO,
    "MobileGarage/android-screenshot-tests/src/screenshotTestDebug/reference",
)
OUT = os.path.join(REPO, "MobileGarage/screenshots/store")
BG = (0, 0, 0)

# (source, category, output-name, orientation)
#   source: a path under screenshots/framed/, or a recursive glob under the
#           reference dir (first match wins, so hash suffixes don't matter).
#   orientation: "portrait" -> 9:16, "landscape" -> 16:9
PHONE = "phoneScreenshots"
SEVEN = "sevenInchScreenshots"
TEN = "tenInchScreenshots"

SHOTS = [
    # Phone (framed README shots, padded to 9:16 portrait)
    ("framed:home_tab_light.png",     PHONE, "01_home_light.png",     "portrait"),
    ("framed:history_tab_light.png",  PHONE, "02_history_light.png",  "portrait"),
    ("framed:settings_tab_light.png", PHONE, "03_settings_light.png", "portrait"),
    ("framed:home_tab_dark.png",      PHONE, "04_home_dark.png",      "portrait"),
    ("framed:history_tab_dark.png",   PHONE, "05_history_dark.png",   "portrait"),
    ("framed:settings_tab_dark.png",  PHONE, "06_settings_dark.png",  "portrait"),

    # 7-inch tablet (wide dashboard + narrow 3-pane, padded to 16:9 landscape)
    ("ref:HomeDashboardPreview1024dpTest_Light_*.png",        SEVEN, "01_dashboard_light.png", "landscape"),
    ("ref:ThreePaneDashboardTabletNarrowPreviewTest_Light_*.png", SEVEN, "02_threepane_light.png", "landscape"),
    ("ref:HomeDashboardPreview1024dpTest_Dark_*.png",         SEVEN, "03_dashboard_dark.png",  "landscape"),
    ("ref:ThreePaneDashboardTabletNarrowPreviewTest_Dark_*.png",  SEVEN, "04_threepane_dark.png",  "landscape"),

    # 10-inch tablet (large 3-pane + 1280dp dashboard, padded to 16:9 landscape)
    ("ref:ThreePaneDashboardLargeTabletPreviewTest_Light_*.png", TEN, "01_threepane_light.png", "landscape"),
    ("ref:HomeDashboardPreview1280dpTest_Light_*.png",          TEN, "02_dashboard_light.png", "landscape"),
    ("ref:ThreePaneDashboardLargeTabletPreviewTest_Dark_*.png",  TEN, "03_threepane_dark.png",  "landscape"),
    ("ref:HomeDashboardPreview1280dpTest_Dark_*.png",           TEN, "04_dashboard_dark.png",  "landscape"),
]


def resolve(source):
    kind, val = source.split(":", 1)
    if kind == "framed":
        p = os.path.join(FRAMED, val)
        return p if os.path.exists(p) else None
    hits = sorted(glob.glob(os.path.join(REF, "**", val), recursive=True))
    return hits[0] if hits else None


def frame_tablet(screen, max_side):
    """Wrap a landscape tablet render in a programmatic tablet bezel, centered on
    a WHITE 16:9 canvas. The white margin both polishes the shot (a tablet on a
    studio background) and makes it exactly 16:9 / Play-compliant. Final image is
    downscaled so the long side is <= max_side. Mirrors the phone framer's
    body + shadow + rounded-screen approach (scripts/frame-screenshot.py)."""
    screen = screen.convert("RGBA")
    sw, sh = screen.size
    short = min(sw, sh)
    bezel = max(8, int(short * 0.022))         # thin, uniform on all sides
    inner_r = int(short * 0.03)                 # screen corner radius
    outer_r = inner_r + bezel
    bezel_color = (28, 28, 30, 255)
    body_w, body_h = sw + 2 * bezel, sh + 2 * bezel

    # White 16:9 canvas that contains the body plus a studio margin.
    margin = int(short * 0.08)
    min_w, min_h = body_w + 2 * margin, body_h + 2 * margin
    canvas_h = max(min_h, -(-min_w * 9 // 16))
    canvas_w = round(canvas_h * 16 / 9)
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (255, 255, 255, 255))
    bx, by = (canvas_w - body_w) // 2, (canvas_h - body_h) // 2

    # Soft drop shadow.
    shadow = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    off = int(short * 0.012)
    ImageDraw.Draw(shadow).rounded_rectangle(
        [bx, by + off, bx + body_w, by + body_h + off], radius=outer_r, fill=(0, 0, 0, 70))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(int(short * 0.02))))

    # Body.
    body = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle(
        [bx, by, bx + body_w, by + body_h], radius=outer_r, fill=bezel_color)
    canvas.alpha_composite(body)

    # Front camera dot, centered on the top bezel.
    cam_r = max(3, int(bezel * 0.22))
    cx, cy = bx + body_w // 2, by + bezel // 2
    ImageDraw.Draw(canvas).ellipse(
        [cx - cam_r, cy - cam_r, cx + cam_r, cy + cam_r], fill=(60, 60, 66, 255))

    # Rounded screen pasted inside the bezel.
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw, sh], radius=inner_r, fill=255)
    canvas.paste(screen, (bx + bezel, by + bezel), mask)

    if max(canvas.size) > max_side:
        scale = max_side / max(canvas.size)
        canvas = canvas.resize(
            (round(canvas_w * scale), round(canvas_h * scale)), Image.LANCZOS)
    return canvas.convert("RGB")


def flatten_white(src_path):
    """Load and flatten onto a WHITE background (Play needs opaque RGB; the
    framed phone shots have a transparent background, which would otherwise
    become black). Opaque tablet renders are unaffected."""
    img = Image.open(src_path).convert("RGBA")
    out = Image.new("RGB", img.size, (255, 255, 255))
    out.paste(img, mask=img.split()[3])
    return out


def write_gallery(by_cat):
    """Emit screenshots/store/README.md so the latest store images are always
    viewable on GitHub. Includes icon + feature graphic (produced by
    generate.sh) when present."""
    def imgs(paths, width):
        return " ".join(
            f'<img src="{p}" width="{width}" alt="{os.path.basename(p)}">'
            for p in paths
        )

    icon = "icon-512.png" if os.path.exists(os.path.join(OUT, "icon-512.png")) else None
    feat = ("feature-graphic-1024x500.png"
            if os.path.exists(os.path.join(OUT, "feature-graphic-1024x500.png")) else None)

    lines = [
        "# Play Store assets (generated — latest)",
        "",
        "Auto-generated; do not hand-edit. Regenerated by the screenshot pipeline",
        "(`./scripts/generate-android-screenshots.sh`) + "
        "`MobileGarage/distribution/playstore/generate.sh`.",
        "These are the **candidates**; the curated subset that's live in the store",
        "is copied by hand into `MobileGarage/distribution/playstore/` "
        "(see the `play-store-assets` skill).",
        "",
    ]
    if icon or feat:
        lines += ["## Icon & feature graphic", ""]
        if icon:
            lines += [f'<img src="{icon}" width="96" alt="app icon">', ""]
        if feat:
            lines += [f'<img src="{feat}" width="600" alt="feature graphic">', ""]
    titles = [
        ("phoneScreenshots", "Phone", 180),
        ("sevenInchScreenshots", "7-inch tablet", 360),
        ("tenInchScreenshots", "10-inch tablet", 360),
    ]
    for cat, title, width in titles:
        paths = sorted(f"{cat}/{n}" for n in by_cat.get(cat, []))
        if paths:
            lines += [f"## {title}", "", imgs(paths, width), ""]
    with open(os.path.join(OUT, "README.md"), "w") as f:
        f.write("\n".join(lines).rstrip() + "\n")


def main():
    made = 0
    by_cat = {}
    for source, cat, name, orient in SHOTS:
        src = resolve(source)
        outdir = os.path.join(OUT, cat)
        os.makedirs(outdir, exist_ok=True)
        if not src:
            print(f"  MISSING source for {cat}/{name}: {source}")
            continue
        # Phone: framed Pixel shot flattened onto white (native ratio).
        # Tablet: wrapped in a tablet bezel on a white 16:9 canvas.
        if orient == "landscape":
            out = frame_tablet(Image.open(src), 3840 if cat == SEVEN else 7680)
        else:
            out = flatten_white(src)
        dst = os.path.join(outdir, name)
        out.save(dst, optimize=True)
        w, h = out.size
        mb = os.path.getsize(dst) / 1_048_576
        cap = "" if max(w, h) <= 2 * min(w, h) else "  <-- exceeds 2:1"
        print(f"  {cat}/{name}: {w}x{h} ratio {max(w,h)/min(w,h):.3f} {mb:.2f}MB{cap}")
        by_cat.setdefault(cat, []).append(name)
        made += 1
    write_gallery(by_cat)
    print(f"\nWrote {made} screenshot(s) + README.md gallery to {OUT}")
    print("Review them, then copy the ones you want into "
          "MobileGarage/distribution/playstore/screenshots/ by hand.")


if __name__ == "__main__":
    sys.exit(main())
