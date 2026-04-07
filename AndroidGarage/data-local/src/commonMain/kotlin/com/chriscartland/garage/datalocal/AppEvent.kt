package com.chriscartland.garage.datalocal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AppEvent(
    val eventKey: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = currentTimeMillis(),
    val appVersion: String = "",
)

/**
 * KMP-compatible current time in milliseconds.
 * On JVM/Android this delegates to System.currentTimeMillis().
 */
expect fun currentTimeMillis(): Long
