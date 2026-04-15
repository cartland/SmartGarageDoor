package com.chriscartland.garage.domain.model

/**
 * Unified state for the remote garage button.
 *
 * Combines tap-to-confirm interaction with network/door request tracking
 * into a single sum type. The button can only be in one state at a time.
 *
 * State flow (happy path):
 *   Ready → tap → Preparing → AwaitingConfirmation → tap →
 *   SendingToServer → SendingToDoor → Succeeded → Ready
 *
 * Failure paths:
 *   AwaitingConfirmation → (5s timeout) → Cancelled → Ready
 *   SendingToServer → (10s timeout) → ServerFailed → Ready
 *   SendingToDoor → (10s timeout) → DoorFailed → Ready
 *
 * See ButtonStateMachine for transition logic.
 */
sealed interface RemoteButtonState {
    /** Default state. First tap begins the confirmation flow. */
    data object Ready : RemoteButtonState

    /** Brief pause after first tap (anti-bounce). */
    data object Preparing : RemoteButtonState

    /** Waiting for second tap to confirm. Times out to Cancelled. */
    data object AwaitingConfirmation : RemoteButtonState

    /** User did not confirm in time. Brief display before returning to Ready. */
    data object Cancelled : RemoteButtonState

    /** Network request to server in flight. */
    data object SendingToServer : RemoteButtonState

    /** Server acknowledged, waiting for door movement. */
    data object SendingToDoor : RemoteButtonState

    /** Door moved — request fulfilled. Brief display before returning to Ready. */
    data object Succeeded : RemoteButtonState

    /** Network call to server did not return in time. */
    data object ServerFailed : RemoteButtonState

    /** Door did not move after server acknowledged. */
    data object DoorFailed : RemoteButtonState
}
