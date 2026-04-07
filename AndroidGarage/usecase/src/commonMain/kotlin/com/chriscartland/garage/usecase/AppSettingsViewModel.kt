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
import com.chriscartland.garage.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface AppSettingsViewModel {
    val fcmDoorTopic: StateFlow<String>
    val profileUserCardExpanded: StateFlow<Boolean>
    val profileLogCardExpanded: StateFlow<Boolean>
    val profileAppCardExpanded: StateFlow<Boolean>

    fun setFcmDoorTopic(topic: String)

    fun setProfileUserCardExpanded(expanded: Boolean)

    fun setProfileLogCardExpanded(expanded: Boolean)

    fun setProfileAppCardExpanded(expanded: Boolean)
}

class DefaultAppSettingsViewModel(
    private val settings: AppSettingsRepository,
) : ViewModel(),
    AppSettingsViewModel {
    override val fcmDoorTopic: StateFlow<String> = settings.fcmDoorTopic.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    override val profileUserCardExpanded: StateFlow<Boolean> = settings.profileUserCardExpanded.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    override val profileLogCardExpanded: StateFlow<Boolean> = settings.profileLogCardExpanded.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override val profileAppCardExpanded: StateFlow<Boolean> = settings.profileAppCardExpanded.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    override fun setFcmDoorTopic(topic: String) {
        viewModelScope.launch { settings.fcmDoorTopic.set(topic) }
    }

    override fun setProfileUserCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.profileUserCardExpanded.set(expanded) }
    }

    override fun setProfileLogCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.profileLogCardExpanded.set(expanded) }
    }

    override fun setProfileAppCardExpanded(expanded: Boolean) {
        viewModelScope.launch { settings.profileAppCardExpanded.set(expanded) }
    }
}
