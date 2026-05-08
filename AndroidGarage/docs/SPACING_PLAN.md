---
category: plan
status: active
---

# Spacing Consistency Strategy

A proposal to introduce a small, named set of spacing tokens that capture the spacing rules already followed in practice across the Android UI — so future changes have a token to reach for instead of a raw `.dp` literal, and so the rules that exist *implicitly* today become explicit and reviewable.

## Status (2026-05-08)

**Stages 1–4 and Stage 0 shipped to `main`** in PRs #677–#683. Tokens are in place across every screen. Tablet/landscape content is centered at 640dp via `RouteContent`. Diagnostics action buttons follow the parent-vs-child spacing rule via `Arrangement.spacedBy(ButtonSpacing.Stacked)`.

Not yet shipped to a release tag — the work sits between `android/202` and the next `android/N` minor bump. The large-screen cap is the user-facing capability that justifies the bump.

Remaining open items:

- **Stage 4 sub-decisions** — bottom-sheet vertical padding (`AccountBottomSheet` 24dp vs `SnoozeBottomSheet` 16dp) and paragraph-spacing rule for `FunctionListWarning` and similar standalone prose blocks. Both are subjective design calls deferred for a real review pass; both are noted in the "Open questions" sections below.
- **Stage 5 (lint)** — deferred per plan, revisit ~90 days after Stage 4 lands.
- **Per-screen `ContentWidth` variants** — held back per the "≥2 call sites" rule. Add only if a real screen needs it.

## Parent vs. child spacing responsibility

The rule that grounds every token in this proposal:

> **A composable is responsible for spacing *within* its own bounds. It is NOT responsible for spacing *outside* its bounds. The parent owns the gap between its children; each child owns everything inside itself.**

### Mapping

