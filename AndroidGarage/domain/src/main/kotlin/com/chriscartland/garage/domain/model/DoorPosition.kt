package com.chriscartland.garage.domain.model

// DO NOT CHANGE NAMES — must match server strings
enum class DoorPosition {
    UNKNOWN,
    CLOSED,
    OPENING,
    OPENING_TOO_LONG,
    OPEN,
    OPEN_MISALIGNED,
    CLOSING,
    CLOSING_TOO_LONG,
    ERROR_SENSOR_CONFLICT,
}
