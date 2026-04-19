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
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.GoogleIdToken
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface AuthViewModel {
    val authState: StateFlow<AuthState>

    fun signInWithGoogle(idToken: GoogleIdToken)

    fun signOut()
}

class DefaultAuthViewModel(
    observeAuthState: ObserveAuthStateUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val logAppEvent: LogAppEventUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel(),
    AuthViewModel {
    // ADR-022: expose the repository's StateFlow by reference — no mirror.
    override val authState: StateFlow<AuthState> = observeAuthState()

    override fun signInWithGoogle(idToken: GoogleIdToken) {
        viewModelScope.launch(dispatchers.io) {
            logAppEvent(AppLoggerKeys.BEGIN_GOOGLE_SIGN_IN)
            Logger.d { "signInWithGoogle" }
            signInWithGoogleUseCase(idToken)
        }
    }

    override fun signOut() {
        viewModelScope.launch(dispatchers.io) {
            signOutUseCase()
        }
    }
}
