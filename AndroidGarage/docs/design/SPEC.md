# SmartGarageDoor Android App — Design Specification

*This document grows with each design iteration. See HISTORY.md for decision rationale.*

---

## 1. Screen Inventory & Information Architecture

### App Structure

Three-tab bottom navigation. Home is the root; other tabs stack on top.

| Tab | Icon | Label | Purpose |
|-----|------|-------|---------|
| Home | Home | Home | Primary: door status + remote button |
| History | DateRange | History | Recent door events list |
| Settings | Person | Settings | User account, notification snooze, app info |

### Home Screen

The most-used screen. Split vertically into two halves:

**Upper half — Door Status Card:**
- Door position label (Open, Closed, Opening, etc.)
- Duration since last change (e.g., "2 hours ago")
- Animated garage door icon (colored by state)
- Last change date/time
- Warning message for error states

**Lower half — Remote Button:**
- Large circular gradient button with tap-to-confirm
- Progress indicator bar below button (5 parallelogram segments)
- Status text below bar (Ready, Sending, Sent, Door Moved, etc.)

**Conditional elements:**
- Notification permission request card (top, if not granted)
- Error card with retry (if fetch fails)
- Stale data banner (if no check-in for >1 hour)
- Sign-in prompt (if unauthenticated)

### History Screen

Scrollable list of recent door events.

**Each list item:**
- Left column: door position label + static garage icon (56dp)
- Right column: time (large), date (small), duration ago

### Settings Screen

Vertical list of expandable cards. All cards persist collapsed/expanded state via DataStore.

**Cards (top to bottom):**
1. **Snooze Card** — only if notifications granted. Radio group for snooze duration (None, 1h, 4h, 8h, 12h). Shows current snooze status, save/cancel actions, loading spinner during save.
2. **User Info Card** — expandable. User name, email. Sign in / sign out button.
3. **App Info Card** — expandable. Version name, version code, build info.
4. **Log Summary Card** — expandable, optional (config flag). Event counts for debugging.

### Navigation

- Bottom bar always visible
- Back from non-Home tab → Home
- Back from Home → exit app
- Fade transitions between tabs (300ms, FastOutSlowIn)
- Predictive back gesture uses system default for app exit

### Top Bar

- Title: "Garage"
- Right action: check-in pill showing time since last server heartbeat, colored to match door state

## 2. Color System

### Design Principles

- **Door state is the primary visual signal.** Color immediately communicates whether the door is open (danger), closed (safe), or unknown (warning).
- **Freshness matters.** Stale data (>1 hour old) uses desaturated colors so the user knows the info may be outdated.
- **Material 3 pastel palette** for chrome (navigation, cards, surfaces). Door colors are intentionally more saturated to stand out against the soft background.

### Material 3 Palette

| Role | Light | Dark | Notes |
|------|-------|------|-------|
| Primary | `#A6C8FF` (soft blue) | `#6F8FD5` | Nav selection, armed button, success text |
| On Primary | `#002868` | `#143469` | Text on primary surfaces |
| Secondary | `#B4C6F8` | `#6780B7` | Supporting elements |
| Error | `#F9DEDC` (soft pink) | `#F9DEDC` | Error backgrounds |
| On Error Container | `#601410` | `#F9DEDC` | Error text (high contrast on surface) |
| Background | `#FFFBFE` | `#1A1C19` | App background |
| Surface Container Low | `#F1F1EC` | `#21231E` | Card backgrounds |

### Door State Colors

Two dimensions: **state** (closed/open/unknown) × **freshness** (fresh/stale).

| State | Fresh Light | Fresh Dark | Stale Light | Stale Dark |
|-------|-------------|------------|-------------|------------|
| Closed | `#226B43` (green) | `#25673C` | `#456C54` (muted) | `#40694F` |
| Open | `#932F1E` (red) | `#7A2B1E` | `#9A655C` (muted) | `#7A524B` |
| Unknown | `#444444` (gray) | `#555555` | `#444444` | `#555555` |

**On-colors** (text on door-colored surfaces):

| State | On Fresh Light | On Fresh Dark | On Stale Light | On Stale Dark |
|-------|----------------|---------------|----------------|---------------|
| Closed | `#BEDCC2` | `#BEDCC2` | `#A8C5B8` | `#A8C5B8` |
| Open | `#D5BAB3` | `#D5BAB3` | `#E8D7D4` | `#E8D7D4` |
| Unknown | `#C9C9C9` | `#C9C9C9` | `#C9C9C9` | `#C9C9C9` |

### Semantic Color Usage

| Context | Color Token | Purpose |
|---------|-------------|---------|
| Door status card text | `onBackground` blended 50% with door color | Tinted text that hints at door state |
| Garage door icon fill | Door state color → dark gradient | Visual door rendering |
| Check-in pill | Door color / on-door color | Top bar status indicator |
| Armed button | `primary` | Highlight confirm state |
| Progress bar (success) | `#3333FF` (blue) | Network request progress |
| Progress bar (failure) | `#FF3333` (red) | Timeout/error indicator |
| Snooze success text | `onPrimaryContainer` | High contrast on surface |
| Snooze error text | `onErrorContainer` | High contrast on surface |
| Card elevation shadow | 1dp (list items), 4dp (cards), 8dp (snooze) | Depth hierarchy |

## 3. Component States & Interaction Design

### Remote Button — State Machine UX

The button is the highest-stakes interaction: it physically moves a garage door. The tap-to-confirm pattern prevents accidental presses.

