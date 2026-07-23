---
category: reference
status: active
last_verified: 2026-07-22
---

# UI gallery — local keyboard-driven viewer

A fully offline browser for the important UI components and screens across the
three clients (Android, iOS, Wear OS). It renders the screenshots this repo
already commits — nothing is captured by the viewer itself.

```bash
open MobileGarage/ui-gallery/index.html
```

No server, no build, no network. The page loads `manifest.js` and references
the committed PNGs by relative path, so it works from a fresh clone.

## Keyboard

| Keys | Action |
| --- | --- |
| `↑` `↓` (or `k` `j`) | previous / next row (wraps) |
| `←` `→` (or `h` `l`) | previous / next variant within the row (no-op today — all rows show every state at once) |
| `Space` | switch platform (Android ↔ iOS) |
| `t` | toggle light / dark theme |
| `Home` / `End` | first / last row |
| `?` | help overlay |

Selection, per-row variant choices, platform, theme, and scroll position are
remembered in `localStorage`, so switching any dimension never loses your
place. URL params (`?row=door-canvas&platform=ios&theme=dark`) override the
remembered state — handy for sharing a pointer to a specific row.

## The dimension model

Every image cell is addressed by four dimensions:

- **Row** (up/down) — a named collection inside a section. The whole feed is
  always rendered; up/down moves the selection and scrolls to it.
- **Variant** (left/right) — a row-specific flip-in-place axis. Present in
  the schema but **deliberately unused by the current curation** (see below).
- **Platform** (Space) — global. `platformCycle` in the manifest defines what
  Space cycles through. Rows captured only on a platform outside the cycle
  (Wear OS) are **pinned**: they always show their platform, with a badge.
- **Theme** (t) — global light/dark.

**Density first — show everything, no pills.** Every row puts ALL of its
states in `items`, side by side, wrapping onto as many lines as the width
needs — the page never scrolls horizontally. Eight Home states in one glance
beats one image plus seven chips. Reach for `variants` only if a future row
genuinely cannot show its states simultaneously; nothing today qualifies.

**Sizing.** `height` is the row's nominal tile height; `maxw` (default 420)
caps display width, so wide components (pills, full-width sections) scale
*down* to fit instead of dominating the page.

A missing combination renders as a labeled placeholder ("No dark capture",
"Not captured on iOS") rather than silently substituting — the gaps are
information, not noise.

## Architecture

```
manifest.yaml   hand-curated, declarative source of truth (this dir)
     │  python3 scripts/generate-ui-gallery.py
     ▼
manifest.js     generated + committed (validated paths, embedded dimensions)
     │  <script src="manifest.js">
     ▼
index.html      hand-written static viewer, opened via file://
```

Why the generated intermediate: `file://` pages cannot `fetch()` local files,
but `<script src>` works. The generator is also where correctness lives — it
resolves `*` globs (AGP reference PNGs embed content hashes that change when
previews change), fails on any path that doesn't resolve to exactly one file,
and embeds pixel dimensions so tiles reserve exact space before images load
(no layout shift, exact scroll restore).

`python3 scripts/generate-ui-gallery.py --check` verifies the committed
`manifest.js` is current without writing.

## Manifest schema

```yaml
platformCycle: [android, ios]        # what Space cycles through
platformLabels: {android: Android, ios: iOS, wear: Wear OS}
themes: [light, dark]
themeTokens: {light: Light, dark: Dark}   # {theme} placeholder expansion
pathAliases:
  ref: MobileGarage/android-screenshot-tests/src/...   # ${ref} in paths

sections:
  - title: Screens
    rows:
      - id: home              # unique, stable (used in URLs + saved state)
        title: Home
        height: 300           # nominal tile height in px
        maxw: 420             # optional width cap (default 420)
        note: Optional caption under the row header.
        items:                # every state side by side (wraps to fit)
          - id: closed
            label: Closed
            images:
              android: ${ref}/..._{theme}_*.png
              ios: {light: "${ios}/Home-closed.1.png"}
```

A row may also declare `variants` (with an `axis` label) to make ←/→ flip
all tiles in place; the current curation intentionally has none. With
variants, each item's `images` gains a variant-id level above the platform
keys.

Image value idioms (one per platform, by design):

- `android: ...Foo_{theme}_*.png` — `{theme}` expands per theme via
  `themeTokens`; the `*` absorbs the AGP content hash.
- `ios: {light: "..."}` — explicit themes only; iOS snapshots are light-only
  today, so dark shows an honest placeholder.
- `wear: ...` — a plain string applies to **all** themes (the wear UI has a
  single theme; the same capture is correct under both).

When a row's `items` images use platform names directly (no variant level),
the row gets a single implicit `default` variant — that's the multi-item
"states at a glance" shape.

Gotcha: quote YAML boolean-like ids (`"off"`, `"on"`, `"yes"`, `"no"`) — YAML
1.1 parses them as booleans and the generator rejects non-string ids.

## Where the images come from

The viewer only references captures produced by the existing pipelines:

| Platform | Source | Refreshed by |
| --- | --- | --- |
| Android | `android-screenshot-tests/**/reference/` (CI-rendered; local regen is blank on this Mac — see root `CLAUDE.md`) | `./scripts/generate-android-screenshots.sh` |
| iOS | `iosApp/SnapshotTests/__Snapshots__/` (swift-snapshot-testing via Prefire, regenerate-don't-assert — a visual record, not a pixel gate) | `./scripts/generate-ios-screenshots.sh` |
| Wear OS | `screenshots/store/wear/` (emulator captures, clock pinned) | `./scripts/generate-wear-screenshots.sh` |

**After any screenshot refresh, rerun `python3 scripts/generate-ui-gallery.py`**
— Android reference filenames carry content hashes, so a refresh that changes
a preview renames files and the committed `manifest.js` goes stale. The viewer
surfaces a stale manifest as "File missing — rerun ..." tiles, and `--check`
detects it mechanically.

## Curating

Add a row when a state matters for design review, not merely because a PNG
exists. Deliberately omitted today: synthetic fixtures
(`SafeListContentPaddingCanary`, `SpacingTokensReference`), `*Screen`/`*Tab`
wrappers that duplicate their `*Content` previews, framed marketing shots, and
store composites.

When a new screen or component ships with previews/snapshots, the workflow is:
find the committed PNGs, add a row (or a variant/item to an existing row) in
`manifest.yaml`, regenerate, and eyeball the row in the viewer.

## Future ideas (not built, on purpose)

- Wire `--check` into `validate.sh` so a screenshot refresh that strands the
  gallery fails fast. Deferred until the gallery proves it earns a gate.
- Auto-append the regen step to `generate-android-screenshots.sh` /
  `generate-ios-screenshots.sh` / `generate-wear-screenshots.sh`.
- More global dimensions (font scale, RTL) — the manifest schema already
  carries themes as a list, so a third theme-like axis is a schema evolution,
  not a rewrite.
