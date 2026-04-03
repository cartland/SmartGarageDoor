package com.chriscartland.garage.domain.model

data class DoorEvent(
    val doorPosition: DoorPosition? = null,
    val message: String? = null,
    val lastCheckInTimeSeconds: Long? = null,
    val lastChangeTimeSeconds: Long? = null,
)
