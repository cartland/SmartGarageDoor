#!/usr/bin/env python3
"""Wrap an Android Compose preview screenshot in a programmatic Pixel-style
device frame for README / docs use.

Usage:
    frame-screenshot.py <input.png> <output.png>
    frame-screenshot.py --batch <allowlist.txt> <reference_dir> <output_dir>

Allowlist format (one mapping per line, '#' for comments):
    HomeTabPreviewTest_Light_fc5b723e_0.png   home_tab_light.png
    DoorHistoryScreenPreviewTest_Light_*.png  history_light.png

Designed for 1080x2400 (Pixel-class) input. Other sizes scale.
"""
import os
import sys
import glob
from PIL import Image, ImageDraw, ImageFilter, ImageFont


def _draw_status_bar(canvas: Image.Image, x: int, y: int, w: int, h: int, dark_ui: bool) -> None:
    """Overlay a simulated status bar (time, signal, wifi, battery) onto the
    top of the screen region. Colors invert for dark-mode UI underneath.
    """
    fg = (255, 255, 255, 230) if dark_ui else (40, 40, 40, 230)
    d = ImageDraw.Draw(canvas)

    # Try a system font for the time; fall back to default if missing.
    font_size = max(20, int(h * 0.55))
    font = None
    for path in (
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ):
        if os.path.exists(path):
            try:
                font = ImageFont.truetype(path, font_size)
                break
            except Exception:
                pass
    if font is None:
        font = ImageFont.load_default()

    pad = int(h * 0.25)
    # Time on left.
    d.text((x + pad * 2, y + pad // 2), "9:41", fill=fg, font=font)

    # Right-side icons: signal bars, wifi, battery.
    icon_x = x + w - int(w * 0.16)
    icon_y = y + pad
    icon_h = h - 2 * pad

    # Signal bars (4 ascending bars).
    bar_w = max(2, int(icon_h * 0.12))
    for i in range(4):
        bh = int(icon_h * (0.35 + i * 0.20))
        bx = icon_x + i * (bar_w + 2)
        by = icon_y + (icon_h - bh)
        d.rectangle([bx, by, bx + bar_w, by + bh], fill=fg)

    # Wifi (simplified: three concentric arcs).
    wifi_x = icon_x + 4 * (bar_w + 2) + int(icon_h * 0.4)
    wifi_y = icon_y + icon_h // 2
    for i, r in enumerate((int(icon_h * 0.45), int(icon_h * 0.30), int(icon_h * 0.15))):
        d.arc(
            [wifi_x - r, wifi_y - r, wifi_x + r, wifi_y + r],
            start=215, end=325, fill=fg, width=max(2, bar_w - 1),
        )

    # Battery rectangle with fill.
    bat_x = wifi_x + int(icon_h * 0.55)
    bat_w = int(icon_h * 1.4)
    bat_h = int(icon_h * 0.65)
    bat_y = icon_y + (icon_h - bat_h) // 2
    d.rounded_rectangle(
        [bat_x, bat_y, bat_x + bat_w, bat_y + bat_h], radius=2, outline=fg, width=2,
    )
    # Battery tip.
    tip_w = max(2, int(bat_h * 0.18))
    d.rectangle(
        [bat_x + bat_w, bat_y + bat_h // 4, bat_x + bat_w + tip_w, bat_y + (3 * bat_h) // 4],
        fill=fg,
    )
    # Charge level (~80%).
    inner_pad = 3
    fill_w = int((bat_w - 2 * inner_pad) * 0.80)
    d.rounded_rectangle(
        [bat_x + inner_pad, bat_y + inner_pad,
         bat_x + inner_pad + fill_w, bat_y + bat_h - inner_pad],
        radius=1, fill=fg,
    )


def _sample_top_color(screen: Image.Image) -> tuple:
    """Sample the top-center of the screenshot to get a fill color (RGBA)."""
    sw, sh = screen.size
    sample = screen.crop((sw // 3, 0, 2 * sw // 3, max(2, sh // 80)))
    r, g, b = sample.convert("RGB").resize((1, 1)).getpixel((0, 0))
    return (r, g, b, 255)


def _is_dark_color(rgba: tuple) -> bool:
    return 0.299 * rgba[0] + 0.587 * rgba[1] + 0.114 * rgba[2] < 128


def frame(src_path: str, dst_path: str) -> None:
    screen = Image.open(src_path).convert("RGBA")
    sw, sh = screen.size

    # Bezel proportions (modern Pixel: thin & uniform).
    side_bezel = int(sw * 0.04)
    top_bezel = int(sh * 0.025)
    bottom_bezel = int(sh * 0.025)
    corner_radius = int(sw * 0.10)
    outer_corner_radius = int(sw * 0.13)
    bezel_color = (24, 24, 24, 255)
    button_color = (40, 40, 40, 255)
    shadow_padding = int(sw * 0.06)
    status_bar_h = int(sh * 0.04)

    # Reserve space for the fake status bar by prepending a strip in the same
    # color as the screenshot's top edge. The original screenshot is not
    # resized or distorted; the effective screen inside the bezel is taller.
    top_color = _sample_top_color(screen)
    padded = Image.new("RGBA", (sw, sh + status_bar_h), top_color)
    padded.paste(screen, (0, status_bar_h))
    screen = padded
    sh = sh + status_bar_h

    body_w = sw + 2 * side_bezel
    body_h = sh + top_bezel + bottom_bezel
    canvas_w = body_w + 2 * shadow_padding
    canvas_h = body_h + 2 * shadow_padding

    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))

    # Drop shadow.
    shadow = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    shadow_offset = int(sw * 0.01)
    sd.rounded_rectangle(
        [shadow_padding, shadow_padding + shadow_offset,
         shadow_padding + body_w, shadow_padding + body_h + shadow_offset],
        radius=outer_corner_radius, fill=(0, 0, 0, 90),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=int(sw * 0.015)))
    canvas.alpha_composite(shadow)

    # Phone body.
    body = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    body_x0, body_y0 = shadow_padding, shadow_padding
    body_x1, body_y1 = shadow_padding + body_w, shadow_padding + body_h
    bd.rounded_rectangle(
        [body_x0, body_y0, body_x1, body_y1],
        radius=outer_corner_radius, fill=bezel_color,
    )

    # Side buttons (right edge).
    btn_x = body_x1 - 2
    btn_w = max(4, int(sw * 0.012))
    power_y = body_y0 + int(body_h * 0.18)
    power_h = int(body_h * 0.06)
    bd.rounded_rectangle(
        [btn_x, power_y, btn_x + btn_w, power_y + power_h], radius=2, fill=button_color,
    )
    vol_y = body_y0 + int(body_h * 0.27)
    vol_h = int(body_h * 0.10)
    bd.rounded_rectangle(
        [btn_x, vol_y, btn_x + btn_w, vol_y + vol_h], radius=2, fill=button_color,
    )
    canvas.alpha_composite(body)

    # Round-mask the screenshot.
    mask = Image.new("L", (sw, sh), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, sw, sh], radius=corner_radius, fill=255)
    rounded_screen = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    rounded_screen.paste(screen, (0, 0), mask)

    screen_x = body_x0 + side_bezel
    screen_y = body_y0 + top_bezel
    canvas.alpha_composite(rounded_screen, (screen_x, screen_y))

    # Status bar overlay (drawn on top so it sits above app chrome).
    _draw_status_bar(canvas, screen_x, screen_y, sw, status_bar_h, _is_dark_color(top_color))

    canvas.save(dst_path, "PNG")
    print(f"  wrote {os.path.relpath(dst_path)}  ({canvas_w}x{canvas_h})")


def batch(allowlist_path: str, reference_dir: str, output_dir: str) -> int:
    os.makedirs(output_dir, exist_ok=True)
    failures = 0
    framed: list[str] = []
    with open(allowlist_path) as f:
        for raw in f:
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) != 2:
                print(f"WARN: skipping malformed line: {raw.rstrip()}", file=sys.stderr)
                continue
            pattern, out_name = parts
            matches = glob.glob(os.path.join(reference_dir, "**", pattern), recursive=True)
            if not matches:
                print(f"WARN: no match for {pattern} under {reference_dir}", file=sys.stderr)
                failures += 1
                continue
            src = matches[0]
            dst = os.path.join(output_dir, out_name)
            try:
                frame(src, dst)
                framed.append(out_name)
            except Exception as e:
                print(f"WARN: failed to frame {src}: {e}", file=sys.stderr)
                failures += 1
    print(f"framed {len(framed)} screenshot(s); {failures} failure(s)")
    if framed:
        _write_collection(output_dir, framed)
    return 0 if framed else 1


