#!/usr/bin/env python3
"""
Generate the Play Store feature graphic (1024x500) with Pillow.

Pillow (not qlmanage) because qlmanage rasterizes SVGs onto a square canvas,
which letterboxes + squashes a 1024x500 design. This draws the banner at the
exact target dimensions: brand-green diagonal gradient, the app icon as a
rounded chip on the left, and the wordmark on the right.

Usage: python3 feature_graphic.py <icon-512.png> <out.png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
TOP_LEFT = (0x52, 0x75, 0x3A)      # #52753A
BOTTOM_RIGHT = (0x37, 0x51, 0x1F)  # #37511F
CREAM = (0xF5, 0xF2, 0xE8)
SUBTITLE = (0xD7, 0xE4, 0xC5)

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REG = "/System/Library/Fonts/Supplemental/Arial.ttf"


def diagonal_gradient(w, h, c0, c1):
    base = Image.new("RGB", (w, h))
    px = base.load()
    maxd = (w - 1) + (h - 1)
    for y in range(h):
        for x in range(w):
            t = (x + y) / maxd
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    return base


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.size[0], img.size[1]), radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    icon_path, out_path = sys.argv[1], sys.argv[2]
    banner = diagonal_gradient(W, H, TOP_LEFT, BOTTOM_RIGHT).convert("RGBA")

    # app icon as a rounded chip on the left
    chip = Image.open(icon_path).convert("RGBA").resize((290, 290), Image.LANCZOS)
    chip = rounded(chip, 64)
    icon_x, icon_y = 92, (H - 290) // 2
    banner.alpha_composite(chip, (icon_x, icon_y))

    draw = ImageDraw.Draw(banner)
    title_font = ImageFont.truetype(FONT_BOLD, 82)
    sub_font = ImageFont.truetype(FONT_REG, 38)
    text_x = icon_x + 290 + 56
    draw.text((text_x, 196), "Smart Garage", font=title_font, fill=CREAM)
    draw.text((text_x + 2, 290), "Open, monitor, and get alerts", font=sub_font, fill=SUBTITLE)

    banner.convert("RGB").save(out_path)
    print(f"wrote {out_path} ({W}x{H})")


if __name__ == "__main__":
    main()
