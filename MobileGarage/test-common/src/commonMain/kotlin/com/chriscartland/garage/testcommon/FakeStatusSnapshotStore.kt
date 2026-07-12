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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.data.statuscache.StatusCacheKey
import com.chriscartland.garage.data.statuscache.StatusSnapshot
import com.chriscartland.garage.data.statuscache.StatusSnapshotStore
import kotlinx.serialization.KSerializer

/**
 * In-memory [StatusSnapshotStore] for tests. Mirrors the production
 * never-throw contract: failure injection makes reads/writes DEGRADE
 * ([failNextRead] → null, [failNextWrite] → dropped write), never
 * throw — matching what `DefaultStatusSnapshotStore` does on storage
 * or decode failure.
 *
 * Entries are held as live [StatusSnapshot] objects (no JSON round
 * trip); [seed] pre-populates a snapshot as if a previous process had
 * persisted it. Schema-version mismatch behaves like production: the
 * read returns null and the entry is deleted.
 */
class FakeStatusSnapshotStore : StatusSnapshotStore {
    private data class Entry(
        val schemaVersion: Int,
        val snapshot: StatusSnapshot<*>,
    )

    private val entries = mutableMapOf<StatusCacheKey, Entry>()

    private var readFailurePending = false
    private var writeFailurePending = false

    private val _clearCalls = mutableListOf<Set<StatusCacheKey>>()

    /** Every [clear] invocation, in order, with the keys it was given. */
    val clearCalls: List<Set<StatusCacheKey>> get() = _clearCalls

    private var _writeCount = 0
    val writeCount: Int get() = _writeCount

    /** Pre-populates [key] as if persisted by a previous process. */
    fun <T : Any> seed(
        key: StatusCacheKey,
        schemaVersion: Int,
        snapshot: StatusSnapshot<T>,
    ) {
        entries[key] = Entry(schemaVersion, snapshot)
    }

    /** The next [read] returns null (simulates storage/decode failure). */
    fun failNextRead() {
        readFailurePending = true
    }

    /** The next [write] is dropped (simulates storage failure). */
    fun failNextWrite() {
        writeFailurePending = true
    }

    fun contains(key: StatusCacheKey): Boolean = entries.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> read(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
    ): StatusSnapshot<T>? {
        if (readFailurePending) {
            readFailurePending = false
            return null
        }
        val entry = entries[key] ?: return null
        if (entry.schemaVersion != schemaVersion) {
            entries.remove(key)
            return null
        }
        return entry.snapshot as StatusSnapshot<T>
    }

    override suspend fun <T : Any> write(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
        snapshot: StatusSnapshot<T>,
    ) {
        if (writeFailurePending) {
            writeFailurePending = false
            return
        }
        _writeCount++
        entries[key] = Entry(schemaVersion, snapshot)
    }

    override suspend fun clear(keys: Set<StatusCacheKey>) {
        _clearCalls.add(keys)
        keys.forEach { entries.remove(it) }
    }
}
