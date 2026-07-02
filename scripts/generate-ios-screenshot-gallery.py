#!/usr/bin/env python3
"""Generate a browsable Markdown gallery from the iOS reference screenshots.

Mirrors scripts/generate-android-screenshot-gallery.sh: walks the recorded
swift-snapshot-testing PNGs and emits a single SCREENSHOT_GALLERY.md with an
<img> per snapshot, grouped by test class. The PNGs are a visual REFERENCE
(regenerate, don't assert) — see scripts/generate-ios-screenshots.sh and the
ADR on the iOS snapshot gallery.

No timestamp is written so re-running only diffs when an actual image changes.
"""

import os

SNAP_ROOT = "MobileGarage/iosApp/SnapshotTests/__Snapshots__"
OUT_MD = "MobileGarage/iosApp/SnapshotTests/SCREENSHOT_GALLERY.md"
# Width the <img> tags render at (px). Screen-sized shots are tall; this keeps
# the gallery scannable.
IMG_WIDTH = 240


def collect():
    """Return {group_name: [relative_png_path, ...]} sorted for determinism."""
    groups = {}
    if not os.path.isdir(SNAP_ROOT):
        return groups
    for group in sorted(os.listdir(SNAP_ROOT)):
        group_dir = os.path.join(SNAP_ROOT, group)
        if not os.path.isdir(group_dir):
            continue
        pngs = sorted(f for f in os.listdir(group_dir) if f.endswith(".png"))
        if pngs:
            groups[group] = pngs
    return groups


def main():
    groups = collect()
    out_dir = os.path.dirname(OUT_MD)
    lines = [
        "<!-- GENERATED FILE - DO NOT EDIT -->",
        "<!-- Regenerate: ./scripts/generate-ios-screenshots.sh -->",
        "",
        "# iOS Screenshot Gallery",
        "",
        "A browsable visual reference of every SwiftUI `#Preview` in the iOS app, "
        "captured via Prefire + swift-snapshot-testing. These are reference images, "
        "**not** pixel-perfect gating tests — they are regenerated, never asserted.",
        "",
    ]

    total = sum(len(v) for v in groups.values())
    if not groups:
        lines += ["_No snapshots recorded yet. Run `./scripts/generate-ios-screenshots.sh`._", ""]
    else:
        lines.append(f"**{total} snapshot(s)** across {len(groups)} group(s).")
        lines.append("")
        lines.append("## Table of contents")
        for group in groups:
            anchor = group.lower().replace(".", "").replace(" ", "-")
            lines.append(f"- [{group}](#{anchor})")
        lines.append("")
        for group, pngs in groups.items():
            lines.append(f"## {group}")
            lines.append("")
            for png in pngs:
                title = png[:-4]  # strip .png
                rel = os.path.relpath(os.path.join(SNAP_ROOT, group, png), out_dir)
                lines.append(f"### {title}")
                lines.append(f'<img src="{rel}" width="{IMG_WIDTH}" />')
                lines.append("")

    with open(OUT_MD, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Wrote {OUT_MD} ({total} snapshot(s), {len(groups)} group(s)).")


if __name__ == "__main__":
    main()
