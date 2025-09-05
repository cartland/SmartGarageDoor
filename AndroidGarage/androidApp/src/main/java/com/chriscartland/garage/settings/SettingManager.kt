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
import android.util.Log
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

interface Setting<T> {
    val key: String

    fun get(): T

    fun set(value: T)

    fun restoreDefault()
}

sealed class SettingType<T> : Setting<T> {
    class StringSetting(
        private val prefs: SharedPreferences,
        override val key: String,
        private val default: String,
    ) : SettingType<String>() {
        override fun get(): String =
            (prefs.getString(key, default) ?: default).also {
                Log.d(TAG, "get: String $key = $it")
            }

        override fun set(value: String) {
            Log.d(TAG, "set: String $key = $value")
            with(prefs.edit()) {
                putString(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Log.d(TAG, "restoreDefault: String $key = $default")
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
                Log.d(TAG, "get: Boolean $key = $it")
            }

        override fun set(value: Boolean) {
            Log.d(TAG, "set: Boolean $key = $value")
            with(prefs.edit()) {
                putBoolean(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Log.d(TAG, "restoreDefault: Boolean $key = $default")
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
                Log.d(TAG, "get: Int $key = $it")
            }

        override fun set(value: Int) {
            Log.d(TAG, "set: Int $key = $value")
            with(prefs.edit()) {
                putInt(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Log.d(TAG, "restoreDefault: Int $key = $default")
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Int {
            val newValue = get() + 1
            set(newValue)
            return newValue.also {
                Log.d(TAG, "increment: Int $key = $it")
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
                Log.d(TAG, "get: Long $key = $it")
            }

        override fun set(value: Long) {
            Log.d(TAG, "set: Long $key = $value")
            with(prefs.edit()) {
                putLong(key, value)
                apply()
            }
        }

        override fun restoreDefault() {
            Log.d(TAG, "restoreDefault: Long $key = $default")
            with(prefs.edit()) {
                remove(key)
                apply()
            }
        }

        fun increment(): Long {
            val newValue = get() + 1
            set(newValue)
            return newValue.also {
                Log.d(TAG, "increment: Long $key = $it")
            }
        }
    }
}

private const val TAG = "SettingManager"
