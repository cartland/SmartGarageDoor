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
import androidx.concurrent.futures.await
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallResult
import com.chriscartland.garage.domain.repository.WearCompanionRepository
import com.google.android.gms.wearable.CapabilityClient
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
                WatchAppStatus.InstalledOnWatch
            } else {
                val connected = Wearable.getNodeClient(context).connectedNodes.await()
                if (connected.isEmpty()) WatchAppStatus.NoWatch else WatchAppStatus.WatchNeedsApp
            }
        } catch (e: Exception) {
            Logger.d { "WearCompanion: wearable status query failed: $e" }
            WatchAppStatus.Unavailable
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
    }
}
