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

package com.chriscartland.garage.data.statuscache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class TestPayload(
    val label: String,
    val count: Int = 0,
)

/** Requires a field [TestPayload] never writes — decoding fails. */
@Serializable
private data class IncompatiblePayload(
    val requiredField: String,
)

/**
 * In-memory [StatusCacheStorage] with per-call failure injection.
 * Throws (like a real IO failure) so the tests exercise the typed
 * store's never-throw catch-and-degrade policy.
 */
private class InMemoryStatusCacheStorage : StatusCacheStorage {
    val map = mutableMapOf<String, String>()
    var failNextGet = false
    var failNextPut = false
    var failNextRemove = false
    var cancelNextCall = false

    override suspend fun get(key: String): String? {
        throwIfInjected()
        if (failNextGet) {
            failNextGet = false
            throw RuntimeException("injected get failure")
        }
        return map[key]
    }

    override suspend fun put(
        key: String,
        value: String,
    ) {
        throwIfInjected()
        if (failNextPut) {
            failNextPut = false
            throw RuntimeException("injected put failure")
        }
        map[key] = value
    }

    override suspend fun remove(keys: Set<String>) {
        throwIfInjected()
        if (failNextRemove) {
            failNextRemove = false
            throw RuntimeException("injected remove failure")
        }
        keys.forEach { map.remove(it) }
    }

    private fun throwIfInjected() {
        if (cancelNextCall) {
            cancelNextCall = false
            throw CancellationException("injected cancellation")
        }
    }
}

class DefaultStatusSnapshotStoreTest {
    private val key = StatusCacheKey("testStatus")
    private val snapshot = StatusSnapshot(
        payload = TestPayload(label = "online", count = 3),
        fetchedAtEpochSeconds = 1000L,
        confirmedAtEpochSeconds = 2000L,
        accountEmail = "test@example.com",
    )

    @Test
    fun writeThenReadRoundTripsPayloadAndMetadata() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)

            store.write(key, 1, TestPayload.serializer(), snapshot)
            val result = store.read(key, 1, TestPayload.serializer())

            assertEquals(snapshot, result)
        }

    @Test
    fun readMissingEntryReturnsNull() =
        runTest {
            val store = DefaultStatusSnapshotStore(InMemoryStatusCacheStorage())

            assertNull(store.read(key, 1, TestPayload.serializer()))
        }

    @Test
    fun readCorruptEnvelopeReturnsNullAndDeletesEntry() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.map[key.storageKey] = "this is not json"

            assertNull(store.read(key, 1, TestPayload.serializer()))
            // Self-healing: the undecodable entry is gone, so it can't
            // fail again on the next launch.
            assertFalse(storage.map.containsKey(key.storageKey))
        }

    @Test
    fun readSchemaVersionMismatchReturnsNullAndDeletesEntry() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            store.write(key, 1, TestPayload.serializer(), snapshot)

            assertNull(store.read(key, 2, TestPayload.serializer()))
            assertFalse(storage.map.containsKey(key.storageKey))
        }

    @Test
    fun readIncompatiblePayloadReturnsNullAndDeletesEntry() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            store.write(key, 1, TestPayload.serializer(), snapshot)

            // Same schema version but a payload type whose required
            // field is absent — the model-drift case the plan requires
            // to degrade to cache-absent instead of crash-looping.
            assertNull(store.read(key, 1, IncompatiblePayload.serializer()))
            assertFalse(storage.map.containsKey(key.storageKey))
        }

    @Test
    fun readToleratesUnknownEnvelopeAndPayloadKeys() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            // A future app version added fields at both levels.
            storage.map[key.storageKey] =
                """
                {"schemaVersion":1,"fetchedAtEpochSeconds":1000,"confirmedAtEpochSeconds":2000,
                 "accountEmail":"test@example.com","futureEnvelopeField":true,
                 "payload":{"label":"online","count":3,"futurePayloadField":"x"}}
                """.trimIndent()

            assertEquals(snapshot, store.read(key, 1, TestPayload.serializer()))
        }

    @Test
    fun readStorageFailureReturnsNullWithoutDeleting() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            store.write(key, 1, TestPayload.serializer(), snapshot)
            storage.failNextGet = true

            // Transient IO failure: degrade to absent but do NOT
            // delete — the entry itself may be fine.
            assertNull(store.read(key, 1, TestPayload.serializer()))
            assertTrue(storage.map.containsKey(key.storageKey))

            // Next read (storage recovered) sees the entry again.
            assertEquals(snapshot, store.read(key, 1, TestPayload.serializer()))
        }

    @Test
    fun writeStorageFailureDoesNotThrow() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.failNextPut = true

            store.write(key, 1, TestPayload.serializer(), snapshot)

            assertNull(store.read(key, 1, TestPayload.serializer()))
        }

    @Test
    fun clearStorageFailureDoesNotThrow() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            store.write(key, 1, TestPayload.serializer(), snapshot)
            storage.failNextRemove = true

            store.clear(setOf(key))

            // Clear was dropped; entry still present.
            assertEquals(snapshot, store.read(key, 1, TestPayload.serializer()))
        }

    @Test
    fun clearRemovesOnlyGivenKeys() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            val otherKey = StatusCacheKey("otherStatus")
            store.write(key, 1, TestPayload.serializer(), snapshot)
            store.write(otherKey, 1, TestPayload.serializer(), snapshot)

            store.clear(setOf(key))

            assertNull(store.read(key, 1, TestPayload.serializer()))
            assertEquals(snapshot, store.read(otherKey, 1, TestPayload.serializer()))
        }

    @Test
    fun clearEmptySetIsNoOp() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.failNextRemove = true

            store.clear(emptySet())

            // The injected failure was never consumed — no storage call.
            assertTrue(storage.failNextRemove)
        }

    @Test
    fun readPropagatesCancellation() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.cancelNextCall = true

            // Cancellation must unwind the caller, never degrade to
            // cache-absent (a cancelled hydration would otherwise keep
            // running and publish a spurious null).
            assertFailsWith<CancellationException> {
                store.read(key, 1, TestPayload.serializer())
            }
        }

    @Test
    fun writePropagatesCancellation() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.cancelNextCall = true

            assertFailsWith<CancellationException> {
                store.write(key, 1, TestPayload.serializer(), snapshot)
            }
        }

    @Test
    fun clearPropagatesCancellation() =
        runTest {
            val storage = InMemoryStatusCacheStorage()
            val store = DefaultStatusSnapshotStore(storage)
            storage.cancelNextCall = true

            assertFailsWith<CancellationException> {
                store.clear(setOf(key))
            }
        }

    @Test
    fun writeWithoutAccountEmailRoundTripsNull() =
        runTest {
            val store = DefaultStatusSnapshotStore(InMemoryStatusCacheStorage())
            val anonymous = snapshot.copy(accountEmail = null)

            store.write(key, 1, TestPayload.serializer(), anonymous)

            assertEquals(anonymous, store.read(key, 1, TestPayload.serializer()))
        }
}