| Concern | Owner | Token examples |
|---|---|---|
| Screen-edge gutter | Parent (route wrapper at `Main.kt`) | `Spacing.Screen` |
| Top/bottom safe-area carve-out | Parent (Scaffold) | `innerPadding` |
| Gap above first list item / below last | Parent (LazyColumn) | `Spacing.ListVertical` |
| Gap between sibling items | Parent (LazyColumn / Column) | `Spacing.BetweenItems`, `ButtonSpacing.Stacked` |
| Where a section header sits | Parent (the section's own header Row) | `Spacing.SectionHeader*` |
| Card body interior padding | Child (the card body) | `CardPadding.Standard` / `Tall` / `Compact` |
| Button label interior | Child (the button) | Material defaults, `ButtonSpacing.IconText` |
| Pill internal layout | Child (the pill) | One-off |
| Bottom sheet interior | Child (the sheet) | One-off (24dp / 16dp) |

### How to decide for a new composable

Two questions:

1. **"If I dropped this composable into a different parent, would the spacing still make sense?"** Yes → child-owned. No → parent-owned.
2. **"Could a future caller want a different gap above this?"** Yes → don't bake it in; let the parent decide via `Arrangement.spacedBy(...)` or a `Spacer`. No → bake it in.

A useful litmus test: **a composable should never apply `Modifier.padding(top = ...)` to itself.** That's claiming ownership of the gap above it, which is the parent's job. Padding values inside a composable should describe its *interior*, not its surroundings.

Stage 4 closed exactly this kind of violation — `DiagnosticsContent`'s Export and Clear buttons each had `Modifier.padding(top = 16.dp)` claiming their own outer gap. The fix wraps both buttons in a Column with `Arrangement.spacedBy(ButtonSpacing.Stacked)` and the buttons themselves drop their outer padding entirely. Visual: byte-identical (16dp via spacedBy = 16dp via per-button top padding). Structural: the parent now owns the gap, so future button additions or removals work without re-checking padding math.

The rule applies to gap tokens too: `ButtonSpacing.Stacked` and `ButtonSpacing.Inline` are *gaps between buttons*, parent-owned. Consume them via `Arrangement.spacedBy(ButtonSpacing.Stacked)` on a Column, never via `Modifier.padding(top = ButtonSpacing.Stacked)` on a button.

## Why this proposal exists

Today, every spacing value in `androidApp/` is a hardcoded `.dp` literal at its use site. A grep for `.padding(` returns hundreds of matches across screens. There is no `Spacing.kt` / `Dimensions.kt` / `Tokens.kt` file. The values are remarkably consistent — `16.dp`, `8.dp`, `24.dp`, and `4.dp` cover ~90% of the layout — but the *roles* those values play (screen padding vs. card interior vs. icon-text gap) are encoded only in human convention. A new screen has nothing to import; it copies a literal from a neighboring file.

This works as long as one developer holds the pattern in their head. It does not work as well for AI-agent collaboration, where each session is ephemeral and "look at how Home does it" is a multi-step investigation. The agent who built FunctionList's warning paragraph (`FunctionListContent.kt:119–128`) chose `vertical = 8.dp` with no explicit precedent; the agent who builds the next paragraph will have to choose again.

A real bug has already been caught by the one place where the convention *is* written down: the `// SINGLE source of horizontal screen padding` comment at `Main.kt:173–178`, added after PR #589 doubled the Settings card to 32dp. That comment is the proof-of-concept for what this proposal generalizes — a written rule prevents a regression an agent (or human) would otherwise re-introduce.

## Goals

1. **Name the rules already followed.** Turn the implicit 16/8/24/4 grid and its variants into named constants whose names describe their *role*, not their value.
2. **Surface the inconsistencies that are real choices** (e.g., card interior padding has three justifiable variants) and the inconsistencies that are accidents (e.g., button inter-spacing has three styles for no reason).
3. **Make the load-bearing rule discoverable.** The `Main.kt:186` "single horizontal padding wrapper" rule is currently a code comment. With a named token, the rule becomes a code-review trigger ("why are you using `Spacing.Screen` outside `Main.kt`?").
4. **Keep visual design unchanged.** This is a refactor of *names*, not values. Every screen should render pixel-identical after migration.

## Non-goals

- **Not a redesign.** Padding values stay where they are. We are documenting current pixel choices, not changing them.
- **Not adding a custom typography scale.** Material 3 stock typography is preserved (see `Type.kt:22`). Type changes are a separate proposal if they ever happen.
- **Not enforcing tokens via lint.** A lint rule that bans raw `.dp` literals in `androidApp/.../ui/` is *possible* (the codebase already has `checkNoBareTopLevelFunctions` and `checkScreenViewModelCardinality` as precedents) but would land in a follow-up after the tokens have soaked. Lint rules on freshly-introduced conventions tend to be noisy.
- **Not addressing `Modifier.size(...)` for fixed-dimension components.** Icon sizes (24dp, 36dp, 40dp, 56dp, 160dp), button heights, and component bounding boxes are out of scope. This proposal is about *padding* and *gap* values only.
- **Not a `Dimens.xml` migration.** Compose-only tokens. The tokens live in Kotlin (`Spacing.kt`), not Android resources.
- **Not adaptive-layout / two-pane support.** Two-pane (list-detail) layout for tablets and foldables is anticipated future work that will ship behind a runtime toggle so single-pane stays available. This proposal *includes* large-screen content-width capping (see "Large-screen extension" below) because that work shares the same wrapper as the spacing tokens. It does *not* include `WindowSizeClass`-driven layout switching, navigation rail, or pane composition — those land in their own proposal once we have a real two-pane experiment to learn from.

## Current state (audit summary)

Full investigation in conversation logs; condensed here:

### What's already disciplined

- **Section headers** are pixel-identical across `home/HomeContent.kt:244`, `SettingsContent.kt:239`, and `HistoryContent.kt:174` — `padding(start = 16.dp, top = 8.dp, bottom = 8.dp)` + `labelMedium` + `primary` + `uppercase()`. Three files, one shape, no drift. This is the gold standard.
- **List screens** all use `LazyColumn { contentPadding = PaddingValues(vertical = 16.dp); verticalArrangement = Arrangement.spacedBy(8.dp) }`. Six screens, identical setup.
- **Screen horizontal padding** is applied exactly once, at `Main.kt:186, 195, 205, 212` (one wrapper per route entry). The `// SINGLE source` comment enforces the rule informally; it has held since #593.

### What's inconsistent without good reason

- **Button inter-spacing**: `padding(top = 16.dp)` per button (`DiagnosticsContent.kt:185–233`), `Spacer(Modifier.width(12.dp))` (`home/HomeContent.kt:435`), and `AlertDialog` defaults are all in use for "space between buttons." No pattern documented.
- **Divider inset**: `start = 56.dp` (`SettingsContent.kt:176`), `start = 72.dp` (`HistoryContent.kt:174`), `horizontal = 16.dp` (`DiagnosticsContent.kt:178`). The 72.dp variant is correct for `HistoryContent`'s 40dp leading icon, but it's not labeled as such — a future contributor might "fix" it to 56.
- **Paragraph spacing**: no primitive at all. `HistoryEmptyState` uses explicit `Spacer(16.dp)` and `Spacer(4.dp)`; `FunctionListWarning` uses `padding(vertical = 8.dp)` with no spacer; `ClearDiagnosticsDialog` relies on `AlertDialog` defaults. Three approaches for "vertical breathing room around prose."

### What's inconsistent for *good* reasons

- **Card interior padding** has three variants:
  - `vertical = 24.dp, horizontal = 16.dp` (`HomeStatusCardBody:278`) — for cards with a 160dp hero element
  - `16.dp` uniform (`HomeRemoteButtonBody:347`, `ErrorCard.kt:61`) — default
  - `vertical = 12.dp, horizontal = 16.dp` (`HomeAlertCard:419`) — banner-style alert
- **Bottom sheet padding**: `AccountBottomSheet` uses `24.dp/24.dp`, `SnoozeBottomSheet` uses `24.dp/16.dp`. Both are reasonable for their content; neither matches list-row 16.dp because sheets are visually separate surfaces.

These are *role differences*, not drift. Tokens should preserve all three.

## Proposed token set

A new file: `androidApp/src/main/java/com/chriscartland/garage/ui/theme/Spacing.kt`. Single object, no companion levels, names describe the *role*:

```kotlin
object Spacing {
    /** Applied ONCE at Main.kt's NavDisplay wrapper. Never re-applied by a screen. */
    val Screen = 16.dp

    /** Vertical contentPadding on every screen-level LazyColumn. */
    val ListVertical = 16.dp

    /** Arrangement.spacedBy(...) between items in a screen-level list. */
    val BetweenItems = 8.dp

    /** Tight grouping: icon ↔ text inside a pill, supporting text ↔ headline. */
    val Tight = 4.dp

    /** Spacing inside section headers (between header and content below). */
    val SectionHeaderTop = 8.dp
    val SectionHeaderBottom = 8.dp
    val SectionHeaderStart = 16.dp
}

object CardPadding {
    /** Default card body — uniform breathing room. ErrorCard, RemoteButtonBody. */
    val Standard = PaddingValues(16.dp)

    /** Tall content (hero icon, illustration). HomeStatusCardBody. */
    val Tall = PaddingValues(vertical = 24.dp, horizontal = 16.dp)

    /** Banner-style alerts. HomeAlertCard. */
    val Compact = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
}

object DividerInset {
    /** Material ListItem with a 24dp leading icon. */
    val ListItem = 56.dp

    /** ListItem with a 40dp leading icon (HistoryContent's GarageIcon). */
    val LargeLeading = 72.dp

    /** No leading icon — divider spans the full card-content width. */
    val FullWidth = 16.dp
}

object ButtonSpacing {
    /** Between icon and text label inside a Button. */
    val IconText = 8.dp

    /** Between vertically-stacked buttons (Diagnostics' Export → Clear). */
    val Stacked = 16.dp

    /** Between an inline message and an adjacent action button (HomeAlertCard). */
    val Inline = 12.dp
}

object ParagraphSpacing {
    /** Between a section title and the body text below it. */
    val TitleToBody = 4.dp

    /** Between an icon and the prose it labels (empty states, alert cards). */
    val IconToText = 12.dp

    /** Vertical padding around a standalone warning/info paragraph. */
    val Block = 8.dp
}
```

Six small objects, ~20 named values. Imports stay readable (`import ...theme.Spacing`).

## Migration plan

A four-stage rollout, each stage is a small PR. **No PR rewrites all screens.** Each migration is screen-scoped so review stays focused and a reverted PR rolls back exactly one screen.

(Stage 0 — large-screen wrapper — is described in the "Large-screen extension" section below. It can land before or after Stage 1; see that section for sequencing.)

### Stage 1 — Introduce tokens (one PR)

- Add `Spacing.kt` with the values above.
- Add a screenshot test that exercises every token (one fixture per `CardPadding`, `DividerInset` variant, `ButtonSpacing` variant). This is the "tokens render the same as the literals they replace" guardrail.
- No screen changes yet. Tokens exist but nothing imports them.

### Stage 2 — Migrate the gold-standard pattern (one PR)

- Replace section-header literals in `home/HomeContent.kt:244`, `SettingsContent.kt:239`, `HistoryContent.kt:174` with `Spacing.SectionHeaderStart` / `Top` / `Bottom`.
- Add `labelMedium + primary + uppercase` to a shared `SectionHeaderText` Composable if it doesn't already exist (it currently doesn't — three near-identical inline `Text(...)` declarations).
- Pixel diff before/after: zero.

