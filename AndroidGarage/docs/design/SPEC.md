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
