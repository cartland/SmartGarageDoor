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
