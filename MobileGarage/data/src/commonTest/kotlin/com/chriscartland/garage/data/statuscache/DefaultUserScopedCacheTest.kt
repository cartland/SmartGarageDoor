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
 */

package com.chriscartland.garage.data.statuscache

import com.chriscartland.garage.testcommon.FakeStatusSnapshotStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The memory half of the sign-out clear (DATA_CACHING_STRATEGY P8):
 * registered resets run in the same transition as the disk clear, one
 * failing reset cannot skip the others, and the whole clear never
 * throws.
 */
class DefaultUserScopedCacheTest {
    private fun makeCache(store: FakeStatusSnapshotStore = FakeStatusSnapshotStore()) =
        DefaultUserScopedCache(
            statusSnapshotStore = store,
            userScopedKeys = emptySet(),
        )

    @Test
    fun clearRunsEveryRegisteredResetInRegistrationOrder() =
        runTest {
            val cache = makeCache()
            val ran = mutableListOf<String>()
            cache.registerInMemoryReset("a") { ran.add("a") }
            cache.registerInMemoryReset("b") { ran.add("b") }

            cache.clearUserScopedEntries()

            assertEquals(listOf("a", "b"), ran)
        }

    @Test
    fun aThrowingResetDoesNotSkipTheOthersAndDoesNotThrow() =
        runTest {
            // Never-throws contract: sign-out is a privacy boundary; a
            // failing reset must degrade to a log line, not break the
            // transition or skip the remaining resets.
            val cache = makeCache()
            val ran = mutableListOf<String>()
            cache.registerInMemoryReset("first") { ran.add("first") }
            cache.registerInMemoryReset("boom") { error("reset failed") }
            cache.registerInMemoryReset("last") { ran.add("last") }

            cache.clearUserScopedEntries() // must not throw

            assertEquals(listOf("first", "last"), ran)
        }

    @Test
    fun clearWithNoRegistrationsStillClearsDisk() =
        runTest {
            // A repository that was never constructed has nothing to
            // reset; the disk tier still clears.
            val store = FakeStatusSnapshotStore()
            val cache = makeCache(store)

            cache.clearUserScopedEntries()

            assertEquals(1, store.clearCalls.size)
        }
}
