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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface AppSettingsViewModel {
    val fcmDoorTopic: StateFlow<String>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileUserCardExpanded: StateFlow<Boolean?>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileLogCardExpanded: StateFlow<Boolean?>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileAppCardExpanded: StateFlow<Boolean?>

    fun setFcmDoorTopic(topic: String)

    fun setProfileUserCardExpanded(expanded: Boolean)

    fun setProfileLogCardExpanded(expanded: Boolean)

    fun setProfileAppCardExpanded(expanded: Boolean)
}

class DefaultAppSettingsViewModel(
    private val settings: AppSettingsUseCase,
) : ViewModel(),
    AppSettingsViewModel {
    override val fcmDoorTopic: StateFlow<String> = settings
        .observeFcmDoorTopic()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    override val profileUserCardExpanded: StateFlow<Boolean?> = settings
        .observeProfileUserCardExpanded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val profileLogCardExpanded: StateFlow<Boolean?> = settings
        .observeProfileLogCardExpanded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val profileAppCardExpanded: StateFlow<Boolean?> = settings
        .observeProfileAppCardExpanded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun setFcmDoorTopic(topic: String) {
        viewModelScope.launch { settings.setFcmDoorTopic(topic) }
    }

    override fun setProfileUserCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.setProfileUserCardExpanded(expanded) }
    }

    override fun setProfileLogCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.setProfileLogCardExpanded(expanded) }
    }

    override fun setProfileAppCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.setProfileAppCardExpanded(expanded) }
    }
}
