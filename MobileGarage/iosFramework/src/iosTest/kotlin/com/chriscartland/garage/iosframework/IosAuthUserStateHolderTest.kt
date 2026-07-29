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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the cold-start auth timeline on iOS (DATA_CACHING_STRATEGY.md T4).
 *
 * The holder publishes to the shared `FirebaseAuthRepository` before Firebase's
 * listener has said anything, and that repository reads null as
 * `Unauthenticated` — a claim, not an absence. When the claim is wrong,
 * `SignOutCacheClearManager` deletes the button-health / snooze / allowlist
 * snapshots that ADR-034 exists to render instantly, so a signed-in user pays a
 * revalidation round-trip on every launch.
 */
class IosAuthUserStateHolderTest {
    private val restored = AuthUserInfo(displayName = "Ada Lovelace", email = "ada@example.com")

    @Test
    fun aRestoredSessionIsAlreadyVisibleBeforeTheListenerFires() =
        runTest {
            // No `update` call: this is exactly what a collector sees in the window
            // between constructing the bridge and Firebase delivering its first
            // callback.
            val holder = IosAuthUserStateHolder(initialUser = restored)

            assertEquals(
                restored,
                holder.asFlow().first(),
                "A restored session must be the holder's first emission. Seeding null " +
                    "here asserts 'signed out' and wipes the ADR-034 caches on cold start.",
            )
        }

    @Test
    fun noRestoredSessionStillReportsSignedOut() =
        runTest {
            val holder = IosAuthUserStateHolder(initialUser = null)

            assertNull(
                holder.asFlow().first(),
                "With no restored session the holder must still report signed out.",
            )
        }

    @Test
    fun theListenerStillOverridesTheSeed() =
        runTest {
            val holder = IosAuthUserStateHolder(initialUser = restored)

            holder.update(user = null)

            assertNull(
                holder.asFlow().first(),
                "A real sign-out delivered by the platform listener must win over the seed.",
            )
        }
}
