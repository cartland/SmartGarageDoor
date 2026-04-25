---
category: reference
status: active
last_verified: 2026-04-25
---

# Door Animation

Contract for the animated `GarageIcon` rendered in `DoorStatusCard` and the recent events list. The icon translates a [`DoorPosition`](../domain/src/commonMain/kotlin/com/chriscartland/garage/domain/model/DoorPosition.kt) into a vertical door offset and animates between offsets as the state changes.

See ADR-025 in [`DECISIONS.md`](DECISIONS.md) for the design decision that motivated this contract.

## Design principle

**Target = pure function of state.** Same `DoorPosition` always produces the same target offset, regardless of where the animation currently is. The animation framework owns the *trajectory* (current value, current velocity, spec → next frame); the app owns the *target* (state → offset).

Five pure mappings on the [`DoorAnimation`](../androidApp/src/main/java/com/chriscartland/garage/ui/AnimatableGarageDoor.kt) object:

| Function | Returns | Used by |
|----------|---------|---------|
| `DoorAnimation.targetPositionFor(state)` | offset to animate toward | `LaunchedEffect` |
| `DoorAnimation.initialPositionFor(state)` | seed value for `Animatable` on first composition | `remember { Animatable(...) }` |
| `DoorAnimation.useSpringFor(state)` | `false` → tween (motion); `true` → spring (settle) | `LaunchedEffect` |
| `DoorAnimation.staticPositionFor(state)` | offset to render when `static = true` (no animation) | recent events list |
| `DoorAnimation.overlayFor(state)` | which overlay icon (arrow up/down, warning, none) to draw | `DoorIconBox` |

Each is an exhaustive `when` over `DoorPosition` with no `else`. Adding a new enum value fails to compile until every mapping is updated.

## State table

| State | `target` | `initial` | spec | overlay |
|-------|----------|-----------|------|---------|
| `UNKNOWN` | `MIDWAY_POSITION` | target | spring | warning |
| `CLOSED` | `CLOSED_POSITION` | target | spring | none |
| `OPENING` | `OPEN_POSITION` | `CLOSED_POSITION` | tween (linear, 12s) | arrow up |
| `OPENING_TOO_LONG` | `MIDWAY_POSITION` | target | spring | warning |
| `OPEN` | `OPEN_POSITION` | target | spring | none |
| `OPEN_MISALIGNED` | `OPEN_POSITION` | target | spring | none |
| `CLOSING` | `CLOSED_POSITION` | `OPEN_POSITION` | tween (linear, 12s) | arrow down |
| `CLOSING_TOO_LONG` | `MIDWAY_POSITION` | target | spring | warning |
| `ERROR_SENSOR_CONFLICT` | `MIDWAY_POSITION` | target | spring | warning |

Position constants in `AnimatableGarageDoor.kt`:

| Constant | Value | Meaning |
|----------|-------|---------|
| `CLOSED_POSITION` | `0.0f` | Door fully closed, panels at rest in frame |
| `MIDWAY_POSITION` | `-0.35f` | Mid-cycle position used for warning / unknown states |
| `OPEN_POSITION` | `-0.75f` | Door fully open |
| `OPENING_STATIC_POSITION` | `-0.65f` | Mid-cycle snapshot for `static = true` opening |
| `CLOSING_STATIC_POSITION` | `-0.20f` | Mid-cycle snapshot for `static = true` closing |

Negative offsets correspond to the door sliding upward (opening). The `GarageDoorCanvas` translates panels by `doorOffset * containerHeight`.

## Spring spec

```kotlin
spring(
    dampingRatio = Spring.DampingRatioNoBouncy,    // 1.0 — critically damped, zero overshoot
    stiffness = Spring.StiffnessVeryLow,           // 50 — slow settle
)
```

`initialVelocity = 0f` is passed explicitly so a tween → spring transition does not carry over the tween's residual velocity past the target. This keeps terminal arrivals visually still after settling.

## Tween spec

```kotlin
tween(
    durationMillis = DEFAULT_GARAGE_DOOR_ANIMATION_DURATION.toMillis().toInt(),  // 12000
    easing = LinearEasing,
)
```

Linear easing matches the roughly constant-speed motion of a real garage door.

## Trade-offs

