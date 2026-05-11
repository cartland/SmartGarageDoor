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

    /**
     * Developer-only: when `true`, the app paints the TopAppBar, the
     * NavigationBar / NavigationRail, and the Scaffold body with distinct
     * debug colors so layout boundaries (especially padding / inset
     * regions) become visible. UI gate: Settings → Developer → "Layout
     * debug colors" toggle. Persists across launches.
     *
     * Default `false` — production users never see debug colors even on
     * a developer-allowlisted build, until they explicitly toggle it.
     */
    val layoutDebugEnabled: Setting<Boolean>
}
