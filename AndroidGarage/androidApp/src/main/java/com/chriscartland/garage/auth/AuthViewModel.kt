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

package com.chriscartland.garage.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

interface AuthViewModel {
    val authState: StateFlow<AuthState>

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun signOut()
}

@Inject
class DefaultAuthViewModel(
    private val authRepository: AuthRepository,
    private val appLoggerRepository: AppLoggerRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    AuthViewModel {
    override val authState: StateFlow<AuthState> = authRepository.authState

    override fun signInWithGoogle(idToken: GoogleIdToken) {
        viewModelScope.launch(dispatchers.io) {
            appLoggerRepository.log(AppLoggerKeys.BEGIN_GOOGLE_SIGN_IN)
            Logger.d { "signInWithGoogle" }
            authRepository.signInWithGoogle(idToken)
        }
    }

    override fun signOut() {
        viewModelScope.launch(dispatchers.io) {
            authRepository.signOut()
        }
    }
}
