---
category: reference
status: active
last_verified: 2026-07-30
---

# iOS localization — current state and the path to it

Android localizes through `strings.xml` (209 entries). iOS has **no localization
mechanism at all**: every user-visible string is a bare Swift literal, and there is no
`Localizable.strings`, no `.xcstrings`, no `.lproj`, and no `NSLocalizedString` anywhere
in the tree.

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
3. **Ratchet.** An iOS shell lint in the style of `scripts/check-ios-self-force-unwrap.sh`
   flagging `Text(` / `Label(` on `String`-typed expressions, with an exemption file that
   starts full and burns down — mirroring Android's `checkNoLiteralStringsInCompose`.

Do **not** bundle this with moving resolvers into shared Kotlin. The type change alone
achieves localizability; the two are separable and mixing them makes both harder to review.

## Snapshot impact

Nil. With an English-only catalog, missing keys fall back to the key (the English source
string), so `./scripts/generate-ios-screenshots.sh` should be byte-for-byte unchanged.
