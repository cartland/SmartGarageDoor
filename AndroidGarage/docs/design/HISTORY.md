# Design History

Decision log for each iteration. Read top-to-bottom to understand how the spec evolved.

---

## Iteration 1: Screen Inventory & Information Architecture

**Focus:** Establish the foundational screen inventory — what exists, what's on each screen, how navigation works.

**Why this first:** Everything else (color, typography, spacing, interactions) depends on knowing the full surface area. Can't design details without the map.

**Brainstorm areas considered:**
1. Screen inventory (chosen) — foundation for everything
2. Color system — important but needs screen context first
3. Typography scale — needs content hierarchy context
4. Component library — needs screen context
5. Spacing system — needs layout context

**Decision:** Document all three screens, their content hierarchy, conditional elements, navigation model, and top bar. This gives future iterations a map to work from.

**What changed:** SPEC.md section 1 created with full screen inventory, per-screen content breakdown, navigation behavior, and top bar description.

---

## Iteration 2: Color System

**Focus:** Document the complete color system — Material 3 palette, door state colors, semantic usage rules.

**Why this second:** Color is the strongest visual signal in the app (open = red, closed = green). Documenting it now establishes the vocabulary for later iterations on components and states.

**Brainstorm areas considered:**
1. Color system (chosen) — foundational, high visual impact
2. Typography scale — important but less unique to this app
3. Component states — needs color vocabulary first
4. Spacing grid — mechanical, lower priority
5. Animation & transitions — detail work, too early

**Decision:** Extract the full color palette from Color.kt and DoorStatusColorScheme.kt. Organize by Material 3 roles, door state matrix (state × freshness), and semantic usage rules. Include hex values for precision.

**What changed:** SPEC.md section 2 added with Material 3 palette table, door state color matrix (6 states × light/dark), on-color matrix, and semantic color usage guide covering 11 contexts.

---

## Iteration 3: Component States & Interaction Design

**Focus:** Document the state machines and interaction patterns for the two most complex components — the remote button and the snooze card.

**Why this third:** These are the components users interact with most. The button has 9 states with precise timing; the snooze card has two independent state dimensions. Documenting these prevents UX regressions and gives designers a complete picture.

**Brainstorm areas considered:**
1. Component states & interactions (chosen) — high complexity, high user impact
2. Typography scale — straightforward, can wait
3. Spacing system — mechanical
4. Error states catalog — partially covered by component states
5. Responsive layout — Phase 41 work, not started

**Decision:** Document the full button state table (9 states × 6 columns), snooze state overlay model, expandable card behavior, and door status card interactions. Include timing values and key UX principles.

**What changed:** SPEC.md section 3 added with remote button state table, snooze state/action overlay tables, expandable card behavior, and door status card interaction notes.

---

## Iteration 4: Typography & Spacing

**Focus:** Document the type scale, spacing grid, elevation hierarchy, and layout principles.

**Why this fourth:** With screens, colors, and component states documented, the remaining foundation is the spatial and typographic system. This completes the "design toolkit" before the final iteration adds improvements.

**Brainstorm areas considered:**
1. Typography & spacing (chosen) — completes the toolkit
2. Responsive/adaptive layout — future work (Phase 41), too early to spec
3. Animation catalog — minor, mostly covered by component states
4. Accessibility audit — important but needs the foundations first
5. Onboarding flow — doesn't exist yet

**Decision:** Document the 3-level type hierarchy, 8dp spacing grid, elevation hierarchy, and layout principles (vertical split, card sizing, button constraints).

**What changed:** SPEC.md section 4 added with type scale usage table, spacing tokens, elevation hierarchy, and layout principles.

---

## Iteration 5: UX Improvement Opportunities

**Focus:** With the full spec documented, identify concrete UX improvements — the "what should change" list.

**Why this last:** The first 4 iterations described what IS. This iteration describes what SHOULD BE. Can only identify gaps once the current state is fully mapped.

**Brainstorm areas considered:**
1. Home screen information hierarchy — door status is the hero but gets equal space
2. Button visual feedback — 7 of 9 states look identical
3. History screen enrichment — missing duration-in-state
4. Empty states — no design for empty/unauthenticated states
5. Stale data communication — gradual aging indicators
6. Accessibility — no documented standards

**Decision:** Document all 6 as prioritized improvement proposals. Each has a problem statement and a concrete proposal. These become the backlog for future design iterations or implementation work.

**What changed:** SPEC.md section 5 added with 6 prioritized UX improvement proposals: home screen density, button visual feedback, history enrichment, empty states, stale data communication, and accessibility.

---

## Iteration 6: Garage Door Icon Component

**Focus:** Document the garage door icon — the app's most distinctive visual element. Appears on Home (large, animated) and History (small, static) but had zero spec documentation.

**Decision:** Full component spec covering viewport geometry, panel layout, frame, handle, door positions, animation, overlays, and DoorPosition mapping.

**What changed:** SPEC.md section 6 added with complete icon component specification.

---

## Iteration 7: Loading States & Data Freshness

**Focus:** Document what users see during loading — cold start (no cache), cached refresh, and error recovery. The hybrid offline-first approach means most opens show data instantly, but the empty-cache and refresh paths need clear spec.

**Decision:** Document LoadingResult model, cold start displays per screen, cached refresh indicators, fetch triggers, and screen transition behavior.

**What changed:** SPEC.md section 7 added.

---

