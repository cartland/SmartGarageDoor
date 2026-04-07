package com.chriscartland.garage.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic reactive key-value setting.
 *
 * Backed by DataStore on all platforms. Changes propagate reactively via [flow].
 */
interface Setting<T> {
    val key: String

    /** Observe this setting's value reactively. Emits the current value immediately. */
    val flow: Flow<T>

    /** Update this setting's value. */
    suspend fun set(value: T)

    /** Reset to the default value. */
    suspend fun restoreDefault()
}

/**
 * Platform-agnostic settings contract.
 *
 * Defines the settings the app needs without referencing SharedPreferences,
 * DataStore, or any other platform storage directly.
 */
interface AppSettingsRepository {
    val fcmDoorTopic: Setting<String>
    val profileAppCardExpanded: Setting<Boolean>
    val profileLogCardExpanded: Setting<Boolean>
    val profileUserCardExpanded: Setting<Boolean>
}