### Stage 3 — Migrate cards, dividers, buttons (one PR per screen)

- One PR each for `HomeContent`, `SettingsContent`, `DiagnosticsContent`, `FunctionListContent`, `HistoryContent`. Each replaces literal `.dp` values with the appropriate token, *only for the patterns covered by the tokens*. Raw `.dp` for one-off layout (a fixed-size box, a `Modifier.height(8.dp)` spacer in a one-shot place) stays.
- The screenshot tests catch any visual drift.

### Stage 4 — Resolve the "inconsistent without good reason" cases

This stage *does* change pixels. Each item is its own PR with its own design call. Don't bundle.

- **Button inter-spacing**: pick one approach (likely `ButtonSpacing.Stacked` everywhere) and migrate all three current call sites. Settle which dialog uses defaults vs. our token.
- **Paragraph spacing**: `FunctionListWarning`, `HistoryEmptyState`, `ClearDiagnosticsDialog` body all start using `ParagraphSpacing.*`. The "right" values may not be 4/12/8 — Stage 4 is when we *decide*, not just rename.
- **Bottom sheet vertical padding**: `AccountBottomSheet:90` (24dp) vs. `SnoozeBottomSheet:99` (16dp). Pick one, or document the rule that distinguishes them.

Stage 4 PRs are the only ones that produce a screenshot diff. Stages 1–3 are pure renames.

