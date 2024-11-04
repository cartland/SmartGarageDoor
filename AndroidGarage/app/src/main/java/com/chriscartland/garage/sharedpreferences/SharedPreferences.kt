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

package com.chriscartland.garage.sharedpreferences

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

interface SharedPreferences {
    fun getString(key: String, default: String? = null): String?
    fun setString(key: String, value: String)
    fun removeString(key: String)
    fun getInt(key: String, default: Int = 0): Int
    fun setInt(key: String, value: Int)
    fun incrementInt(key: String): Int
}

class SharedPreferencesImpl(private val context: Context) : SharedPreferences {

    val prefs = context.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? {
        return prefs.getString(key, default).also {
            Log.d(TAG, "getString: $key = $it")
        }
    }

    override fun setString(key: String, value: String) {
        Log.d(TAG, "setString: $key = $value")
        with(prefs.edit()) {
            putString(key, value)
            apply()
        }
    }

    override fun removeString(key: String) {
        Log.d(TAG, "removeString: $key")
        with(prefs.edit()) {
            remove(key)
            apply()
        }
    }

    override fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default).also {
            Log.d(TAG, "getInt: $key = $it")
        }
    }

    override fun setInt(key: String, value: Int) {
        Log.d(TAG, "setInt: $key = $value")
        with(prefs.edit()) {
            putInt(key, value)
            apply()
        }
    }

    override fun incrementInt(key: String): Int {
        Log.d(TAG, "incrementInt: $key")
        val value = getInt(key) + 1
        setInt(key, value)
        return value
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SharedPreferencesModule {
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext appContext: Context): SharedPreferences {
        return SharedPreferencesImpl(appContext)
    }
}

private const val SHARED_PREFERENCES = "shared_preferences"
private const val TAG = "SharedPreferences"
