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

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SignInWithGoogleUseCaseTest {
    @Test
    fun invokeDelegatesToRepository() =
        runTest {
            val repo = FakeAuthRepository()
            val useCase = SignInWithGoogleUseCase(repo)

            useCase(GoogleIdToken("test-token"))

            assertEquals(1, repo.signInCount)
        }

    @Test
    fun invokeUpdatesAuthStateOnSuccess() =
        runTest {
            val repo = FakeAuthRepository()
            repo.signInResult = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Alice"),
                    email = Email("alice@test.com"),
                    idToken = FirebaseIdToken("token", exp = Long.MAX_VALUE),
                ),
            )
            val useCase = SignInWithGoogleUseCase(repo)

            useCase(GoogleIdToken("token"))

            assertIs<AuthState.Authenticated>(repo.getAuthState())
        }
}
