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

import android.content.Context
import com.chriscartland.garage.settings.SettingType.BooleanSetting
import com.chriscartland.garage.settings.SettingType.IntSetting
import com.chriscartland.garage.settings.SettingType.LongSetting
import com.chriscartland.garage.settings.SettingType.StringSetting
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

interface AppSettings : SettingContract, SettingManager

interface SettingContract {
    val fcmDoorTopic: StringSetting
    val profileAppCardExpanded: BooleanSetting
    val profileLogCardExpanded: BooleanSetting
    val profileUserCardExpanded: BooleanSetting
}

enum class Key {
    FCM_DOOR_TOPIC,
    PROFILE_USER_CARD_EXPANDED,
    PROFILE_LOG_CARD_EXPANDED,
    PROFILE_APP_CARD_EXPANDED,
}

class AppSettingsImpl(context: Context) : AppSettings {
    val prefs = context.getSharedPreferences(APP_SETTINGS, Context.MODE_PRIVATE)

    override val fcmDoorTopic = Key.FCM_DOOR_TOPIC.StringSetting("")
    override val profileAppCardExpanded = Key.PROFILE_APP_CARD_EXPANDED.BooleanSetting(true)
    override val profileLogCardExpanded = Key.PROFILE_LOG_CARD_EXPANDED.BooleanSetting(false)
    override val profileUserCardExpanded = Key.PROFILE_USER_CARD_EXPANDED.BooleanSetting(true)

    fun Key.StringSetting(default: String) = getStringSetting(this.name, default)
    fun Key.BooleanSetting(default: Boolean) = getBooleanSetting(this.name, default)
    fun Key.IntSetting(default: Int) = getIntSetting(this.name, default)
    fun Key.LongSetting(default: Long) = getLongSetting(this.name, default)

    override fun getStringSetting(key: String, default: String): StringSetting =
        StringSetting(prefs, key, default)
    override fun getBooleanSetting(key: String, default: Boolean): BooleanSetting =
        BooleanSetting(prefs, key, default)
    override fun getIntSetting(key: String, default: Int): IntSetting =
        IntSetting(prefs, key, default)
    override fun getLongSetting(key: String, default: Long): LongSetting =
        LongSetting(prefs, key, default)
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext appContext: Context): AppSettings {
        return AppSettingsImpl(appContext)
    }
}

private const val APP_SETTINGS = "app_settings"
private const val TAG = "AppSettings"
