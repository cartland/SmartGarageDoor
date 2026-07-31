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

package com.chriscartland.garage.data.wearrelay

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What the watch app publishes about itself, so the phone's Settings can name
 * the build that is actually on the wrist.
 *
 * The phone can already tell that the app is installed — a node advertising
 * `WearCompanionRepository.WATCH_APP_CAPABILITY` is proof of that — but a
 * capability is a boolean and carries nothing else. Nothing in the Wearable
 * API reports another node's app version, so the watch has to say.
 */
@Serializable
data class WearAppInfo(
    /** The watch app's `versionName`, e.g. `0.5.1`. */
    val versionName: String,
    /** Its `versionCode` (1000000 + the `wear/N` tag), when known. */
    val versionCode: Long? = null,
)

/**
 * Wire format for [WearAppInfo] over the Wearable Data Layer.
 *
 * ## Why a DataItem and not a message
 *
 * The auth relay is a `MessageClient` RPC because it asks a live question ("give
 * me a fresh token") that only a running phone app can answer. This is the
 * opposite shape: a fact that rarely changes and that the phone wants to read
 * whenever *it* is open, which is usually not when the watch app is running.
 * A DataItem is retained and replicated by the Data Layer, so the watch writes
 * it once at startup and the phone can read it later from its own local copy,
 * with the watch asleep or out of range.
 *
 * It also cleans itself up: data items belong to the app that wrote them, so
 * uninstalling the watch app removes this one. The phone cannot end up naming a
 * version that is no longer installed.
 *
 * ## Bytes, not a DataMap
 *
 * `DataMap` would be the more obvious carrier, but it is an Android type and
 * would strand this file in `androidMain`, leaving the two sides free to
 * disagree about key names. Encoding to JSON bytes keeps ONE codec in
 * `commonMain` that both the watch writer and the phone reader must go through
 * — the same reason `WearAuthRelayProtocol` is shaped this way.
 *
 * Decoding is lenient (`ignoreUnknownKeys`) and returns null rather than
 * throwing: the writer may be a newer or older watch app than the reader, and
 * a phone that cannot parse the payload must degrade to "installed, version
 * unknown" rather than crash in Settings.
 */
object WearAppInfoProtocol {
    /**
     * Data Layer path the watch writes and the phone reads.
     *
     * Namespaced under `/garage/watch/` alongside the relay's
     * `/garage/auth/` so the two features cannot collide.
     */
    const val APP_INFO_PATH = "/garage/watch/app_info"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(info: WearAppInfo): ByteArray = json.encodeToString(WearAppInfo.serializer(), info).encodeToByteArray()

    fun decode(bytes: ByteArray): WearAppInfo? =
        try {
            json
                .decodeFromString(WearAppInfo.serializer(), bytes.decodeToString())
                // A blank versionName is not a version. Treating it as one would
                // put an empty string where Settings promises a build number.
                .takeIf { it.versionName.isNotBlank() }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
