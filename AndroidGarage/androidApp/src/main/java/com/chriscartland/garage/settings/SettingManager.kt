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

package com.chriscartland.garage.settings

import android.content.SharedPreferences
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.repository.Setting
import com.chriscartland.garage.settings.SettingType.BooleanSetting
import com.chriscartland.garage.settings.SettingType.IntSetting
import com.chriscartland.garage.settings.SettingType.LongSetting
import com.chriscartland.garage.settings.SettingType.StringSetting

interface SettingManager {
    fun getStringSetting(
        key: String,
        default: String,
    ): StringSetting

    fun getBooleanSetting(
        key: String,
        default: Boolean,
    ): BooleanSetting

    fun getIntSetting(
        key: String,
        default: Int,
    ): IntSetting

    fun getLongSetting(
        key: String,
        default: Long,
    ): LongSetting
}

sealed class SettingType<T> : Setting<T> {
    class StringSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: String,
    ) : SettingType<String>() {
        override fun get(): String =
            (prefs.getString(key, default) ?: default).also {
                Logger.d { "get: String $key = $it" }
            }

        override fun set(value: String) {
            Logger.d { "set: String $key = $value" }
            with(prefs.edit()) {
                putString(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Logger.d { "restoreDefault: String $key = $default" }
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }
    }

    class BooleanSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: Boolean,
    ) : SettingType<Boolean>() {
        override fun get(): Boolean =
            prefs.getBoolean(key, default).also {
                Logger.d { "get: Boolean $key = $it" }
            }

        override fun set(value: Boolean) {
            Logger.d { "set: Boolean $key = $value" }
            with(prefs.edit()) {
                putBoolean(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Logger.d { "restoreDefault: Boolean $key = $default" }
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }
    }

    class IntSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: Int,
    ) : SettingType<Int>() {
        override fun get(): Int =
            prefs.getInt(key, default).also {
                Logger.d { "get: Int $key = $it" }
            }

        override fun set(value: Int) {
            Logger.d { "set: Int $key = $value" }
            with(prefs.edit()) {
                putInt(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Logger.d { "restoreDefault: Int $key = $default" }
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Int {
            val newValue = get() + 1
            set(newValue)
            return newValue.also {
                Logger.d { "increment: Int $key = $it" }
            }
        }
    }

    class LongSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: Long,
    ) : SettingType<Long>() {
        override fun get(): Long =
            prefs.getLong(key, default).also {
                Logger.d { "get: Long $key = $it" }
            }

        override fun set(value: Long) {
            Logger.d { "set: Long $key = $value" }
            with(prefs.edit()) {
                putLong(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Logger.d { "restoreDefault: Long $key = $default" }
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Long {
            val newValue = get() + 1
            set(newValue)
            return newValue.also {
                Logger.d { "increment: Long $key = $it" }
            }
        }
    }
}