| State | Button Label | Button Color | Tappable | Progress Bar | Progress Text |
|-------|-------------|--------------|----------|--------------|---------------|
| Ready | "Garage\nButton" | surfaceDim | Yes | Empty (0/5) | "Ready" |
| Arming | "Preparing..." | surfaceDim | No | Empty (0/5) | "Arming" |
| Armed | "Tap Again\nTo Confirm" | **primary (blue)** | Yes | Empty (0/5) | "Tap to confirm" |
| NotConfirmed | "Not Pressed" | surfaceDim | No | Empty (0/5, red) | "Not confirmed" |
| Sending | "Sending..." | surfaceDim | No | 1/5 filled | "Sending" |
| Sent | "Sent" | surfaceDim | No | 3/5 filled | "Sent" |
| Received | "Door Moved!" | surfaceDim | No | 5/5 filled | "Door moved" |
| SendingTimeout | "Send Timeout" | surfaceDim | No | 2/5 filled (red) | "Sending failed" |
| SentTimeout | "No Response" | surfaceDim | No | 4/5 filled (red) | "Command not delivered" |

**Timing:**
- Ready → Arming: 500ms anti-bounce delay
- Armed window: 5 seconds to confirm, then → NotConfirmed
- NotConfirmed display: 10 seconds, then → Ready
- Network timeout: 10 seconds (Sending or Sent)
- Terminal state display: 10 seconds, then → Ready

**Key UX principle:** The Armed state is the only state with a distinct color (primary blue). This draws the eye and signals "action is possible now."

### Snooze Card — State Overlay

Two independent state dimensions displayed simultaneously:

**SnoozeState (always visible — server truth):**

| State | Display |
|-------|---------|
| Loading | "Loading snooze status..." |
| NotSnoozing | "Snooze notifications" |
| Snoozing(until) | "Snoozing notifications until {time}" + "or until the door moves" |

**SnoozeAction (overlay — last user action):**

| Action | Display | Color |
|--------|---------|-------|
| Idle | (nothing) | — |
| Sending | "Saving..." + spinner replaces Save button | default |
| Succeeded.Set(until) | "Saved! Snoozing until {time}" | onPrimaryContainer |
| Succeeded.Cleared | "Notifications resumed" | onPrimaryContainer |
| Failed.NotAuthenticated | "Sign in to snooze notifications" | onErrorContainer |
| Failed.MissingData | "No door event available" | onErrorContainer |
| Failed.NetworkError | "Couldn't reach server" | onErrorContainer |

**Timing:** Succeeded and Failed auto-reset to Idle after 10 seconds.

**Key UX principle:** A failed save never hides the current snooze state. The user always knows what's active on the server.

### Expandable Cards

- **Collapsed:** Entire card surface is tappable. Arrow icon points down.
- **Expanded:** Header row is tappable (edge-to-edge, padding inside clickable area). Arrow icon points up. Content animates in (300ms vertical expand).
- **Persistence:** Expanded/collapsed state saved to DataStore. Cards don't render until the saved state loads (avoids animation on cold start).

### Door Status Card

- Tappable anywhere to refresh data
- Shows loading indicator during fetch
- Error state shows retry button
- Duration since last change updates live (recomposes periodically)

## 4. Typography & Spacing

### Typography Scale

Uses Material 3 default type scale. No custom font family.

| Style | Usage |
|-------|-------|
| `titleLarge` | Door position name ("Open", "Closed"), history event time, card section titles, button label |
| `titleMedium` | Loading text |
| `labelSmall` | Timestamps, durations, secondary status text, snooze overlay messages, "or until the door moves" |

**Observation:** The app uses only 3 type styles. This is intentionally minimal — the garage door app is glanceable, not content-heavy. Typography hierarchy is: big status → small detail.

### Spacing System

Consistent 8dp grid with 16dp content margins.

| Token | Value | Usage |
|-------|-------|-------|
| Content horizontal padding | 16dp | Applied to all screen content within NavDisplay |
| Card internal padding | 16dp | Inside cards, expandable card headers and content |
| Vertical spacing between items | 8dp | `Arrangement.spacedBy(8.dp)` in all screen columns |
| Icon padding | 8dp | Horizontal padding around clock/calendar icons |
| Button-to-progress spacing | 8dp | Gap between circle button and progress bar |
| Expandable card animation | 300ms | Vertical expand/shrink tween |

### Elevation Hierarchy

| Level | Elevation | Usage |
|-------|-----------|-------|
| Flat | 0dp | Background, inline text |
| Low | 1dp | History list items |
| Medium | 4dp | Expandable cards, door status card, button shadow |
| High | 8dp | Snooze notification card |

### Layout Principles

- **Vertical split on Home:** Upper half = information (door status), lower half = action (remote button). Equal weight (1f each).
- **Cards fill width:** All cards use `fillMaxWidth()` with 16dp horizontal margin from screen edges.
- **History items:** Two-column layout — left column (door icon, weight 1f), right column (timestamps, weight 1f).
- **Button sizing:** Square, capped at 192dp via custom `SquareButtonWithProgress` layout. Progress bar matches button width exactly.
- **Bottom nav:** Standard Material 3 NavigationBar height. Always visible.

## 5. UX Improvement Opportunities

Areas identified during spec review that would improve the user experience. Ordered by estimated impact.

### 5.1 Home Screen Information Density

**Problem:** The door status card and remote button split the screen 50/50, but the door status (the information users check most often) gets the same space as the action button (used rarely). On shorter screens, both feel cramped.

