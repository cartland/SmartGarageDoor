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
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface AppSettingsViewModel {
    val fcmDoorTopic: StateFlow<String>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileUserCardExpanded: StateFlow<Boolean?>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileLogCardExpanded: StateFlow<Boolean?>

    /** Null until DataStore loads — callers should skip rendering until non-null. */
    val profileAppCardExpanded: StateFlow<Boolean?>

    /**
     * Per-user access decision for the Function List feature, used to gate the
     * Settings-screen entry button.
     *
     * - `null` — not yet fetched, fetch failed, or signed out (gate closed).
     * - `false` — server says this user is NOT on the allowlist (gate closed).
     * - `true` — server says this user IS on the allowlist (gate open).
     *
     * **Tri-state is load-bearing.** Both `null` and `false` deny. Gate UI on
     * `== true` only; never on `!= false`. Full convention in
     * `docs/FEATURE_FLAGS.md`. The Function List screen has its own
     * independent gate via `FunctionListViewModel.accessGranted`; this flag
     * is a UI hint to hide the entry point and is not a security boundary.
     */
    val functionListAccess: StateFlow<Boolean?>

    fun setFcmDoorTopic(topic: String)

    fun setProfileUserCardExpanded(expanded: Boolean)

    fun setProfileLogCardExpanded(expanded: Boolean)

    fun setProfileAppCardExpanded(expanded: Boolean)
}

class DefaultAppSettingsViewModel(
    private val settings: AppSettingsUseCase,
    private val observeFeatureAccessUseCase: ObserveFeatureAccessUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    AppSettingsViewModel {
    // ADR-017 Rule 6: explicit MutableStateFlow + collect, no stateIn in ViewModels.
    private val _fcmDoorTopic = MutableStateFlow("")
    override val fcmDoorTopic: StateFlow<String> = _fcmDoorTopic

    private val _profileUserCardExpanded = MutableStateFlow<Boolean?>(null)
    override val profileUserCardExpanded: StateFlow<Boolean?> = _profileUserCardExpanded

    private val _profileLogCardExpanded = MutableStateFlow<Boolean?>(null)
    override val profileLogCardExpanded: StateFlow<Boolean?> = _profileLogCardExpanded

    private val _profileAppCardExpanded = MutableStateFlow<Boolean?>(null)
    override val profileAppCardExpanded: StateFlow<Boolean?> = _profileAppCardExpanded

    private val _functionListAccess = MutableStateFlow<Boolean?>(null)
    override val functionListAccess: StateFlow<Boolean?> = _functionListAccess

    init {
        viewModelScope.launch {
            settings.observeFcmDoorTopic().collect { _fcmDoorTopic.value = it }
        }
        viewModelScope.launch {
            settings.observeProfileUserCardExpanded().collect { _profileUserCardExpanded.value = it }
        }
        viewModelScope.launch {
            settings.observeProfileLogCardExpanded().collect { _profileLogCardExpanded.value = it }
        }
        viewModelScope.launch {
            settings.observeProfileAppCardExpanded().collect { _profileAppCardExpanded.value = it }
        }
        viewModelScope.launch(dispatchers.io) {
            observeFeatureAccessUseCase.functionList().collect { _functionListAccess.value = it }
        }
    }

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
