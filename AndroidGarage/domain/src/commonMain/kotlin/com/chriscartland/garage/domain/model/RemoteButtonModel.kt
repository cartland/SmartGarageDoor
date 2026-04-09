package com.chriscartland.garage.domain.model

enum class RequestStatus {
    NONE,
    SENDING,
    SENDING_TIMEOUT,
    SENT,
    SENT_TIMEOUT,
    RECEIVED,
}

enum class PushStatus {
    IDLE,
    SENDING,
}

/**
 * Unified state for the remote garage button.
 *
 * Combines tap-to-confirm interaction with network/door request tracking
 * into a single sum type. The button can only be in one state at a time.
 *
 * State flow (happy path):
 *   Ready → tap → Arming → Armed → tap → Sending → Sent → Received → Ready
 *
 * Failure paths:
 *   Armed → (5s timeout) → NotConfirmed → Ready
 *   Sending → (10s timeout) → SendingTimeout → Ready
 *   Sent → (10s timeout) → SentTimeout → Ready
 *
 * See ButtonStateMachine for transition logic.
 */
sealed interface RemoteButtonState {
    /** Default state. First tap arms the button. */
    data object Ready : RemoteButtonState

    /** Brief pause after first tap (anti-bounce). */
    data object Arming : RemoteButtonState

    /** Waiting for second tap to confirm. Times out to NotConfirmed. */
    data object Armed : RemoteButtonState

    /** User did not confirm in time. Brief display before returning to Ready. */
    data object NotConfirmed : RemoteButtonState

    /** Network request in flight. */
    data object Sending : RemoteButtonState

    /** Network call returned, waiting for door movement. */
    data object Sent : RemoteButtonState

    /** Door moved — request fulfilled. Brief display before returning to Ready. */
    data object Received : RemoteButtonState

    /** Network call did not return in time. */
    data object SendingTimeout : RemoteButtonState

    /** Door did not move after sending. */
    data object SentTimeout : RemoteButtonState
}
