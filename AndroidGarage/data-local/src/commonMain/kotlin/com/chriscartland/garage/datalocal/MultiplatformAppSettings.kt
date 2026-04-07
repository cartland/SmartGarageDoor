/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.datalocal

import com.chriscartland.garage.domain.repository.AppSettingsRepository
import com.chriscartland.garage.domain.repository.Setting
import com.russhwolf.settings.Settings

/**
 * KMP-compatible [AppSettingsRepository] backed by [com.russhwolf.settings.Settings].
 *
 * On Android this uses SharedPreferences, on iOS it uses NSUserDefaults.
 * The [Settings] instance is provided by platform-specific factory code.
 */
class MultiplatformAppSettings(
    private val settings: Settings,
) : AppSettingsRepository {
    override val fcmDoorTopic: Setting<String> =
        StringSetting(settings, Key.FCM_DOOR_TOPIC.name, "")
    override val profileAppCardExpanded: Setting<Boolean> =
        BooleanSetting(settings, Key.PROFILE_APP_CARD_EXPANDED.name, true)
    override val profileLogCardExpanded: Setting<Boolean> =
        BooleanSetting(settings, Key.PROFILE_LOG_CARD_EXPANDED.name, false)
    override val profileUserCardExpanded: Setting<Boolean> =
        BooleanSetting(settings, Key.PROFILE_USER_CARD_EXPANDED.name, true)
}

private enum class Key {
    FCM_DOOR_TOPIC,
    PROFILE_USER_CARD_EXPANDED,
    PROFILE_LOG_CARD_EXPANDED,
    PROFILE_APP_CARD_EXPANDED,
}

private class StringSetting(
    private val settings: Settings,
    override val key: String,
    private val default: String,
) : Setting<String> {
    override fun get(): String = settings.getString(key, default)

    override fun set(value: String) = settings.putString(key, value)

    override fun restoreDefault() = settings.remove(key)
}

private class BooleanSetting(
    private val settings: Settings,
    override val key: String,
    private val default: Boolean,
) : Setting<Boolean> {
    override fun get(): Boolean = settings.getBoolean(key, default)

    override fun set(value: Boolean) = settings.putBoolean(key, value)

    override fun restoreDefault() = settings.remove(key)
}
