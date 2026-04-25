---
category: archive
status: shipped
---
# Plan: Remove PushStatus StateFlow (ADR-013 Violation Fix)

## Problem

`RemoteButtonRepository.pushButtonStatus: StateFlow<PushStatus>` uses StateFlow to signal
transient events (SENDING → IDLE). StateFlow conflates intermediate values — if SENDING
and IDLE are set in quick succession, collectors may only see IDLE, silently dropping
the SENDING signal.

**User-visible bug:** After confirming the button press, the UI shows "Sending..." but
never transitions to "Waiting..." (server acknowledged). The door may open, causing the
UI to jump straight to "Done!" — skipping the acknowledgment step. Or the user stares at
"Sending..." indefinitely because the timeout never starts.

## Why existing tests missed this

The fake repository set SENDING→IDLE synchronously. Under TestDispatcher, runCurrent()
processed everything deterministically, so tests passed. Under production Dispatchers.IO,
the collector ran on a different thread and missed the intermediate value. The test and
production used fundamentally different timing models.

## Solution

Remove PushStatus entirely. The ViewModel tells the state machine directly based on the
suspend call lifecycle:

- Before calling UseCase → `stateMachine.onNetworkStarted()`
- After UseCase returns success → `stateMachine.onNetworkCompleted()`
- If UseCase returns error → `stateMachine.reset()`

## Changes

### Phase 1: Add direct methods to ButtonStateMachine

Add `onNetworkStarted()` and `onNetworkCompleted()` that send events through the existing
Channel. Remove the `pushButtonStatus` Flow constructor parameter and its `drop(1)` collector.

### Phase 2: Update RemoteButtonViewModel

Replace the `ObservePushButtonStatusUseCase` dependency with direct state machine calls
around the `PushRemoteButtonUseCase` suspend call.

### Phase 3: Remove PushStatus from repository chain

- Remove `PushStatus` enum from domain model
- Remove `pushButtonStatus: StateFlow<PushStatus>` from `RemoteButtonRepository` interface
- Remove `_pushButtonStatus` and `yield()` from `NetworkRemoteButtonRepository`
- Remove `ObservePushButtonStatusUseCase`
- Remove `setPushStatus()` and StateFlow from `FakeRemoteButtonRepository`
- Remove DI wiring in `AppComponent`

### Phase 4: Update tests

- ButtonStateMachineTest: use `onNetworkStarted()`/`onNetworkCompleted()` instead of
  `pushStatus.value = PushStatus.SENDING/IDLE`
- RemoteButtonViewModelTest: verify full state sequence
  (Ready → Preparing → AwaitingConfirmation → SendingToServer → SendingToDoor → Succeeded)
- RemoteButtonViewModelTest: verify error resets to Ready

### Phase 5: Validate

- `./scripts/validate.sh` passes
- No references to PushStatus remain in production code

## Why new tests catch the bug

The tests and production use the same signal mechanism — a suspend function return.
There is no dispatcher-dependent timing, no parallel collector, no conflation. If the
test passes, production works, because the code path is identical.

## UX mapping

| State | Button Text | Network Diagram | Triggered by |
|-------|-------------|-----------------|--------------|
| Ready | "Garage Door Button" | all gray | App launch |
| Preparing | disabled | phone lit | First tap |
| AwaitingConfirmation | "Door will move. Confirm?" | phone lit | 500ms delay |
| SendingToServer | "Sending..." | phone→server animating | Second tap + onNetworkStarted() |
| SendingToDoor | "Waiting..." | phone✓, server→door animating | UseCase returns success |
| Succeeded | "Done!" | all green | Door position changes |
| ServerFailed | "Failed" | phone→server red | 10s timeout |
| DoorFailed | "Failed" | server→door red | 10s timeout |
| Cancelled | "Cancelled" | all gray | 5s no second tap |
