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

package com.chriscartland.garage.wearrelay

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import co.touchlab.kermit.Logger
import com.chriscartland.garage.data.wearrelay.WearAppInfoProtocol
import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.domain.repository.WearCompanionRepository
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * [WearCompanionRepository] over the Play services Wearable API.
 *
 * Detection: the watch app declares
 * [WearCompanionRepository.WATCH_APP_CAPABILITY] in its Wear resources;
 * any node advertising it means the app is installed. A connected node
 * without it means a watch that needs the app. Polls while collected
 * (same cadence rationale as the watch side's `RelayFallbackAuthBridge`)
 * so an install completing on the watch flips the row live, and a watch
 * connecting mid-screen is picked up without re-entering.
 *
 * Install: `RemoteActivityHelper` launches the app's Play Store listing
 * directly on the watch — the supported "install from your phone" flow.
 * Falls back to [WatchInstallResult.Failed] on any error; the UI then
 * opens the phone's own Play Store listing instead.
 */
class PlayServicesWearCompanionRepository(
    private val context: Context,
    private val playStorePackageName: String,
) : WearCompanionRepository {
    override fun observeWatchAppStatus(): Flow<WatchAppStatus> =
        flow {
            while (true) {
                val status = queryStatus()
                emit(status)
                if (status == WatchAppStatus.Unavailable) {
                    // No Wearable module on this device — terminal, stop polling.
                    return@flow
                }
                delay(STATUS_POLL_MILLIS)
            }
        }

    // Play services surfaces failures as a mix of ApiException, runtime
    // exceptions, and wrapped Task errors; this is a best-effort boundary
    // where any failure maps to Unavailable, so the generic catch is the
    // design (same posture as FirebaseAuthBridge).
    @Suppress("TooGenericExceptionCaught")
    private suspend fun queryStatus(): WatchAppStatus =
        try {
            val capabilityNodes = Wearable
                .getCapabilityClient(context)
                .getCapability(
                    WearCompanionRepository.WATCH_APP_CAPABILITY,
                    CapabilityClient.FILTER_ALL,
                ).await()
                .nodes
            if (capabilityNodes.isNotEmpty()) {
                WatchAppStatus.InstalledOnWatch(versionName = readWatchVersionName())
            } else {
                val connected = Wearable.getNodeClient(context).connectedNodes.await()
                if (connected.isEmpty()) WatchAppStatus.NoWatch else WatchAppStatus.WatchNeedsApp
            }
        } catch (e: Exception) {
            Logger.d { "WearCompanion: wearable status query failed: $e" }
            WatchAppStatus.Unavailable
        }

    /**
     * The version the watch app published about itself, or null if it has not.
     *
     * Null is a real answer and is rendered as such: a watch running a build
     * older than the one that started publishing this says nothing, and the
     * Data Layer's local copy may simply not have arrived yet. Reporting
     * "installed" while admitting the version is unknown beats guessing.
     *
     * Read from the LOCAL replica — no round trip, so this stays cheap enough
     * to sit inside the 15s status poll and works with the watch asleep or out
     * of range.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun readWatchVersionName(): String? =
        try {
            val uri = Uri
                .Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                // Any node, not a specific one: the phone does not know (and
                // should not care) which watch wrote it, and hardcoding a node
                // id would break the moment a second watch is paired.
                .authority(ANY_NODE)
                .path(WearAppInfoProtocol.APP_INFO_PATH)
                .build()
            val items = Wearable.getDataClient(context).getDataItems(uri).await()
            try {
                // With more than one watch paired this picks the first that has
                // published. The row is singular ("your watch"), so naming one
                // build is the honest simplification until the UI is plural.
                items
                    .asSequence()
                    .mapNotNull { it.data }
                    .mapNotNull { WearAppInfoProtocol.decode(it) }
                    .firstOrNull()
                    ?.versionName
            } finally {
                // DataItemBuffer holds native memory and does NOT free itself.
                // Leaking it inside a 15s poll would accumulate for as long as
                // Settings is open.
                items.release()
            }
        } catch (e: Exception) {
            Logger.d { "WearCompanion: watch version read failed: $e" }
            null
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun requestInstallOnWatch(): WatchInstallResult =
        try {
            val connected = Wearable.getNodeClient(context).connectedNodes.await()
            if (connected.isEmpty()) {
                WatchInstallResult.NoWatchReachable
            } else {
                val opened = launchPlayStoreOnNodes(connected.map { it.id to it.displayName })
                if (opened > 0) WatchInstallResult.OpenedOnWatch else WatchInstallResult.Failed
            }
        } catch (e: Exception) {
            Logger.w { "WearCompanion: install request failed: $e" }
            WatchInstallResult.Failed
        }

    /** Returns how many nodes the Play Store listing was launched on. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun launchPlayStoreOnNodes(nodes: List<Pair<String, String>>): Int {
        val helper = RemoteActivityHelper(context)
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData("market://details?id=$playStorePackageName".toUri())
        var opened = 0
        nodes.forEach { (nodeId, displayName) ->
            try {
                helper.startRemoteActivity(intent, nodeId).await()
                opened++
            } catch (e: Exception) {
                Logger.w { "WearCompanion: remote launch failed on $displayName: $e" }
            }
        }
        return opened
    }

    private companion object {
        /** Re-query cadence while the Settings screen is collecting. */
        const val STATUS_POLL_MILLIS: Long = 15_000L

        /** Data Layer wildcard authority: match the item on any paired node. */
        const val ANY_NODE = "*"
    }
}
