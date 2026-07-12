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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * [StatusSnapshotStore] over a raw [StatusCacheStorage], owning the
 * envelope format and the never-throw / self-healing policy
 * (see `MobileGarage/docs/STATUS_CACHE_PLAN.md` §D1).
 *
 * Envelope format (one JSON string per entry): the payload rides as a
 * nested [JsonElement] so an envelope that parses but carries an
 * incompatible payload still fails cleanly at the payload-decode step
 * and is deleted. `ignoreUnknownKeys` keeps forward-compat when a
 * newer app version adds envelope or DTO fields; removing or renaming
 * a field instead requires bumping the caller's schema version.
 */
class DefaultStatusSnapshotStore(
    private val storage: StatusCacheStorage,
) : StatusSnapshotStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun <T : Any> read(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
    ): StatusSnapshot<T>? {
        val raw = runCatching { storage.get(key.storageKey) }
            .getOrElse { t ->
                // Cancellation is a control signal, not an IO failure — it
                // must unwind the caller, or a cancelled hydration keeps
                // running and publishes a spurious cache-absent state.
                if (t is CancellationException) throw t
                Logger.w(t) { "StatusSnapshotStore: read failed for ${key.storageKey}; treating as absent" }
                return null
            } ?: return null

        val decoded = runCatching {
            val envelope = json.decodeFromString(StatusSnapshotEnvelope.serializer(), raw)
            if (envelope.schemaVersion != schemaVersion) {
                Logger.i {
                    "StatusSnapshotStore: ${key.storageKey} has schemaVersion ${envelope.schemaVersion}, " +
                        "expected $schemaVersion; invalidating"
                }
                null
            } else {
                StatusSnapshot(
                    payload = json.decodeFromJsonElement(payloadSerializer, envelope.payload),
                    fetchedAtEpochSeconds = envelope.fetchedAtEpochSeconds,
                    confirmedAtEpochSeconds = envelope.confirmedAtEpochSeconds,
                    accountEmail = envelope.accountEmail,
                )
            }
        }.getOrElse { t ->
            Logger.w(t) { "StatusSnapshotStore: ${key.storageKey} failed to decode; invalidating" }
            null
        }

        if (decoded == null) {
            // Self-heal: a snapshot we can never decode would otherwise sit
            // on disk failing on every launch.
            clear(setOf(key))
        }
        return decoded
    }

    override suspend fun <T : Any> write(
        key: StatusCacheKey,
        schemaVersion: Int,
        payloadSerializer: KSerializer<T>,
        snapshot: StatusSnapshot<T>,
    ) {
        runCatching {
            val envelope = StatusSnapshotEnvelope(
                schemaVersion = schemaVersion,
                fetchedAtEpochSeconds = snapshot.fetchedAtEpochSeconds,
                confirmedAtEpochSeconds = snapshot.confirmedAtEpochSeconds,
                accountEmail = snapshot.accountEmail,
                payload = json.encodeToJsonElement(payloadSerializer, snapshot.payload),
            )
            storage.put(key.storageKey, json.encodeToString(StatusSnapshotEnvelope.serializer(), envelope))
        }.onFailure { t ->
            if (t is CancellationException) throw t
            Logger.w(t) { "StatusSnapshotStore: write failed for ${key.storageKey}; value not persisted" }
        }
    }

    override suspend fun clear(keys: Set<StatusCacheKey>) {
        if (keys.isEmpty()) return
        runCatching {
            storage.remove(keys.map { it.storageKey }.toSet())
        }.onFailure { t ->
            if (t is CancellationException) throw t
            Logger.w(t) { "StatusSnapshotStore: clear failed for ${keys.map { it.storageKey }}" }
        }
    }
}

/**
 * On-disk JSON shape of one cache entry. Internal to `:data` — repos
 * see only [StatusSnapshot]. Field removals/renames here require a
 * coordinated schema-version bump in every consumer.
 */
@Serializable
internal data class StatusSnapshotEnvelope(
    val schemaVersion: Int,
    val fetchedAtEpochSeconds: Long,
    val confirmedAtEpochSeconds: Long,
    val accountEmail: String? = null,
    val payload: JsonElement,
)