**Proposal:** Make the door status the hero — give it ~65% of the vertical space. The button should be smaller and tucked below. Most app opens are "check the door" not "press the button."

### 5.2 Remote Button Visual Feedback

**Problem:** The button's non-Armed states all use the same `surfaceDim` color. During the request lifecycle (Sending → Sent → Received), the only visual change is the button label text and the small progress bar below. The circular button — the largest element — looks identical across 7 of 9 states.

**Proposal:** Add subtle color shifts to the button during request states:
- Sending: pulse animation or slightly lighter shade
- Received: brief green flash or checkmark overlay
- Timeout states: brief red tint

### 5.3 History Screen Enrichment

**Problem:** History items show time and door position but don't show duration-in-state (how long the door was open/closed before the next event). This is the most useful metric for checking if someone left the door open.

**Proposal:** Add a "duration in state" label to each history item (e.g., "Open for 23 minutes").

### 5.4 Empty State Design

**Problem:** No empty state design for any screen. If there are no recent events, the history screen shows a blank list. If the user isn't authenticated, the button area shows a text-only sign-in prompt.

**Proposal:** Add illustrated empty states:
- History with no events: garage door illustration + "No recent events"
- Unauthenticated home: lock icon + "Sign in to control your garage"

### 5.5 Stale Data Communication

**Problem:** Stale data (>1 hour) desaturates the door color, but this is subtle. The banner at the top is aggressive but only appears after the threshold. There's no gradual warning.

**Proposal:** Add a secondary indicator — perhaps a "Last updated: 45 min ago" label that changes color as it ages. Yellow at 30 min, orange at 45 min, red at 60 min.

### 5.6 Accessibility

**Problem:** No explicit accessibility annotations documented. Content descriptions exist for icons but contrast ratios, touch target sizes (48dp minimum), and screen reader flow aren't specified.

**Proposal:** Add accessibility section to this spec: minimum touch targets, contrast ratio requirements (WCAG AA), screen reader content descriptions for all interactive elements.

## 6. Garage Door Icon Component

The garage door icon is a custom Canvas-drawn component that appears in two contexts: large and animated on the Home screen, and small and static in History list items. It is the app's primary visual identity element.

### 6.1 Viewport & Coordinate System

The icon is drawn on a **300×300 unit viewport** with a 1:1 aspect ratio. The Canvas scales uniformly to fit the available size, centered within its bounds.

### 6.2 Frame

A U-shaped stroke path (open at bottom) with rounded top corners. Drawn ON TOP of door panels.

| Property | Value |
|----------|-------|
| Inset from viewport edge | 10 |
| Stroke width | 12 |
| Corner radius | 16 (top corners only) |
| Bottom edge Y | 290 |
| Fill | Vertical gradient (door color → 50% black blend) |

### 6.3 Door Panels

Four identical rectangular panels stacked vertically with uniform gaps. Clipped to the frame interior.

| Property | Value |
|----------|-------|
| Panel count | 4 |
| Panel X / width | 20 / 260 |
| Panel height | 61 |
| Panel corner radius | 3 |
| Gap between panels | 6 |
| Panel Y starts | 22, 89, 156, 223 |
| Clip inset | 22 (frame + stroke/2 + gap) on top and sides |

Fill: shared vertical gradient from door state color (30%) to dark blend (100%).

### 6.4 Handle

Small dark rectangle on the bottom panel: X=139, Y=278, 22×4, radius=2, color `#111111`.

### 6.5 Door Positions

| Position | Offset | Description |
|----------|--------|-------------|
| Closed | 0.0 | All 4 panels visible |
| Closing (static) | -0.20 | ~80% visible |
| Midway | -0.35 | Roughly half-open |
| Opening (static) | -0.65 | ~35% visible |
| Open | -0.75 | Only bottom of last panel |

### 6.6 Animation

Opening/Closing use `infiniteRepeatable` with `LinearEasing`, `RepeatMode.Restart`, 12-second duration. Intentionally slow for gentle background motion.

### 6.7 Overlays

Centered circular badges (30% of icon width, background-colored fill):

| Door State | Overlay | Icon | Rotation |
|------------|---------|------|----------|
| Opening | Direction arrow | ArrowForward | -90° (up) |
| Closing | Direction arrow | ArrowForward | +90° (down) |
| Midway states | Warning | Warning | None |
| Closed / Open | None | — | — |

Direction icons fill 90% of badge; warning icon fills 60%.

### 6.8 DoorPosition → Icon Mapping

| DoorPosition | Position | Animated | Overlay |
|-------------|----------|----------|---------|
| CLOSED | 0.0 | No | None |
| OPEN / OPEN_MISALIGNED | -0.75 | No | None |
| OPENING | 0.0 → -0.75 | Yes | Up arrow |
| CLOSING | -0.75 → 0.0 | Yes | Down arrow |
| OPENING_TOO_LONG / CLOSING_TOO_LONG | -0.35 | No | Warning |
| ERROR_SENSOR_CONFLICT / UNKNOWN | -0.35 | No | Warning |

### 6.9 Sizing in Context

| Context | Size | Static |
|---------|------|--------|
| Home screen | Fills available space (weight 1f) | No (animated) |
| History list item | 56dp × 56dp | Yes |

## 7. Loading States & Data Freshness

The app uses offline-first data: Room caches the last-known state, network fetches update it. Most app opens show data instantly.

### LoadingResult Model

| Variant | `.data` | Meaning |
|---------|---------|---------|
| `Loading(data)` | Cached or null | Fetch in progress |
| `Complete(data)` | Fresh | Fetch succeeded |
| `Error(exception, data)` | Cached or null | Fetch failed |

