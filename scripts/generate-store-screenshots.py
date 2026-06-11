#!/usr/bin/env python3
"""
Generate Play-Store-ready screenshots into a STAGING directory.

Mirrors the README framing flow (scripts/frame-screenshot.py): it composes
already-committed PNGs - it does NOT render, so it works on this machine even
though Layoutlib screenshot rendering is blank locally. Sources:

  * phone   -> the framed README shots in AndroidGarage/screenshots/framed/
               (already wrapped in a Pixel bezel), padded to 9:16.
  * tablet  -> the CI-committed wide / 3-pane reference renders under the
               android-screenshot-tests reference dir, padded to 16:9.

Output goes to AndroidGarage/screenshots/store/{phoneScreenshots,
sevenInchScreenshots,tenInchScreenshots}/ - a STAGING area. Nothing under
distribution/ is written; copy the ones you want into
distribution/playstore/screenshots/ by hand when updating the store.

Play limits honored: PNG, <=8 MB, each side within range, ratio padded to an
exact 16:9 / 9:16 (well inside Play's 2:1 cap).

Usage: python3 scripts/generate-store-screenshots.py
"""
import glob
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRAMED = os.path.join(REPO, "AndroidGarage/screenshots/framed")
REF = os.path.join(
    REPO,
    "AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference",
)
OUT = os.path.join(REPO, "AndroidGarage/screenshots/store")
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


def _edge_color(img, vertical_bars):
    """Mean color of the edges that will be extended, so the pad bars blend into
    the content instead of reading as stark black frames. vertical_bars=True
    samples the left+right columns (bars go on the sides), else top+bottom rows."""
    w, h = img.size
    s = max(2, min(w, h) // 100)  # a few-pixel strip
    if vertical_bars:
        strips = [img.crop((0, 0, s, h)), img.crop((w - s, 0, w, h))]
    else:
        strips = [img.crop((0, 0, w, s)), img.crop((0, h - s, w, h))]
    cols = [st.resize((1, 1), Image.BOX).getpixel((0, 0)) for st in strips]
    return tuple(sum(c[i] for c in cols) // len(cols) for i in range(3))


def pad(img, orientation):
    """Center img on the smallest exact-16:9 (landscape) / 9:16 (portrait) canvas.
    Bars are filled with the sampled edge color so they blend into the UI."""
    w, h = img.size
    if orientation == "portrait":          # W/H = 9/16
        if h * 9 >= w * 16:                 # too tall -> widen (side bars)
            cw, ch = -(-h * 9 // 16), h
        else:                               # too wide -> heighten (top/bottom bars)
            cw, ch = w, -(-w * 16 // 9)
    else:                                   # landscape, W/H = 16/9
        if w * 9 <= h * 16:                 # too tall/narrow -> widen (side bars)
            cw, ch = -(-h * 16 // 9), h
        else:                               # too wide -> heighten (top/bottom bars)
            cw, ch = w, -(-w * 9 // 16)
    bg = _edge_color(img, vertical_bars=(cw > w))
    canvas = Image.new("RGB", (cw, ch), bg)
    canvas.paste(img, ((cw - w) // 2, (ch - h) // 2))
    return canvas


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
        "`AndroidGarage/distribution/playstore/generate.sh`.",
        "These are the **candidates**; the curated subset that's live in the store",
        "is copied by hand into `AndroidGarage/distribution/playstore/` "
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
        ("phoneScreenshots", "Phone (9:16)", 180),
        ("sevenInchScreenshots", "7-inch tablet (16:9)", 360),
        ("tenInchScreenshots", "10-inch tablet (16:9)", 360),
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
        out = pad(Image.open(src).convert("RGB"), orient)
        dst = os.path.join(outdir, name)
        out.save(dst, optimize=True)
        w, h = out.size
        mb = os.path.getsize(dst) / 1_048_576
        flag = "" if max(w, h) <= 2 * min(w, h) and mb <= 8 else "  <-- CHECK"
        print(f"  {cat}/{name}: {w}x{h} ratio {max(w,h)/min(w,h):.3f} {mb:.2f}MB{flag}")
        by_cat.setdefault(cat, []).append(name)
        made += 1
    write_gallery(by_cat)
    print(f"\nWrote {made} screenshot(s) + README.md gallery to {OUT}")
    print("Review them, then copy the ones you want into "
          "AndroidGarage/distribution/playstore/screenshots/ by hand.")


if __name__ == "__main__":
    sys.exit(main())