## Iteration 8: Error States Catalog

**Focus:** Exhaustive inventory of all user-facing errors with tiered severity (blocking/degraded/transient), specific UI treatments, retry behavior, and dismissal rules.

**Decision:** Three-tier error system. T1 blocking (full-screen, auth failures), T2 degraded (persistent banners, network/stale/notification issues), T3 transient (snackbar/inline, single action failures). Snooze errors use the existing SnoozeAction overlay instead of snackbar.

**What changed:** SPEC.md section 8 added with complete error catalog (9 error types), banner/snackbar specs, and retry rules.

---

## Iteration 9: Authentication Flow UX

**Focus:** Sign-in gate, in-progress states, token refresh transparency, sign-out with undo, edge cases.

**Decision:** Progressive auth flow — fast happy path, failure-only surfacing for token refresh, sign-out with Snackbar undo instead of confirmation dialog.

**What changed:** SPEC.md section 9 added.

---

## Iteration 10: Accessibility

**Focus:** Touch targets, contrast ratios, screen reader flow, motion reduction, reachability, text scaling, and a validation checklist.

**Decision:** Full spec with testing checklist. Garage controller is safety-critical — accessibility failures are usability failures for everyone.

**What changed:** SPEC.md section 10 added.

---

## Iteration 11: Remote Button Visual Feedback

**Focus:** Per-state button colors, animated transitions, haptics. Refines proposal 5.2.

**Decision:** Each of the 9 states gets a distinct fill color. Sending=indigo, Sent=soft green, Received=strong green, timeouts=red/orange. Animated transitions 150-600ms. Haptic on Armed, Received, and errors.

**What changed:** SPEC.md section 11 added with light/dark color tables, transition timing, haptics, and accessibility verification.

---

## Iteration 12: Stale Data Progressive Warning

**Focus:** Replace binary fresh/stale with 4 graduated tiers (FRESH/AGING/STALE/EXPIRED). Refines proposal 5.5.

**Decision:** Freshness badge pill below door status, animated dot pulsing at higher severities, text desaturation, icon overlay at EXPIRED, tappable refresh. Timer cadence varies by tier to avoid recomposition waste.

**What changed:** SPEC.md section 12 added.

---

## Iteration 13: Empty States

**Focus:** Design what users see when there's no content — no events, not signed in, no door status. Closes proposal 5.4.

**Decision:** Shared layout container (illustration + title + body + action). Four specific empty states: history empty, history load failed, unauthenticated home, door status unavailable.

**What changed:** SPEC.md section 13 added.

---

## Iteration 14: Home Screen Layout Refinement

**Focus:** Closes proposal 5.1. Change vertical split from 50/50 to 67/33 (door status hero / button secondary). Exact weight ratios, zone definitions, landscape/tablet adaptation.

**What changed:** SPEC.md section 14 added.

---

## Iteration 15: History — Duration in State

**Focus:** Closes proposal 5.3. Each history row shows computed duration badge. Live-updating "so far" for current event. Formatting rules, edge cases, accessibility.

**What changed:** SPEC.md section 15 added. All 6 original UX proposals (5.1-5.6) now fully detailed.

---

## Iteration 16: FCM Push Notification Design

**Focus:** Notification channels, anatomy (collapsed/expanded), grouping/collapse strategy, per-state content, lock screen visibility, snooze-from-notification interaction.

**What changed:** SPEC.md section 16 added.

---

## Iteration 17: Dark Mode & OLED

**Focus:** Surface hierarchy for OLED (pure black background), desaturated state colors meeting WCAG AA, per-component dark adjustments, Dynamic Color rules.

**What changed:** SPEC.md section 17 added.

---

## Iteration 18: Transition Animations Catalog

**Focus:** Comprehensive motion spec: easing curves, navigation transitions, state changes, card expand/collapse, list entries, error banners, accessibility override.

**What changed:** SPEC.md section 18 added.

---

## Iteration 19: Micro-Copy Guide

**Focus:** Voice principles, canonical term list, sentence structure rules, error message templates, numeric formatting. Ensures every string feels like one author wrote it.

**What changed:** SPEC.md section 19 added. Target reduced from 50 to 25.

---

## Iteration 20: Responsive Layout

**Focus:** WindowSizeClass breakpoints (Compact/Medium/Expanded), per-screen adaptation, foldable hinge handling.

**What changed:** SPEC.md section 20 added.

---

## Iteration 21: Home Screen Widget

**Focus:** Three widget sizes (Small/Medium/Large), state mapping, refresh strategy. No remote-action button — prevents accidental presses.

**What changed:** SPEC.md section 21 added.

---

## Iteration 22: Haptic Feedback Catalog

**Focus:** Every haptic event mapped to API constant and pattern. Rules for system compliance.

**What changed:** SPEC.md section 22 added.

---

## Iteration 23: Settings Card Anatomy

**Focus:** Exact dp for expandable card structure, radio button rows, content scrolling.

**What changed:** SPEC.md section 23 added.

---

## Iteration 24: History List Item Anatomy

**Focus:** Event row dimensions, status dots, duration tags, sticky date headers.

**What changed:** SPEC.md section 24 added.

---

## Iteration 25: Onboarding / First-Run (FINAL)

**Focus:** 3-step pager: welcome, sign-in (required), notification permission (optional). Swipe disabled to enforce sign-in gate.

**What changed:** SPEC.md section 25 added. All 25 iterations complete.

---