**Principle:** Never hide valid cached data during a refresh.

### Cold Start (No Cache)

| Screen | Display |
|--------|---------|
| Home — door status | "Loading..." text (`titleMedium`), top-left of card area |
| Home — button area | "Checking authentication..." centered |
| History | "Loading..." as first list item |
| Settings — snooze | "Loading snooze status..." inside card body |
| Settings — user info | Renders immediately (shows sign-in or user details) |

### Cached Refresh

| Screen | Indicator |
|--------|-----------|
| Home — door status | "Loading..." overlay, top-left corner, 8dp from edges |
| History | "Loading..." as first item, pushes cached events down |
| Settings | No visible indicator (silent 1-minute polling) |

### Fetch Triggers

- App foreground (automatic)
- FCM push notification (automatic, background)
- Tap on door status card (manual)
- Tap on history list item (manual)

### Error Recovery

Cached data stays visible. Error card appears above content with error message + "Retry" button. See section 8 for detailed error catalog.

### Screen Transition During Load

- Tab switches show cached content immediately — no blank frame
- Fade transition includes loading indicators
- `ReportDrawnWhen` gates on `Complete` for performance metrics

## 8. Error States Catalog

### 8.1 Error Severity Tiers

| Tier | Name | UI Treatment | Blocks Interaction | Auto-Dismiss |
|------|------|-------------|-------------------|--------------|
| T1 | **Blocking** | Full-screen overlay | Yes | No |
| T2 | **Degraded** | Persistent inline banner | No | When resolved |
| T3 | **Transient** | Snackbar or inline text | No | 6-10 seconds |

### 8.2 Error Inventory

#### T1 — Blocking

| ID | Trigger | Display | Recovery |
|----|---------|---------|----------|
| E-AUTH-001 | Not signed in | Centered: app icon + "Sign in to continue" + Google sign-in button | Sign-in flow |
| E-AUTH-002 | User not on allowlist | "Access Denied" + explanation. No retry — only sign-out | Contact owner |
| E-AUTH-003 | Token refresh failed (3 attempts) | "Session Expired" + sign-in button | Re-authenticate |

#### T2 — Degraded

Appear as banners between top bar and content. Max 1 banner visible (priority: network > stale > notifications).

| ID | Trigger | Banner Text | Background | Action |
|----|---------|-------------|------------|--------|
| E-NET-001 | No connectivity | "No internet connection" | errorContainer | Auto-resolves |
| E-NET-002 | Server unreachable (3 failures) | "Server unavailable" | errorContainer | "Retry" |
| E-DATA-001 | Data older than 5 minutes | "Door status may be outdated" | tertiaryContainer | "Refresh" |
| E-DATA-002 | Notifications disabled | "Notifications are off" | surfaceVariant | "Enable" |

Banner spec: 40dp height, 16dp horizontal padding, icon (18dp) + 8dp + text (Body Small) + action button. Full-bleed (no corner radius). Slide-down entry 300ms, fade-out exit 200ms.

#### T3 — Transient

| ID | Trigger | Text | Action | Duration |
|----|---------|------|--------|----------|
| E-BTN-001 | Button press HTTP error | "Couldn't send command" | "Retry" | 6s |
| E-FETCH-001 | Refresh failed | "Couldn't refresh — try again" | "Retry" | 6s |
| E-SNOOZE-* | Snooze save failed | Inline overlay (see section 3) | Auto-reset | 10s |

Snooze errors use the SnoozeAction.Failed overlay (section 3) instead of Snackbar — they appear inline in the snooze card.

### 8.3 Retry Rules

1. Automatic retries: max 3 (auth) or 5 (network), exponential backoff (1s, 2s, 4s...)
2. User "Retry" resets the automatic retry counter
3. No retry on 4xx (except 401 → silent token refresh)
4. No retry on 429 — show wait message only
5. Optimistic UI for button press — show pending state, revert on failure

## 9. Authentication Flow UX

### Sign-In Gate

Full-screen surface when no session exists. Google Sign-In button (standard branding). No skip option — auth is mandatory. System back exits the app.

### Sign-In In-Progress

1. Google account picker appears immediately (system sheet)
2. After selection: button disabled (opacity 0.38), CircularProgressIndicator (24dp) + "Verifying access..."
3. Success → instant transition to Home (no animation)
4. Failure → inline error banner between text and button

**Error banner variants:**
- Network failure: "No internet connection. Check your network and try again."
- Not authorized: "This account doesn't have access. Contact the garage owner."
- Unknown: "Something went wrong. Try again."

### Token Refresh

Completely invisible during normal operation. On failure:
1. Silent retry (2 attempts, 1s apart)
2. If exhausted: persistent banner "Session expired. Tap to sign in again." Entire banner tappable.
3. Last known door status visible with staleness timestamp
4. Remote button enters disabled state

### Sign-Out Flow

1. User taps profile row → bottom sheet with photo, name, email, "Sign out" row (error color)
2. No confirmation dialog — executes immediately
3. Snackbar on sign-in gate: "Signed out" with "Undo" action (5s window)
4. Undo uses cached credentials if still valid

### Edge Cases

- Multiple accounts: Google picker handles natively
- Poor connectivity during sign-in: 30s timeout → network error banner
- Background-to-foreground with expired token: refresh on first network request, not on resume
- Server-side account removal: 403 → immediate sign-out + "This account no longer has access"

## 10. Accessibility

### Touch Targets

