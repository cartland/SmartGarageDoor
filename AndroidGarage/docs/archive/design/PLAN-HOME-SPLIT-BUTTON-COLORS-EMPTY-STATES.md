# Design Implementation Plan — High Value, Low Effort

These three changes improve the most-used screens with minimal code.

## 1. Home screen 67/33 split

**What:** Change door status / button vertical ratio from 50/50 to 67/33.

**Why:** Most app opens are "check the door" not "press the button." The status card deserves more space.

**Change:** In `HomeContent.kt`, change `Modifier.weight(1f)` on the status Box to `weight(2f)`. Button Box stays `weight(1f)`.

**Risk:** Low. Test on a short phone (640dp content height) to verify the button zone isn't too cramped. Adjust to 60/40 if needed.

## 2. Button color for Received and Timeout states

**What:** Add green fill for Received ("Door Moved!") and red fill for timeout states. Keep all other states as-is (surfaceDim gray, Armed blue).

**Why:** The button is the largest element but looks identical across 7 of 9 states. Users need visual confirmation that the door actually moved. Two colors (green for success, red for failure) cover the critical feedback gap without overcomplicating the palette.

**Changes in `RemoteButtonContent.kt`:**
- Received: fill `#4CAF50` (green), text `#FFFFFF`
- SendingTimeout: fill `#FF8A80` (light red), text `#601410`
- SentTimeout: fill `#FFAB91` (light orange), text `#601410`

Animate transitions with existing `animateColorAsState`. Update screenshot tests.

**Risk:** Low. Only 3 states change. Verify contrast in dark mode.

## 3. Empty states for History and unauthenticated Home

**What:** Replace blank screens with centered illustration + title + body text.

**Why:** Blank screens erode trust. New users see nothing on first launch before sign-in.

**Changes:**
- History empty: faded garage icon (20% opacity) + "No recent events" + "Door activity will appear here"
- Home unauthenticated: `lock_outline` icon (40% opacity) + "Sign in to continue" + "Control your garage door and view its status" + Google Sign-In button

Reuse existing composables (GarageIcon, GradientButton for sign-in). New shared `EmptyStateLayout` composable with centered stack.

**Risk:** Low. No backend changes. Pure UI.

---

## Sections needing review before implementation

The following spec sections (from the 25-iteration design process) describe larger changes that need explicit review to decide if we want them at all. They should not be implemented without discussion.

- **Section 8 (Error Catalog):** T1/T2/T3 framework is sound but most T2 errors (connectivity detection, 5-min stale banner) don't exist yet. Decide which to build.
- **Section 9 (Auth Flow):** Sign-out bottom sheet and undo Snackbar add complexity. Current simple button may be fine.
- **Section 11 (Full Button Colors):** This plan implements 3 of 9 state colors. The other 6 (Arming, NotConfirmed, Sending, Sent) need visual testing before committing.
- **Section 12 (Stale Warning):** Four tiers with pulsing dots may be overkill. A simpler "Updated Xm ago" label might suffice.
- **Section 15 (History Duration):** Requires server-side duration data or fragile client-side calculation.
- **Section 16 (Notifications):** Snooze-from-notification action button is significant scope.
- **Section 17 (Dark Mode):** Outlined status indicator and tonal button change visual identity.
- **Section 18 (Animations):** New animations are polish, not priority.
- **Section 20 (Responsive):** Good target for Phase 41 but substantial work.
- **Section 21 (Widget):** Separate feature with its own scope.
- **Section 22 (Haptics):** Polish, not priority.
- **Section 25 (Onboarding):** May be unnecessary for a single-user utility app.