def _humanize(out_name: str) -> tuple[str, str]:
    """Derive (view, mode) from an output filename like 'home_tab_light.png'."""
    stem = out_name.removesuffix(".png")
    if stem.endswith("_dark"):
        mode = "Dark"
        stem = stem[: -len("_dark")]
    elif stem.endswith("_light"):
        mode = "Light"
        stem = stem[: -len("_light")]
    else:
        mode = ""
    view = stem.replace("_", " ").title()
    return view, mode


def _write_collection(output_dir: str, framed: list[str]) -> None:
    """Emit COLLECTION.md grouping framed PNGs by view, light + dark side by side."""
    by_view: dict[str, dict[str, str]] = {}
    for name in framed:
        view, mode = _humanize(name)
        by_view.setdefault(view, {})[mode] = name
    lines = [
        "<!-- GENERATED FILE — DO NOT EDIT -->",
        "<!-- Source: scripts/framed-screenshots.txt (regenerated by frame-screenshot.py --batch) -->",
        "",
        "# Framed Screenshots",
        "",
        f"{len(framed)} framed screenshots from `scripts/framed-screenshots.txt`. ",
        "Re-rendered on every `./scripts/generate-android-screenshots.sh` run.",
        "",
    ]
    for view in sorted(by_view):
        modes = by_view[view]
        lines.append(f"## {view}")
        lines.append("")
        cells = []
        for mode in ("Light", "Dark", ""):
            name = modes.get(mode)
            if not name:
                continue
            label = f"{view} — {mode}" if mode else view
            cells.append(f'<img src="{name}" alt="{label}" width="240" />')
        lines.append(" ".join(cells))
        lines.append("")
    md_path = os.path.join(output_dir, "COLLECTION.md")
    with open(md_path, "w") as f:
        f.write("\n".join(lines))
    print(f"  wrote {md_path}")


def main() -> int:
    if len(sys.argv) == 5 and sys.argv[1] == "--batch":
        return batch(sys.argv[2], sys.argv[3], sys.argv[4])
    if len(sys.argv) == 3:
        frame(sys.argv[1], sys.argv[2])
        return 0
    sys.stderr.write(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
