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
import com.chriscartland.garage.settings.SettingType.BooleanSetting
import com.chriscartland.garage.settings.SettingType.IntSetting
import com.chriscartland.garage.settings.SettingType.LongSetting
import com.chriscartland.garage.settings.SettingType.StringSetting

interface Setting<T> {
    val key: String
    fun get(): T
    fun set(value: T)
    fun restoreDefault()
}

interface SettingManager {
    fun getStringSetting(key: String, default: String): StringSetting
    fun getBooleanSetting(key: String, default: Boolean): BooleanSetting
    fun getIntSetting(key: String, default: Int): IntSetting
    fun getLongSetting(key: String, default: Long): LongSetting
}

sealed class SettingType<T> : Setting<T> {
    abstract override val key: String
    abstract override fun get(): T
    abstract override fun set(value: T)

    class StringSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: String,
    ) : SettingType<String>() {
        override fun get(): String {
            return prefs.getString(key, default) ?: default
        }

        override fun set(value: String) {
            with(prefs.edit()) {
                putString(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
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
        override fun get(): Boolean {
            return prefs.getBoolean(key, default)
        }

        override fun set(value: Boolean) {
            with(prefs.edit()) {
                putBoolean(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
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
        override fun get(): Int {
            return prefs.getInt(key, default)
        }

        override fun set(value: Int) {
            with(prefs.edit()) {
                putInt(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Int {
            val newValue = get() + 1
            set(newValue)
            return newValue
        }
    }

    class LongSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: Long,
    ) : SettingType<Long>() {
        override fun get(): Long {
            return prefs.getLong(key, default)
        }

        override fun set(value: Long) {
            with(prefs.edit()) {
                putLong(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Long {
            val newValue = get() + 1
            set(newValue)
            return newValue
        }
    }
}