### Stage 5 (optional, deferred) — Lint enforcement

- Add a Gradle `Task` (à la `checkNoBareTopLevelFunctions`) that scans `androidApp/src/main/.../ui/**.kt` for `.dp` literals outside an allowlist.
- Allowlist: `Modifier.size(...)`, `Modifier.height(...)`, `Modifier.width(...)`, `Spacer(Modifier.height(...))`, calls inside `Spacing.kt` itself.
- Failure mode: build error with the offending file:line and the suggested token.
- This stage is *deferred* until at least 90 days after Stage 4 lands. Lint rules on freshly-introduced conventions are noisy.

## Risks

- **Premature abstraction.** If the token set grows beyond what's actually used, it becomes a maintenance burden of its own. Mitigation: every token in the initial set must replace at least two existing call sites. If only one screen uses a value, leave the literal.
- **Designed-by-committee creep.** A spacing token system tends to attract "should we add a `MediumLarge` between `Standard` and `Tall`?" debates. Mitigation: the proposal caps the initial set at the values *already in production*; new tokens require a real call site, not a hypothesis.
- **Stages 1–3 break visual parity by accident.** Mitigation: every migration PR runs `./scripts/generate-android-screenshots.sh` and the diff must be zero.
- **The `Main.kt:186` rule is implicit.** A token named `Spacing.Screen` doesn't itself prevent a screen from re-applying `padding(horizontal = Spacing.Screen)`. Mitigation: the KDoc on `Spacing.Screen` says "applied ONCE at Main.kt." A lint rule (Stage 5) would mechanically enforce it later.

## Open questions

1. **Where does the file live?** Proposal: `androidApp/.../ui/theme/Spacing.kt`, alongside `Theme.kt` and `Type.kt`. Alternative: a new `ui/spacing/` folder. The single-file location is simpler unless the token set grows past ~30.
2. **Should `CardPadding` be `Spacing.CardPadding` (nested) or top-level?** Top-level reads better at the call site (`CardPadding.Tall` vs. `Spacing.CardPadding.Tall`). Nested makes the relationship to `Spacing` explicit. Proposal: top-level for ergonomics, accept slight discoverability cost.
3. **Do we add a `Spacing` object inside `MaterialTheme` (à la `MaterialTheme.spacing`) via a custom `LocalSpacing` `CompositionLocal`?** This is the "official" Compose pattern for design tokens. *Cost:* extra ceremony, every preview and screenshot test must wrap content in a provider. *Benefit:* dark/light or compact/expanded variants become possible. Proposal: skip the `CompositionLocal` for now — there are no variants and may never be. Plain `object` keeps imports simple.
4. **Should bottom sheet padding be a separate `SheetPadding` object, or absorbed into `CardPadding`?** Sheets and cards are visually distinct; absorbing them confuses the role. Proposal: separate `SheetPadding` object, deferred to Stage 4 since it's only two call sites.
5. **What about `Modifier.padding(innerPadding)` from the Scaffold?** Out of scope. That's a Scaffold contract value, not a design token.

## Success criteria