| Element | Minimum | Recommended | Notes |
|---------|---------|-------------|-------|
| Door action button | 48×48dp | 72×72dp | Larger for safety criticality, glove/wet-hand use |
| Navigation items | 48×48dp | 48×48dp | Material 3 standard |
| History list rows | 48dp height | 56dp height | Tappable rows must meet minimum |
| Snooze/Save buttons | 48×48dp | 56×56dp | Secondary action |

All touch targets must have ≥8dp spacing. Door action button must have ≥16dp clearance from contradictory actions.

### Color Contrast (WCAG AA)

| Context | Required ratio | Target |
|---------|---------------|--------|
| Body text (14sp+) | 4.5:1 | 7:1 |
| Large text (18sp+) | 3:1 | 4.5:1 |
| Icon-only controls | 3:1 | 4.5:1 |

All door status colors (open/closed/unknown) must meet 4.5:1 against their background in both themes. Never rely on color alone — every status has a distinct icon shape and text label.

### Screen Reader Flow (TalkBack)

Focus order: top bar → door status card (live region, polite) → action button → snooze → history heading → history rows → bottom nav.

- Door status card: `POLITE` live region. Announces state changes once, without stealing focus.
- Error banners: `ASSERTIVE` live region. Announced immediately.
- Loading indicators: not announced to avoid "loading" spam.

### Motion Reduction

All animations respect system "Remove animations" setting. When active:
- Door icon transitions are instant
- Status color transitions are instant
- All states distinguishable from a single static frame

### One-Handed Reachability

