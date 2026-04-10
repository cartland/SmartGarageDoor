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
