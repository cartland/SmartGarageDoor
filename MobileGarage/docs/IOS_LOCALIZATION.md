---
category: reference
status: active
last_verified: 2026-07-29
---

# iOS localization — current state and the path to it

Android localizes through `strings.xml` (209 entries).

**Status.** iOS has the mechanism (`Localizable.xcstrings` +
`SWIFT_EMIT_LOC_STRINGS: YES` + `scripts/sync-ios-string-catalog.sh`, #1159) and the
fence lint (step 3, #1164). The step-2 type sweep has covered **Settings** (#1165),
**History** (#1166) and **Home**; **Function list** and **Diagnostics** remain. The
catalog holds ~189 keys. Re-measure with `./scripts/sync-ios-string-catalog.sh` rather
than trusting this paragraph.

ADR-035 governs *where* a string is decided (platform, not shared). This document covers
the separate question of *how* iOS stores its words once it has them.

## Scale

~189 production user-visible literals, comparable to Android's 209:

| Area | Count |
|---|---|
| Home | 68 |
| Settings | 41 |
| Function list | 30 |
| History | 26 |
| Diagnostics | 21 |
| Tab titles | 3 |
| `Core/` | 0 — wire keys and Firebase plumbing only |

A further ~140 live in `#Preview` bodies. Those are fixtures and do not need localizing.

## The part that is not obvious

**Roughly two-thirds of those strings would still not be localizable even after adding a
catalog.** SwiftUI extracts a literal only where it can see a `LocalizedStringKey`. It
cannot when the literal has already been flattened to a `String`, which is exactly what
the ViewModel wrappers do — they resolve typed shared state into `String` properties
(`HomeViewModelWrapper.warningText`, `SettingsViewModelWrapper.snoozeLabel`,
`RemoteButtonItem.title`, every `HistoryViewModelWrapper` row field, the Diagnostics
counter labels).

That placement is *correct* under ADR-035 — the platform is doing the wording. The
**type** is what defeats extraction, not the architecture.

Three shapes, all verified empirically against emitted `.stringsdata`:

1. **Wrapper-resolved `String` properties** (~90). Fix: change the property/return type
   to `LocalizedStringResource`. The resolver body does not move.
2. **`String`-typed view parameters** (~15) — `SettingsRowLabel(title: String)`,
   `CopyableValueRow(label:)`, `HomeInfoSheetContentView`, `DoorWarningChip(text:)`.
   These silently swallow literals written at the call site.
3. **`String`-typed computed properties on model types** (~20) — `DoorPosition.statusLabel`,
   `MainTab.title`, `HomeInfoSheet.paragraphs`.

### The trap worth knowing about

```swift
Text(hasDoorData ? doorPosition.statusLabel : "Connecting…")   // literal is DEAD
```

Type unification forces the ternary to `String`, so the literal loses its
`LocalizedStringKey` treatment and is never extracted — with no warning. Write it as
`if` / `else` with two `Text` calls. This bit us once already; the `if` form and the
reason are commented at the `HomeScreen` call site.

## Two facts that decide the mechanism

Both measured, not assumed, on XcodeGen 2.45.4 + Xcode 26.5:

- **`SWIFT_EMIT_LOC_STRINGS` defaults to `NO` in an XcodeGen-generated project.** Xcode's
  own template sets it; XcodeGen does not. Without it the compiler emits no
  `.stringsdata`, nothing is ever extracted, and a String Catalog sits permanently empty
  looking like it works. One line in `project.yml`'s `settings.base` fixes it.
- **Adding the catalog file needs no `project.yml` change.** Dropping
  `Localizable.xcstrings` under an already-declared source path makes XcodeGen file it as
  a Resource automatically.

## Operational gotcha for a CLI-first repo

**`xcodebuild` does not populate the catalog.** A command-line build emits `.stringsdata`
per source file but leaves `Localizable.xcstrings` untouched — harvesting keys into the
catalog is an Xcode.app IDE behavior. Since this repo's `.xcodeproj` is generated and
gitignored and the whole workflow is `validate-ios.sh` → `xcodebuild`, adopting a catalog
needs either opening Xcode once per string-adding PR, or a
`scripts/sync-ios-string-catalog.sh` that merges keys from `$STRINGSDATA_DIR/*.stringsdata`
(plain plists, `plutil -p` readable).

## Three things the sweep surfaced

**1. `#Preview` fixture text lands in the catalog — ~20 keys today, unfixed.**
Once a field is typed `LocalizedStringResource`, the literals `#Preview` bodies pass into
it are literals *in resource position*, so the compiler extracts them. The catalog now
carries `"Since 11:22 AM · 38 min"`, `"Closed for 22 min"`, `"23 min ago"` and similar —
sample data, never shipped, and meaningless to translate. Harmless while the catalog is
English-only (nothing is translated yet), but it should be cleaned before any locale is
added. The fix, when someone does it:

```swift
/// Sample text for `#Preview` fixtures. Takes a runtime `String`, so the
/// compiler never sees a literal in `LocalizedStringResource` position and the
/// sample never reaches the catalog.
func previewText(_ value: String) -> LocalizedStringResource {
    LocalizedStringResource(stringLiteral: value)
}
```

Must be `internal` (a `#Preview` body is embedded verbatim into the generated snapshot
test, which cannot see `private` symbols).

**2. Interpolate the bridged `Int32` directly; do not wrap it in `Int(...)`.**
A Kotlin `Int` bridges to Swift `Int32`, which interpolates into a catalog key as `%d`.
Wrapping it (`Int(d.days)`) widens to `Int` and yields `%lld` — so the same phrase gets
two keys (`"%d min"` *and* `"%lld min"`) and a translator is asked for both. History
originally used the wrapped form and Home the bare one; unified on `%d`. The stale `%lld`
keys had to be pruned by hand, because the sync script only ever adds.

**3. A lone number must be formatted, not localized.**
`Text("\(counter.value)")` produced a catalog key of literally `"%lld"` — meaningless to
a translator, and it collides with every other bare-integer interpolation in the app. A
number wants locale *formatting*, not translation:
`Text(verbatim: counter.value.formatted())`.

**Do not try to fix (1) by extracting in Release configuration.** `#Preview` bodies are
excluded from a Release build, so it looks like the obvious answer. Measured: a Release
build with `SWIFT_EMIT_LOC_STRINGS=YES` yields **zero** usable keys — production strings
like `"Retry"` and `"Connecting…"` vanish too, not just the fixtures. Switching the sync
script to Release would silently stop it finding anything.

## `Localizable.xcstrings` conflicts on every parallel PR — regenerate, don't merge

The catalog is a **generated** artifact keyed by string, so any two PRs that add strings
conflict in it, even when they touch unrelated features. Hit three times in a row across
#1161 → #1162 → #1163.

Do not hand-merge the JSON. The deterministic resolution mirrors the
`SCREENSHOT_GALLERY.md` recipe: take main's copy, then re-run the sync, which re-adds your
keys on top.

```bash
git checkout --ours MobileGarage/iosApp/GarageControl/Localizable.xcstrings
git add MobileGarage/iosApp/GarageControl/Localizable.xcstrings
git rebase --continue
./scripts/sync-ios-string-catalog.sh          # re-adds this branch's keys
git add MobileGarage/iosApp/GarageControl/Localizable.xcstrings && git commit --amend --no-edit
```

The script only ever adds keys, so the result is the union of both branches. Sanity-check
the printed total: it should equal main's count plus your PR's new keys.

## Recommended path

Use a **String Catalog** (`.xcstrings`), not classic `.strings`. No downside at Xcode 26 /
iOS 16 — catalogs compile to `.strings`/`.stringsdict` in the bundle, so runtime support
goes back to iOS 11 — and it is the only option that supports compiler auto-extraction.

In dependency order:

1. **Enablement, zero behavior change.** Add an empty `Localizable.xcstrings` +
   `SWIFT_EMIT_LOC_STRINGS: YES`, plus the sync script. ~70 call-site literals become
   localizable with no source edits — the English string is the key.
2. **Type-change PRs, one feature area at a time.** `String` → `LocalizedStringResource`
   on wrapper properties, view params, and model computed properties.
   `LocalizedStringResource` (iOS 16.0+, exactly the deployment target) over
   `LocalizedStringKey` because it is `Equatable`/`Codable` for `@Published` use and can be
   resolved back with `String(localized:)` — required by the `.lowercased()` call in the
   snooze sheet.
3. **Ratchet — shipped, with a limit worth knowing.** `scripts/check-ios-localizable-text.sh`
   (in `validate-ios.sh` + `ios-ci.yml`) flags `Text(x)` / `Label(x, …)` where `x` is a
   value rather than a literal, against the fence list in
   `MobileGarage/ios-localizable-text-exemptions.txt`.

   **It cannot tell `String` from `LocalizedStringResource`** — shell has no type
   information, so both match. What it enforces is therefore "do not introduce this
   pattern into a file that does not already have it". It is a fence against spreading,
   not a per-file verdict, and the list does **not** burn down to empty as files are
   fixed. Two consequences:

   - Confirm a fix with the **catalog**, not with the lint's silence: run
     `./scripts/sync-ios-string-catalog.sh` and check the key appears in
     `Localizable.xcstrings`. That is compiler-derived. (This is how the `MainTab.title`
     conversion was verified — `"Home"` was genuinely absent from the catalog beforehand
     and present after, while the lint's view of `MainScreen.swift` never changed.)
   - `Text(verbatim:)` is the never-flagged escape hatch for values that genuinely should
     not be translated (version numbers, build hashes, auth tokens, raw server text). It
     also documents that intent at the call site.

   Two traps encountered building it, both of the "silently passes" family:
   `git grep -E` uses POSIX ERE where **`\b` is not a word boundary**, so the first
   pattern matched zero of the ~40 real call sites and the check passed vacuously — the
   same failure mode as the Konsist `file.name` trap. The script now uses an explicit
   `[^A-Za-z0-9_.]` class and carries a **scope-sanity guard** that fails if the broad
   `Text(`/`Label(` form matches nothing at all, so a future glob or dialect change
   cannot resurrect the vacuous pass.

Do **not** bundle this with moving resolvers into shared Kotlin. The type change alone
achieves localizability; the two are separable and mixing them makes both harder to review.

## Snapshot impact

Nil. With an English-only catalog, missing keys fall back to the key (the English source
string), so `./scripts/generate-ios-screenshots.sh` should be byte-for-byte unchanged.
