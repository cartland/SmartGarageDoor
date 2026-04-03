package com.chriscartland.garage.domain.model

sealed class DoorFcmState {
    data object Unknown : DoorFcmState()

    data object NotRegistered : DoorFcmState()

    data class Registered(
        val topic: DoorFcmTopic,
    ) : DoorFcmState()
}

@JvmInline
value class DoorFcmTopic(
    val string: String,
)

enum class FcmRegistrationStatus {
    UNKNOWN,
    REGISTERED,
    NOT_REGISTERED,
}
