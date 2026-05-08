/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.model.ButtonHealthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.LoadingResult
import com.chriscartland.garage.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ButtonHealthDisplayLogicTest {
    private val now = 1_700_000_000L

    private val signedIn = AuthState.Authenticated(
        user = User(
            name = DisplayName("Test User"),
            email = Email("test@example.com"),
        ),
    )

    @Test
    fun signedOut_isUnauthorized() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = AuthState.Unauthenticated,
            health = LoadingResult.Complete(ButtonHealth(ButtonHealthState.OFFLINE, now - 100)),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Unauthorized, display)
    }

    @Test
    fun authUnknown_isUnauthorized() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = AuthState.Unknown,
            health = LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, now - 100)),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Unauthorized, display)
    }

    @Test
    fun signedIn_loading_isLoading() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Loading(null),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Loading, display)
    }

    @Test
    fun signedIn_error_isLoading() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Error(IllegalStateException("network")),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Loading, display)
    }

    @Test
    fun signedIn_completeNullData_isLoading() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Complete(null),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Loading, display)
    }

    @Test
    fun signedIn_completeUnknown_isUnknown() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Complete(ButtonHealth(ButtonHealthState.UNKNOWN, null)),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Unknown, display)
    }

    @Test
    fun signedIn_completeOnline_isOnline() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Complete(ButtonHealth(ButtonHealthState.ONLINE, now - 30)),
            nowSeconds = now,
        )
        assertEquals(ButtonHealthDisplay.Online, display)
    }

    @Test
    fun signedIn_completeOffline_isOfflineWithDurationLabel() {
        val display = ButtonHealthDisplayLogic.compute(
            auth = signedIn,
            health = LoadingResult.Complete(ButtonHealth(ButtonHealthState.OFFLINE, now - 11 * 60)),
            nowSeconds = now,
        )
        val offline = assertIs<ButtonHealthDisplay.Offline>(display)
        assertEquals("11 min ago", offline.durationLabel)
    }
}
