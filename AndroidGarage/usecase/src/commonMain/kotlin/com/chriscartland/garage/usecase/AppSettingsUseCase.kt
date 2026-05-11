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

package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.NavigationRailItemPosition
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Façade over [AppSettingsRepository] so ViewModels never depend on the
 * repository interface directly. Per-setting observe/update methods.
 */
class AppSettingsUseCase(
    private val settings: AppSettingsRepository,
) {
    fun observeFcmDoorTopic(): Flow<String> = settings.fcmDoorTopic.flow

    suspend fun setFcmDoorTopic(value: String) = settings.fcmDoorTopic.set(value)

    fun observeProfileUserCardExpanded(): Flow<Boolean> = settings.profileUserCardExpanded.flow

    suspend fun setProfileUserCardExpanded(value: Boolean) = settings.profileUserCardExpanded.set(value)

    fun observeProfileLogCardExpanded(): Flow<Boolean> = settings.profileLogCardExpanded.flow

    suspend fun setProfileLogCardExpanded(value: Boolean) = settings.profileLogCardExpanded.set(value)

    fun observeProfileAppCardExpanded(): Flow<Boolean> = settings.profileAppCardExpanded.flow

    suspend fun setProfileAppCardExpanded(value: Boolean) = settings.profileAppCardExpanded.set(value)

    fun observeLayoutDebugEnabled(): Flow<Boolean> = settings.layoutDebugEnabled.flow

    suspend fun setLayoutDebugEnabled(value: Boolean) = settings.layoutDebugEnabled.set(value)

    fun observeNavigationRailItemPosition(): Flow<NavigationRailItemPosition> = settings.navigationRailItemPosition.flow

    suspend fun setNavigationRailItemPosition(value: NavigationRailItemPosition) = settings.navigationRailItemPosition.set(value)

    suspend fun restoreNavigationRailItemPositionDefault() = settings.navigationRailItemPosition.restoreDefault()

    fun observeNavigationRailTopPaddingDp(): Flow<Int> = settings.navigationRailTopPaddingDp.flow

    suspend fun setNavigationRailTopPaddingDp(value: Int) = settings.navigationRailTopPaddingDp.set(value)

    suspend fun restoreNavigationRailTopPaddingDpDefault() = settings.navigationRailTopPaddingDp.restoreDefault()
}