- Door action button in lower-center quadrant (thumb zone for ≤6.7" phones)
- Destructive/infrequent actions (sign out, settings) in upper region
- No critical action requires swipe — all achievable via single tap

### Text Scaling

All text respects system font size up to 200% without truncation. Door status label may wrap to two lines — layout accommodates this.

### Validation Checklist

| Test | Pass criteria |
|------|--------------|
| Touch targets ≥48dp | No violations in Accessibility Scanner |
| Contrast ratios | No AA violations in light or dark theme |
| TalkBack focus order | Matches specified order, no orphaned elements |
| Live region announcements | Status change announced once, politely |
| Reduced motion | No animation plays; all states distinguishable |
| 200% font scale | No truncation, no overlap |
| Color independence (grayscale) | All statuses distinguishable by icon + text |

## 11. Remote Button — Visual Feedback by State

Refines section 3's state table with per-state colors, animated transitions, and haptic feedback.

### 11.1 Button Fill Colors (Light Theme)

| State | Fill (top gradient) | Text Color | Progress Accent |
|-------|-------------------|------------|-----------------|
| Ready | `#E1E2DC` (surfaceDim) | `#1B1C18` (onSurface) | `#3333FF` (blue) |
| Arming | `#D0D1CB` (slightly darker) | `#1B1C18` | `#3333FF` |
| Armed | `#A6C8FF` (primary) | `#002868` (onPrimary) | `#3333FF` |
| NotConfirmed | `#F2B8B5` (errorContainer) | `#601410` | `#FF3333` (red) |
| Sending | `#C8BFFF` (light indigo) | `#1D1148` | `#7C4DFF` (purple) |
| Sent | `#90D6A0` (soft green) | `#0A3818` | `#2E7D32` (green) |
| Received | `#4CAF50` (strong green) | `#FFFFFF` | `#1B5E20` (dark green) |
| SendingTimeout | `#FF8A80` (light red) | `#601410` | `#D32F2F` (red) |
| SentTimeout | `#FFAB91` (light orange) | `#601410` | `#E64A19` (orange) |

Bottom gradient: each fill blended 50% toward black via existing `blendColors()`.

### 11.2 Dark Theme Overrides

| State | Fill | Text Color |
|-------|------|------------|
| Ready | `#434740` | `#E4E3DD` |
| Arming | `#3B3F39` | `#E4E3DD` |
| Armed | `#6F8FD5` | `#143469` |
| NotConfirmed | `#93000A` | `#FFDAD6` |
| Sending | `#9E8CFF` | `#E8E0FF` |
| Sent | `#66BB6A` | `#0A3818` |
| Received | `#388E3C` | `#FFFFFF` |
| SendingTimeout | `#EF5350` | `#FFDAD6` |
| SentTimeout | `#FF7043` | `#FFDAD6` |

### 11.3 Color Transition Animation

| Transition | Duration | Easing |
|-----------|----------|--------|
| Ready → Arming | 150ms | Linear |
| Arming → Armed | 300ms | EaseOut |
| Armed → Sending | 200ms | EaseOut |
| Sending → Sent | 400ms | EaseInOut |
| Sent → Received | 300ms | EaseOut |
| Any → failure state | 250ms | EaseIn |
| Any → Ready (timeout) | 600ms | EaseInOut |

Use `animateColorAsState` on both fill and text colors.

### 11.4 Haptic Feedback

| Event | Pattern |
|-------|---------|
| Armed (first tap acknowledged) | `HapticFeedbackType.LongPress` |
| Received (door moved) | 50ms vibration |
| Timeout/Error | Double pulse: 40ms on, 60ms off, 40ms on |

### 11.5 Accessibility

All fill/text pairings meet WCAG AA (≥4.5:1). Color is never the sole indicator — progress bar segment count and text label provide redundant information.

## 12. Stale Data Progressive Warning

Refines proposal 5.5. Replaces the binary fresh/stale switch with graduated tiers.

### 12.1 Freshness Tiers

| Tier | Age Range | Severity |
|------|-----------|----------|
| FRESH | 0–30s | None (no indicator) |
| AGING | 30s–2min | Low |
| STALE | 2–10min | Medium |
| EXPIRED | >10min | High |

### 12.2 Freshness Badge

Horizontal pill below door status text. 28dp height, 14dp corner radius, 12dp horizontal padding. Contains: status dot (8dp) + label text (`labelSmall`).

| Tier | Dot Color | Badge Background | Text |
|------|-----------|-----------------|------|
| FRESH | — | (hidden) | — |
| AGING | `#4CAF50` (green) | surface variant 10% | "Updated {N}s ago" |
| STALE | `#FF9800` (orange) | orange 10% | "Last updated {N} min ago" |
| EXPIRED | `#F44336` (red) | red 10% + 1dp red border | "Door status may be outdated" |

**Dot animation:** STALE = slow pulse (2000ms), EXPIRED = faster pulse (1200ms).

### 12.3 Door Status Text Desaturation

| Tier | Status text opacity |
|------|-------------------|
| FRESH / AGING | 100% |
| STALE | 75% |
| EXPIRED | 50% |

Transitions use 600ms ease-in-out.

### 12.4 EXPIRED Icon Overlay

When EXPIRED: garage door icon gets a semi-transparent scrim with centered `cloud_off` icon (48dp, red). Fade-in 400ms, fade-out 300ms.

### 12.5 Refresh Affordance

Badge is tappable at STALE and EXPIRED (48dp touch target). Shows "Refreshing..." during fetch, "Refresh failed — tap to retry" on error (3s hold).

### 12.6 Timer Cadence

| Tier | Update interval |
|------|----------------|
| AGING | 5s |
| STALE | 60s |
| EXPIRED | Static text |

Tier boundary checks: every 5 seconds.

### 12.7 Dark Theme

Dot colors shift to 300-weight variants: green `#81C784`, orange `#FFB74D`, red `#E57373`. Badge backgrounds use same 10% opacity of the lighter color.

## 13. Empty States

Centered vertical stack: illustration (96dp) → title (`titleLarge`) → body (`labelSmall`, `onSurfaceVariant`) → optional action. Max width 280dp, offset 32dp above true center.

### 13.1 History — No Events

- Illustration: Garage door icon at CLOSED, 20% opacity, no animation
- Title: "No recent events"
- Body: "Door activity will appear here"
- Action: None

### 13.2 History — Loading Failed, No Cache

- Illustration: `cloud_off` icon, 96dp, 40% opacity
- Title: "Couldn't load history"
- Body: "Check your connection and try again"
- Action: "Retry" TextButton

### 13.3 Home — Not Authenticated

- Illustration: `lock_outline` icon, 96dp, 40% opacity
- Title: "Sign in to continue"
- Body: "Control your garage door and view its status"
- Action: Google Sign-In button (standard branding)

Replaces the current text-only sign-in prompt.

### 13.4 Home — Door Status Unavailable

- Illustration: Garage door icon at midway position, unknown color, 30% opacity
- Title: "No door status"
- Body: "Waiting for the first check-in from your garage"
- Action: "Refresh" TextButton

### 13.5 Transitions

| From → To | Animation |
|-----------|-----------|
| Loading → Empty | Fade-in 300ms |
| Empty → Content | Crossfade 200ms |

### 13.6 Accessibility

All illustrations have `contentDescription = null` (decorative). Title + body merged as single semantics block. Action buttons have minimum 48dp touch target.

## 14. Home Screen Layout Refinement

Closes proposal 5.1 — make door status the hero, button secondary.

### 14.1 Vertical Zones

| Zone | Content | Height | Notes |
|------|---------|--------|-------|
| A — Alerts | Error cards, notification permission, stale banner | Auto | Conditional, 0 height when absent |
| B — Hero Status | Door status card with icon, position label, duration, timestamp | `weight(2f)` | Dominates the screen |
| C — Action | Remote button + progress indicator | `weight(1f)` | Intentionally smaller |

**Ratio:** Door status gets 2/3 of the flexible space, button gets 1/3. This reflects actual usage — most opens are "check the door" not "press the button."

### 14.2 Zone B — Hero Status

- Garage icon fills available height (aspect ratio 1:1, centered)
- Door position label: `titleLarge`, centered above icon
- Duration: `labelSmall`, centered below position label
- Date/time row: calendar icon + `labelSmall`, centered at bottom
- Entire card tappable to refresh

### 14.3 Zone C — Action Button

- `SquareButtonWithProgress` layout (section from code)
- Max button size: 192dp (existing cap)
- Button + progress bar centered in zone
- Progress bar matches button width exactly

### 14.4 Weight Change from Current

Current: `weight(1f)` each (50/50 split)
Proposed: `weight(2f)` status / `weight(1f)` button (67/33 split)

This gives the door status ~67% of available space. On a typical 640dp content area (minus top bar and bottom nav), the status gets ~427dp and the button gets ~213dp — enough for a 192dp button with 21dp of breathing room.

## 15. History — Duration in State

Closes proposal 5.3. Each history row shows how long the door stayed in that state.

### 15.1 Layout

Duration badge on the same line as the timestamp, separated by middle-dot:

```
[Icon]  Opened
        2:34 PM · Open 23 min
```

- Duration text: `bodySmall`, `onSurfaceVariant`
- Middle-dot: U+00B7 with spaces
- Current (topmost) event: "Open 23 min so far" in `primary` color (live-updating)

### 15.2 Calculation

- Event N duration = `timestamp(N+1) - timestamp(N)`
- Current event = `now - timestamp(0)`, updated every 60s
- If server provides `durationSeconds`, use it directly

### 15.3 Formatting

| Duration | Display |
|----------|---------|
| < 1 min | "Under 1 min" |
| 1-59 min | "{n} min" |
| 1-23 hr | "{h} hr {m} min" |
| 1+ days | "{d} days {h} hr" |

Never show seconds (false precision from polling sensors).

### 15.4 Edge Cases

- Missing timestamp → omit badge
- Negative duration (clock skew) → omit badge, log warning
- Single event → treat as ongoing with "so far"
- Device offline → freeze at last-known time; stale banner is primary signal

### 15.5 Accessibility

Content description uses unabbreviated words: "Opened at 2:34 PM, open for 23 minutes so far."

## 16. FCM Push Notification Design

### 16.1 Channels

| Channel | Name | Importance | Sound | Vibrate |
|---------|------|-----------|-------|---------|
| `door_alert` | Door Alerts | HIGH | Default | Yes |
| `door_status` | Door Status Updates | MIN | None | No |

`door_alert` for user-visible warnings (door left open). `door_status` reserved for future silent status notifications.

### 16.2 Notification Anatomy

**Collapsed:**
- Small icon: monochrome garage silhouette, 24dp, accent `#226B43` (green — app identity)
- Large icon: 40dp circle, door-state color fill, white garage glyph
- Title: "Garage door open" (varies by state)
- Body: "Open for more than 15 minutes"

**Expanded (2 action buttons):**
- "Snooze 1h" — sends snooze via BroadcastReceiver without opening app
- "Open App" — launches MainActivity

### 16.3 Grouping

- Collapse key: `door_not_closed` (server-set). Replaces previous alert.
- Tag: `door_alert_current`, ID: 1001. Only one alert visible at a time.
- Auto-cancel: true. Ongoing: false (swipeable).

### 16.4 Content by Door State

| State | Title | Body |
|-------|-------|------|
| Open | Garage door open | Open for more than {duration} |
| Opening/ClosingTooLong | Door not closed | Door not closed for more than {duration} |
| Unknown | Unknown door status | Error not resolved for longer than {duration} |
| ErrorSensorConflict | Door error | Door error for longer than {duration} |

### 16.5 Lock Screen

Visibility: PUBLIC (not sensitive — user needs to see "door open" without unlocking). Heads-up on first post only; updates don't re-trigger.

### 16.6 Snooze from Notification

Tap "Snooze 1h" → BroadcastReceiver → SnoozeNotificationsUseCase. On success: notification body → "Snoozed until {time}", auto-dismiss 3s. On failure: toast "Could not snooze. Open the app to try again."

## 17. Dark Mode & OLED

### 17.1 Surface Hierarchy

| Tier | Light | Dark (OLED) | Usage |
|------|-------|-------------|-------|
| Background | `#FFFBFE` | `#000000` | App canvas |
| Surface 1 | `#F4EFF4` | `#0E0E0E` | Door status card, history list |
| Surface 2 | `#E8E0E5` | `#1A1A1A` | Elevated cards (settings) |
| Surface 3 | `#DDD8DD` | `#252525` | Bottom nav, top app bar |

OLED rule: background is always pure black. No in-app dark/light toggle — follow system setting.

### 17.2 Door State Colors (Dark)

Desaturated, lighter variants for contrast against dark surfaces:

| State | Light | Dark | Min contrast vs `#0E0E0E` |
|-------|-------|------|--------------------------|
| Closed | `#2E7D32` | `#81C784` | 4.8:1 |
| Open | `#E65100` | `#FFB74D` | 5.1:1 |
| Opening/Closing | `#F9A825` | `#FFF176` | 5.4:1 |
| Error/Unknown | `#C62828` | `#EF9A9A` | 4.6:1 |

### 17.3 Component Adjustments

- **Status indicator:** Outlined circle (2dp stroke) instead of filled — prevents bright region at night
- **Action button:** Tonal variant — state color at 24% opacity over Surface 2
- **History dividers:** `outlineVariant` at 16% opacity (near-invisible on OLED, rely on spacing)
- **Settings cards:** Surface 2 background (visually separates from near-black Surface 1)
- **Error screens:** Surface 2 dialog background; error icon uses `#EF9A9A` (avoids halation)
- **Loading:** Pure black background + single progress indicator (avoids OLED burn-in)

### 17.4 Dynamic Color

Allow Material You to override `primary`/`secondary` but NOT door-state colors. State colors are safety-critical and must remain fixed.

## 18. Transition Animations Catalog

### 18.1 Motion Principles

- **Fast by default.** No animation exceeds 400ms. Users check door status in a hurry.
- **Informative, not decorative.** Every animation communicates a state change.
- **Interruptible.** All animations cancelable mid-flight.

### 18.2 Easing Curves

| Token | Curve | Use |
|-------|-------|-----|
| Standard | `CubicBezier(0.2, 0.0, 0.0, 1.0)` | Most transitions |
| Decelerate | `CubicBezier(0.0, 0.0, 0.0, 1.0)` | Elements entering |
| Accelerate | `CubicBezier(0.3, 0.0, 1.0, 1.0)` | Elements exiting |

### 18.3 Navigation

| Transition | Type | Duration |
|-----------|------|----------|
| Tab switch | Crossfade | 150ms |
| Auth → Home | Shared axis (vertical, +30dp) | 300ms |
| Back | Reverse shared axis | 250ms |

### 18.4 Door State Changes

- Color crossfade + icon morph: 300ms
- Icon uses fade-through: old out (90ms), gap (30ms), new in (180ms)
- Warning banners: slide down 250ms enter, fade out 200ms exit

### 18.5 Card Expand / Collapse

- Expand: 250ms, content fades in at 60% of height animation
- Collapse: 200ms (faster than expand per Material guidance), content fades out first (100ms)
- Chevron rotates 0° ↔ 180°

### 18.6 History List

- Initial load: staggered fade-in (50ms per item, 200ms each)
- New event prepended: slide from top + fade, 250ms
- Beyond-fold items animate only when scrolled into view

### 18.7 Error Transitions

- Banner enter: slide down 250ms
- Banner dismiss: fade + collapse 200ms
- Retry fail: shake animation (±4dp → ±2dp → 0, 300ms)

### 18.8 Accessibility Override

When `areAnimatorsEnabled() == false`: all animations complete at 0ms. No motion.

## 19. Micro-Copy Guide

### 19.1 Voice Principles

| Principle | Example |
|-----------|---------|
| **Direct** | "Door is open" not "Sensor reading indicates open state" |
| **Calm** | "Could not reach server" not "CRITICAL CONNECTION FAILURE" |
| **Brief** | One sentence for status; two max for errors (what + what to do) |
| **Honest** | "Door status unknown" not "Door is closed" when data is stale |

### 19.2 Canonical Terms

| Use | Never use |
|-----|-----------|
| door | garage, gate, panel |
| open / closed | up / down |
| tap | click, press |
| sign in / sign out | log in / log out |
| server | cloud, backend |
| unknown | unavailable, N/A |
| snooze | mute, silence |

### 19.3 Sentence Rules

- **Status labels:** Sentence case, no period. "Door is open"
- **Action buttons:** Verb phrase, title case. "Sign In", "Retry"
- **Error body:** Problem + remedy, two clauses. "Could not reach server. Check your connection."
- **Empty states:** What will appear, sentence case, period. "Door activity will appear here."
- **Timestamps:** Relative < 24h ("3 min ago"), absolute otherwise ("Apr 8, 2:14 PM"). Never raw epoch.

### 19.4 Error Message Templates

| Scenario | Short | Body | Action |
|----------|-------|------|--------|
| No network | "No connection" | "Could not reach server. Check your connection and try again." | "Retry" |
| Token expired | "Session expired" | "Sign-in expired. Tap to sign in again." | "Sign In" |
| Server error | "Server error" | "Something went wrong on our end. Try again in a moment." | "Retry" |
| Unknown state | "Status unknown" | "Door status could not be determined." | "Refresh" |
| Press failed | "Press failed" | "Could not send the door command." | "Retry" |
| Not authorized | "Not authorized" | "Your account does not have access. Contact the door owner." | "Sign Out" |

### 19.5 Numeric Rules

- "1 event" / "2 events" — always include count
- Durations in UI: "3 min ago", "1 hr ago". In prose: "30 minutes"
- Never negative or "0 seconds" — floor to "Just now"

## 20. Responsive Layout

### 20.1 WindowSizeClass Breakpoints

| Class | Width | Typical devices |
|-------|-------|----------------|
| Compact | < 600dp | Phone portrait |
| Medium | 600–839dp | Phone landscape, small tablet |
| Expanded | ≥ 840dp | Tablet landscape, large tablet, foldables |

### 20.2 Home Screen

**Compact:** Single column. Door status (`weight(2f)`) above button (`weight(1f)`).

**Medium:** Side-by-side 50/50. Left: door status + button (centered, max 360dp). Right: history list.

**Expanded:** Centered max 960dp. Left pane 400dp fixed (status + button). Right pane fills remainder (history, max 560dp).

### 20.3 Settings Screen

All breakpoints: centered column, max-width 480dp. No two-pane layout — settings content doesn't benefit from it.

### 20.4 History List

No multi-column grid at any breakpoint — list items are sequential. Items span full pane width. At Expanded, items capped at 560dp with 32dp end padding.

### 20.5 Auth Screen

All breakpoints: centered column, max-width 360dp. Vertically centered with -48dp offset.

### 20.6 Foldable

When `FoldingFeature` reports a vertical hinge, align the pane split to the hinge boundary instead of 50/50.

## 21. Home Screen Widget

### 21.1 Variants

| Variant | Grid | Content |
|---------|------|---------|
| Small | 2×1 | Icon + status label |
| Medium | 3×2 | Icon + status + timestamp (centered) |
| Large | 4×2 | Icon + status + timestamp + "View Details" button |

No remote-action button in any widget — prevents accidental presses (physical-confirmation philosophy).

### 21.2 State Mapping

| State | Label | Background |
|-------|-------|------------|
| Closed | "Closed" | surfaceContainer |
| Open | "Open" | warningContainer (amber) |
| Opening/Closing | "Opening…"/"Closing…" | surfaceContainer |
| Error | "Check Door" | errorContainer |
| Unknown / stale | "Updating…" | surfaceContainerHighest |

### 21.3 Refresh

- Periodic: every 15 min (Android minimum)
- On app foreground: broadcast triggers widget update
- On FCM push: refresh before notification display

### 21.4 Tap Behavior

Small/Medium: entire surface → opens app. Large: surface + "View Details" button → opens app.

### 14.5 Landscape / Tablet

- **Landscape:** Side-by-side — door status left, button right (50/50 horizontal)
- **Tablet (600dp+):** Max content width 480dp centered. Status icon grows to 120dp, status label to 40sp. Button stays 192dp.
