package com.chriscartland.garage.datalocal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

@Entity(indices = [Index(value = ["eventKey"])])
data class AppEvent(
    val eventKey: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` is the
    // KMP equivalent of `System.currentTimeMillis()` — same wall-clock
    // semantics on Android (delegates to System) and iOS (NSDate).
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val appVersion: String = "",
)
