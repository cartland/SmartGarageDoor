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

package com.chriscartland.garage.iosframework

import com.chriscartland.garage.data.AuthUserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Backing auth-user state for the Swift `FirebaseAuthBridge`.
 *
 * `AuthBridge.observeAuthUser(): Flow<AuthUserInfo?>` is implemented in Swift,
 * but Swift/Kotlin-Native interop offers no way to *construct* a
 * `kotlinx.coroutines` flow from Swift. SKIE transforms the protocol so the
 * Swift conformance must return `SkieSwiftOptionalFlow<DataAuthUserInfo>` — and
 * SKIE produces exactly that Swift type when it bridges a `Flow<AuthUserInfo?>`
 * return value. So the holder exposes [asFlow] typed `Flow<AuthUserInfo?>`; the
 * Swift bridge returns `holder.asFlow()` straight from `observeAuthUser()` and
 * calls [update] from the Firebase `addStateDidChangeListener` callback to push
 * new values.
 *
 * One shared `StateFlow` + a single listener registered in the bridge's init
 * replaces Android's per-collector `callbackFlow`; all collectors observe the
 * latest value, which is the intended semantics.
 */
class IosAuthUserStateHolder {
    private val state = MutableStateFlow<AuthUserInfo?>(null)

    /**
     * The backing flow as `Flow<AuthUserInfo?>`. SKIE bridges this return type
     * to `SkieSwiftOptionalFlow<DataAuthUserInfo>`, which is exactly what the
     * transformed `observeAuthUser()` protocol requirement returns — so the
     * Swift bridge returns it with no cast. The runtime object is the backing
     * `MutableStateFlow`, which the Kotlin consumer collects normally.
     */
    fun asFlow(): Flow<AuthUserInfo?> = state

    /** Push the latest authenticated user (or null when signed out). */
    fun update(user: AuthUserInfo?) {
        state.value = user
    }
}