- After Stage 3, `git grep -E '\.padding\(.*[0-9]+\.dp' androidApp/src/main/.../ui/` returns dramatically fewer hits than today, and the remaining hits are either (a) one-off layout values, (b) `Modifier.size`-style fixed dimensions, or (c) call sites the tokens deliberately don't cover.
- A new screen added after Stage 3 imports `Spacing` / `CardPadding` / etc., not raw `.dp`.
- Reviewers can spot a "wrong" spacing choice in a PR by reading the token name (`Spacing.Tight` for a card body would be obviously wrong) instead of having to know what `4.dp` means in context.
- Stage 4 produces a measurable answer to "what's our paragraph-spacing rule" that the next AI agent can follow without rediscovering it.

## Large-screen extension

A second concern that touches the same wrapper: capping content width on tablets, foldables, and landscape phones so the layout doesn't stretch across an uncomfortable reading width. This is included in the proposal because it shares the architectural location with the spacing tokens (the `Main.kt:186` route wrapper) and because the "single source" rule should cover both *padding* and *width* in one place — not be split across two proposals that risk drifting.

### Forward-compatibility with two-pane

A two-pane (list-detail) experiment is anticipated future work but **not in scope here**. When it ships, it will land behind a runtime toggle so a user can opt back into single-pane on a tablet — meaning single-pane decisions must remain a complete, durable artifact, not a fallback that quietly bit-rots once two-pane becomes the "real" layout.

This shapes two design choices in the extension:

1. **The `ContentWidth` tokens are first-class.** They are not labeled "phone defaults" or "fallback" — they are the canonical single-pane content widths, period. Two-pane code, when it arrives, *consumes* these (e.g., the list pane in two-pane mode could reuse `ContentWidth.Reading` as its width). That keeps single-pane and two-pane reading from the same source of truth.
2. **The wrapper at `Main.kt:186` stays the single source of horizontal layout** even after two-pane. When two-pane is active, that wrapper becomes "pick single-pane or two-pane based on toggle, then apply the appropriate widths." Screens still don't think about layout — only `Main.kt` does.

### Where the cap goes

Today's wrapper:

```kotlin
HomeContent(
    modifier = Modifier
        .padding(horizontal = 16.dp)
        .fillMaxWidth(),
)
```

Becomes:

```kotlin
Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter,
) {
    HomeContent(
        modifier = Modifier
            .widthIn(max = ContentWidth.Standard)
            .padding(horizontal = Spacing.Screen)
            .fillMaxWidth(),
    )
}
```

On phones (<`ContentWidth.Standard`), `widthIn(max = ...)` is a no-op and layout is unchanged. On larger screens, content centers inside the cap and excess width becomes margin.

### What's *not* capped

- **TopAppBar and NavigationBar** — Material 3 chrome stays edge-to-edge. Capping would float the bottom nav awkwardly on a tablet.
- **`ModalBottomSheet`** — Material 3 already caps sheets on large screens; no work needed.
- **Full-bleed media** (none today, but if added) — opt out explicitly via direct placement, not via the route wrapper.

The cap lives at the *route content* layer, between `innerPadding` and the screen Composable. Scaffold chrome is unaffected.

### Token additions

```kotlin
object ContentWidth {
    /**
     * Default cap for screen content. Roughly Material 3's medium
     * WindowSizeClass upper bound. Phones unaffected; tablets,
     * foldables, and landscape get centered content with margins.
     *
     * When two-pane mode lands and is enabled, this value also defines
     * the list-pane width by default — so single-pane and two-pane
     * read from the same canonical width.
     */
    val Standard = 640.dp
}
```

**One value to start.** Per-screen variants (`Reading = 560.dp`, `Wide = 720.dp`) are *not* added in the initial set — same rule as the spacing tokens: "must replace ≥2 real call sites before adding a new variant." If `HomeContent` demonstrably wants more breathing room than `SettingsContent`, a later PR splits the token. Speculation gets us nowhere.

### Stage 0 — Large-screen wrapper (one PR)

Goes *before* Stage 1 if you want to validate the visual on a tablet emulator before committing to the token name. Goes *after* Stage 1 if you want the token introduction to be free of behavior changes.

