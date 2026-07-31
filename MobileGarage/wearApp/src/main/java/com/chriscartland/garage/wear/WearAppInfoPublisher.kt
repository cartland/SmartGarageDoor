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

package com.chriscartland.garage.wear

import android.content.Context
import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.wearrelay.WearAppInfo
import com.chriscartland.garage.data.wearrelay.WearAppInfoProtocol
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Tells the paired phone which build is on the watch.
 *
 * The phone's Settings could already say *that* the watch app is installed —
 * the capability it advertises proves that much — but not *which version*,
 * because nothing in the Wearable API reports another node's app version. So
 * the watch writes it, once, into a retained Data Layer item the phone reads at
 * its leisure. See [WearAppInfoProtocol] for why a DataItem rather than a
 * message.
 *
 * Written on every app start rather than only on change. The Data Layer already
 * dedupes byte-identical payloads into a no-op, so the repeat costs nothing, and
 * making it unconditional removes the only interesting failure mode: an
 * "only publish when it changed" check has to persist what was last published,
 * and any drift between that record and reality strands the phone on a stale
 * version with nothing to correct it.
 */
object WearAppInfoPublisher {
    /**
     * Best effort, and deliberately silent on failure. This is a cosmetic line
     * in the phone's settings; a watch with no Data Layer available (or Play
     * services mid-update) must still start normally, and the phone already
     * renders "installed, version unknown" for exactly this case.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun publish(
        context: Context,
        versionName: String,
        versionCode: Long,
    ) {
        try {
            val request = PutDataRequest
                .create(WearAppInfoProtocol.APP_INFO_PATH)
                .setData(
                    WearAppInfoProtocol.encode(
                        WearAppInfo(versionName = versionName, versionCode = versionCode),
                    ),
                )
                // The phone may be sitting in Settings right now. Urgent skips
                // the Data Layer's batching delay, which is otherwise measured
                // in minutes.
                .setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            Logger.d { "WearAppInfo: published $versionName ($versionCode)" }
        } catch (e: Exception) {
            Logger.d { "WearAppInfo: publish failed: $e" }
        }
    }
}
