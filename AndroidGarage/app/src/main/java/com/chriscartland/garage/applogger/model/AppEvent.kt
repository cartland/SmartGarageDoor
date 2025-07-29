package com.chriscartland.garage.applogger.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AppEvent(
    val eventKey: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(), // Milliseconds
    val appVersion: String = "",
)