- Wrap each `entry<>` in `Main.kt:179–223` with `Box(contentAlignment = TopCenter) { ... }` and add `.widthIn(max = 640.dp)` (literal in Stage 0; absorbs into `ContentWidth.Standard` in Stage 1).
- Add at least one screenshot test fixture at a tablet form factor (`@Preview(device = Devices.PIXEL_TABLET)` or similar) so the cap is exercised by `validate.sh`. Without this, regressions to the cap are invisible on phone-only screenshot tests.
- Test landscape phone — capping at 640dp slightly *reduces* visible content vs. today's full-width landscape. This is acceptable but should be a deliberate decision, not a surprise.
- Phone-portrait screenshot diff: zero.
- Tablet / landscape screenshot diff: yes, this stage produces visible changes (the whole point).

### What two-pane will need from this work later (and why we're future-proofing now)

When two-pane lands, `Main.kt`'s wrapper expands roughly to:

```kotlin
val isTwoPane = userPreferences.twoPaneEnabled.collectAsState().value &&
    windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Expanded

if (isTwoPane) {
    TwoPaneLayout(
        listPane = { route.listContent(modifier = Modifier.widthIn(max = ContentWidth.Standard)) },
        detailPane = { route.detailContent() },
    )
} else {
    Box(contentAlignment = TopCenter) {
        route.singlePaneContent(
            modifier = Modifier
                .widthIn(max = ContentWidth.Standard)
                .padding(horizontal = Spacing.Screen)
        )
    }
}
```

Two things to notice:

- **The single-pane branch doesn't change.** It's the same wrapper this proposal introduces. Two-pane is added *next to* it, not *replacing* it.
- **Both branches consume `ContentWidth.Standard`.** The token survives the architectural change because it's named for what it is (canonical content width), not for the layout mode it serves.

That means today's Stage 0 is forward-compatible. Nothing here will need to be undone or reshaped when two-pane arrives.

### Risks specific to the large-screen extension

- **Card edge alignment shifts on tablets.** Today, on every form factor, the card edge sits at 16dp from the screen edge. After Stage 0 on a tablet, the card sits at ~`(screenWidth - 640) / 2 + 16` from the screen edge. The bottom nav items still anchor to the screen edges. This is *the desired effect* — content centered, chrome full-width — but it does change the geometry. Worth confirming on a real tablet before locking the token value.
- **Landscape phone gets less visible content.** A landscape phone is wider than 640dp. Capping at 640dp means a strip of margin appears. Acceptable, but call it out in the PR description so it's a deliberate decision, not a regression report.
- **Tablet screenshot tests are new infrastructure.** Today every screenshot fixture is phone-portrait. Adding tablet fixtures means more PNGs in `android-screenshot-tests/screenshots/`, longer screenshot test runs, and potentially Layoutlib quirks at unusual viewport sizes. Start with one tablet fixture per route, not the full matrix.
- **Premature multi-token.** Resist the urge to ship `ContentWidth.Reading`, `ContentWidth.Wide`, and `ContentWidth.Standard` simultaneously. One value, observed in production on real form factors, then split.

### Open questions specific to the extension

1. **What's the right default cap value?** 640dp is a reasonable starting point (matches Material 3's medium WindowSizeClass upper bound) but could be 600 or 720. Decide on a tablet emulator with the actual content rendered.
2. **Does the cap apply to `DiagnosticsScreen` too?** It's a sub-screen reached via back navigation, not a tab. Probably yes — same rule applies — but worth confirming the visual.
3. **Order of Stage 0 vs. Stage 1.** Stage 0 first lets you tune the value before naming it; Stage 1 first means Stage 0 doesn't introduce a literal. Either order works.
4. **Should we add a `BoxScope`-aware `RouteContent` Composable** that centralizes the `Box(TopCenter) { content.widthIn(max = ContentWidth.Standard) }` pattern, so each `entry<>` in `Main.kt` reads `RouteContent { HomeContent(...) }` instead of repeating the Box wrapper five times? Reduces duplication; adds one more layer to read. Defer to Stage 0 implementation review.

## What this proposal does NOT change

- Visual design of any screen (Stages 1–3).
- The `Main.kt:186` "one horizontal padding wrapper" rule. The token system *names* it; it doesn't relax or tighten it.
- Material 3 typography (`AppTypography` stays stock).
- Inset handling (Scaffold-centric, no per-screen inset modifiers).
- Bottom sheet inset handling (Material `ModalBottomSheet` defaults).
- Any `Modifier.size(...)` value or component bounding box.
- The screenshot test contract (every preview still gets captured).
- Two-pane layout, navigation rail, or any `WindowSizeClass`-driven layout switching. The large-screen extension caps content width but keeps single-pane layout on every form factor. Two-pane is its own future proposal, designed to land behind a runtime toggle so single-pane remains a first-class option.
