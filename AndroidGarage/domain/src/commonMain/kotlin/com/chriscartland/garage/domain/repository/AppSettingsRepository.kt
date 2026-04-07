package com.chriscartland.garage.domain.repository

/**
 * Platform-agnostic key-value setting.
 */
interface Setting<T> {
    val key: String

    fun get(): T

    fun set(value: T)

    fun restoreDefault()
}

/**
 * Platform-agnostic settings contract.
 *
 * Defines the settings the app needs without referencing SharedPreferences
 * or any other platform storage.
 */
interface AppSettingsRepository {
    val fcmDoorTopic: Setting<String>
    val profileAppCardExpanded: Setting<Boolean>
    val profileLogCardExpanded: Setting<Boolean>
    val profileUserCardExpanded: Setting<Boolean>
}
