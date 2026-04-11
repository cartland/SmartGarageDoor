# Button UX Redesign — Implementation Plan

Reference: ADR-012 in `docs/DECISIONS.md`

## Phase 1: Rename RemoteButtonState variants

Pure rename — no behavior or UI changes. Every file compiles and every test passes after this phase.

**Changes:**
- `domain/.../RemoteButtonModel.kt` — rename sealed interface variants:
  - Arming → Preparing
  - Armed → AwaitingConfirmation
  - NotConfirmed → Cancelled
  - Sending → SendingToServer
  - Sent → SendingToDoor
  - Received → Succeeded
  - SendingTimeout → ServerFailed
  - SentTimeout → DoorFailed
- `usecase/.../ButtonStateMachine.kt` — update all references
- `usecase/.../RemoteButtonViewModel.kt` — update references
- `presentation-model/.../HomeScreenState.kt` — update default value if needed
- `androidApp/.../RemoteButtonContent.kt` — update all `when` branches + preview names
- `androidApp/.../HomeContent.kt` — update references
- `usecase/...test/.../ButtonStateMachineTest.kt` — update all assertions
- `usecase/...test/.../RemoteButtonViewModelTest.kt` — update assertions
- `android-screenshot-tests/.../ComponentsScreenshotTest.kt` — update preview references
- `docs/ARCHITECTURE.md`, `docs/MIGRATION.md` — update state names in prose

**Verification:** `./scripts/validate.sh` passes. All existing tests pass with zero behavior change.

---

## Phase 2: Create NetworkProgressDiagram composable

New generic composable. No integration with button yet — standalone with previews.

**New file:** `androidApp/.../ui/NetworkProgressDiagram.kt`

**Data model:**
```kotlin
enum class NodeStatus { Idle, Active, Succeeded, Failed }
enum class EdgeStatus { NotStarted, InProgress, Succeeded, Failed }

data class NetworkDiagramState(
    val nodes: List<NodeStatus>,  // e.g. [Succeeded, Active, Idle]
    val edges: List<EdgeStatus>,  // e.g. [Succeeded, InProgress]
)
```

**Composable:**
```kotlin
@Composable
fun NetworkProgressDiagram(
    state: NetworkDiagramState,
    icons: List<ImageVector>,  // phone, cloud, house
    modifier: Modifier = Modifier,
)
```

**Visual spec:**
- Three icon nodes connected by two edges, horizontal, evenly spaced
- Icons: Material icons (`PhoneAndroid`, `Cloud`, `Home`) — or custom drawables if needed
- Node colors: gray (idle), theme color (active), green (succeeded), red (failed)
- Edge rendering:
  - NotStarted: gray dashed line
  - InProgress: animated dotted line moving forward (dots translate left→right)
  - Succeeded: solid green line
  - Failed: solid red line

**Previews:** One preview per interesting state combination (idle, sending to server, sent to door, all succeeded, server failed, door failed).

**Tests:** Snapshot/screenshot tests for each preview.

**Verification:** Previews render correctly. Screenshot tests generate reference images.

---

## Phase 3: Create GarageDoorButton composable

New Material3 button composable. No integration with state machine yet — takes explicit parameters.

**New file:** `androidApp/.../ui/GarageDoorButton.kt`

**Composable:**
```kotlin
@Composable
fun GarageDoorButton(
    state: RemoteButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Behavior by state:**
| State | Text | Enabled | Color |
|-------|------|---------|-------|
| Ready | "Garage Door Button" | yes | FilledTonalButton (default) |
| Preparing | "Garage Door Button" | no | FilledTonalButton (default) |
| AwaitingConfirmation | "Door will move." + "Confirm?" | yes | Amber/caution container |
| Cancelled | "Cancelled" | no | Default |
| SendingToServer | "Sending..." | no | Default |
| SendingToDoor | "Waiting..." | no | Default |
| Succeeded | "Done!" | no | Default |
| ServerFailed | "Failed" | no | Default |
| DoorFailed | "Failed" | no | Default |

**Key details:**
- AwaitingConfirmation uses two separate `Text` composables (not `\n`)
- Parent provides width via `modifier` — button fills it in all states
- Standard Material3 `Button` / `FilledTonalButton` — no custom gradient or shape

**Previews:** One per state (9 total).

**Verification:** Previews render correctly.

---

## Phase 4: Create mapping from RemoteButtonState to NetworkDiagramState

Pure function, fully unit-testable.

**Location:** `androidApp/.../ui/RemoteButtonContent.kt` (or a new mapper file)

```kotlin
fun RemoteButtonState.toNetworkDiagramState(): NetworkDiagramState
```

| State | Phone | Edge 1 | Server | Edge 2 | Door |
|-------|-------|--------|--------|--------|------|
| Ready | Idle | NotStarted | Idle | NotStarted | Idle |
| Preparing | Active | NotStarted | Idle | NotStarted | Idle |
| AwaitingConfirmation | Active | NotStarted | Idle | NotStarted | Idle |
| Cancelled | Idle | NotStarted | Idle | NotStarted | Idle |
| SendingToServer | Active | InProgress | Idle | NotStarted | Idle |
| SendingToDoor | Succeeded | Succeeded | Active | InProgress | Idle |
| Succeeded | Succeeded | Succeeded | Succeeded | Succeeded | Succeeded |
| ServerFailed | Failed | Failed | Idle | NotStarted | Idle |
| DoorFailed | Succeeded | Succeeded | Failed | Failed | Idle |

**Tests:** Unit test for each state → expected NetworkDiagramState mapping.

**Verification:** Unit tests pass.

---

## Phase 5: Wire new components into RemoteButtonContent

Replace the old `SquareButtonWithProgress` with `GarageDoorButton` + `NetworkProgressDiagram` in a vertical layout.

**Changes to `RemoteButtonContent.kt`:**
- Replace `SquareButtonWithProgress` call with `Column` containing `GarageDoorButton` + `NetworkProgressDiagram`
- Parent gives both components the same width modifier
- Remove old `buttonLabel()`, `toProgressData()`, `isTappable()` private functions
- Update all 9 preview composables

**Changes to screenshot tests:**
- Update `ComponentsScreenshotTest.kt` preview function names if changed

**Verification:** `./scripts/validate.sh` passes. Previews show new design.

---

## Phase 6: Remove dead code

Remove components that are no longer used after Phase 5.

**Candidates for removal:**
- `SquareButtonWithProgress` composable (if no other callers)
- `GradientButton` composable (if no other callers)
- `ButtonProgressIndicator` composable
- `ParallelogramProgressBar` composable (check for other usages)
- `ProgressIndicatorData` data class
- `blendColors` utility (check for other usages)

**Verification:** `./scripts/validate.sh` passes. No unused code warnings.

---

## Phase 7: Generate screenshots and update docs

- Run `./scripts/generate-android-screenshots.sh`
- Update `docs/ARCHITECTURE.md` — state names in prose
- Update `docs/MIGRATION.md` — state names in prose
- Verify screenshot gallery shows new button design

**Verification:** Gallery looks correct. All docs reference new state names.

---

## Commit Strategy

One commit per phase. All on the same branch (`feature/button-ux-redesign`). Single PR at the end.

Each phase must pass `./scripts/validate.sh` before committing.