**No elapsed-time-based start position.** When the app opens with the door already in motion, the icon starts at the "from" end (`initialPositionFor`) and animates the full motion from scratch. We do not compute `now() - lastChangeTimeSeconds` to seed the position mid-motion because clock drift between the device and the server is significant — relying on it would put the icon in a wrong position silently. The visible cost is that opening the app mid-motion shows the icon "starting over" rather than catching up to physical reality. The animation is now an indicator of *state*, not an attempt to mirror the door's literal current position.

**Always animate to target on state change.** No `|current - target| < ε` skip — that would make behavior depend on current animation value, defeating the "target is pure" principle. If `current ≈ target`, the spring is a near-noop with no perceived movement.

**No client-side direction-flip debounce.** If the server emits `Opening → Closing → Opening` rapidly, the icon visibly waffles. That's the correct response to what the server reported; jitter is a server bug to fix at the source, not paper over in the client.

## Verification

| Layer | What it verifies | Where |
|-------|------------------|-------|
| Unit (mapping) | Each `DoorAnimation.*For` function returns the documented value for every `DoorPosition` | [`GarageDoorAnimationMappingTest`](../androidApp/src/test/java/com/chriscartland/garage/ui/GarageDoorAnimationMappingTest.kt) |
| Compile-time | Every `DoorPosition` enum value is mapped (exhaustive `when`, no `else`) | The compiler — adding a new enum value breaks the build |
| Screenshot | The icon renders as expected for each state in light + dark themes | [`GarageDoorScreenshotTest`](../android-screenshot-tests/src/screenshotTest/kotlin/com/chriscartland/garage/screenshottests/GarageDoorScreenshotTest.kt), regenerate via `./scripts/generate-android-screenshots.sh` |

Screenshot tests use `static = true` so they render deterministically (no `mainClock`-dependent animation frames). The current AGP `screenshot` plugin renders Compose previews and does not expose `mainClock`; adding intermediate-frame screenshots would require switching to Paparazzi/Roborazzi and is intentionally out of scope.

## Updating the contract

To add a new `DoorPosition` value:

1. Add the enum value in [`DoorPosition.kt`](../domain/src/commonMain/kotlin/com/chriscartland/garage/domain/model/DoorPosition.kt). The compiler will fail every `when` in the mapping functions.
2. Decide the target/initial/spec/overlay for the new state and update each function.
3. Update this doc's state table.
4. Update [`GarageDoorAnimationMappingTest`](../androidApp/src/test/java/com/chriscartland/garage/ui/GarageDoorAnimationMappingTest.kt) with assertions for the new value.
5. Add a `@Preview` in `AnimatableGarageDoor.kt` and a screenshot test in `GarageDoorScreenshotTest.kt` so the visual is captured.

To change a target/spec for an existing state:

1. Update the appropriate mapping function.
2. Update the state table above.
3. Update the test to pin the new value.
4. Regenerate screenshots.

## Out of scope (deferred)

- **Per-direction empirical duration estimate.** Average completed motion durations from the event history (separately for opening and closing) and feed `tween` `durationMillis`. Tracked as a follow-up — current behavior is hardcoded 12s.
- **Out-of-order event guard at the data layer.** If the repository emits a stale `OPENING` after a fresh `OPEN`, the icon dutifully animates the wrong direction. A monotonicity guard in `NetworkDoorRepository` would fix it; deferred unless we observe the bug in production.
- **Reduced-motion handling.** Compose `tween`/`spring` already honor `Settings.Global.ANIMATOR_DURATION_SCALE` since Compose 1.2. No custom `LocalAccessibilityManager` handling is needed unless we measure a problem.

## References

- [`AnimatableGarageDoor.kt`](../androidApp/src/main/java/com/chriscartland/garage/ui/AnimatableGarageDoor.kt) — pure mapping functions, position constants, overlays
- [`GarageIcon.kt`](../androidApp/src/main/java/com/chriscartland/garage/ui/GarageIcon.kt) — composable that hosts the `Animatable` and `LaunchedEffect`
- [`GarageDoorCanvas.kt`](../androidApp/src/main/java/com/chriscartland/garage/ui/GarageDoorCanvas.kt) — Canvas-based door rendering driven by `doorOffset`
- [`DoorPosition.kt`](../domain/src/commonMain/kotlin/com/chriscartland/garage/domain/model/DoorPosition.kt) — the enum
- [`DECISIONS.md`](DECISIONS.md) ADR-025 — design decision that motivated this contract
