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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

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

@HiltViewModel
class AppSettingsViewModelImpl
@Inject
constructor(
    private val settings: AppSettings,
) : ViewModel(),
    AppSettingsViewModel {
    private val _fcmDoorTopic = MutableStateFlow(settings.fcmDoorTopic.get())
    override val fcmDoorTopic: StateFlow<String> = _fcmDoorTopic

    override fun setFcmDoorTopic(topic: String) {
        _fcmDoorTopic.value = topic
        settings.fcmDoorTopic.set(topic)
    }

    private val _profileUserCardExpanded = MutableStateFlow(settings.profileUserCardExpanded.get())
    override val profileUserCardExpanded: StateFlow<Boolean> = _profileUserCardExpanded

    override fun setProfileUserCardExpanded(expanded: Boolean) {
        _profileUserCardExpanded.value = expanded
        settings.profileUserCardExpanded.set(expanded)
    }

    private val _profileLogCardExpanded = MutableStateFlow(settings.profileLogCardExpanded.get())
    override val profileLogCardExpanded: StateFlow<Boolean> = _profileLogCardExpanded

    override fun setProfileLogCardExpanded(expanded: Boolean) {
        _profileLogCardExpanded.value = expanded
        settings.profileLogCardExpanded.set(expanded)
    }

    private val _profileAppCardExpanded = MutableStateFlow(settings.profileAppCardExpanded.get())
    override val profileAppCardExpanded: StateFlow<Boolean> = _profileAppCardExpanded

    override fun setProfileAppCardExpanded(expanded: Boolean) {
        _profileAppCardExpanded.value = expanded
        settings.profileAppCardExpanded.set(expanded)
    }
}
