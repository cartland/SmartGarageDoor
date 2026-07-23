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

package com.chriscartland.garage.domain.repository

import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.domain.model.WatchInstallResult
import kotlinx.coroutines.flow.Flow

/**
 * Companion-watch integration: whether the paired watch has the Wear OS
 * app, and a remote-install action that opens the app's Play Store
 * listing directly on the watch.
 *
 * Like [RemoteButtonRepository], this performs remote actions and holds
 * no storage. Android implements it over the Play services Wearable API
 * (`PlayServicesWearCompanionRepository`); iOS wires the terminal no-op
 * (`UnavailableWearCompanionRepository`) since there is no Wear OS
 * concept there.
 */
interface WearCompanionRepository {
    /**
     * Observe the watch-app install status. Emits [WatchAppStatus.Unknown]
     * consumers should hide watch UI for, then live status while collected
     * (the Android implementation re-polls so an install completing on the
     * watch flips the status without leaving the screen). Emits
     * [WatchAppStatus.Unavailable] and completes when the platform cannot
     * query watches at all.
     */
    fun observeWatchAppStatus(): Flow<WatchAppStatus>

    /**
     * Open the app's Play Store listing on the connected watch(es) that
     * don't have the app. Best effort; never throws.
     */
    suspend fun requestInstallOnWatch(): WatchInstallResult

    companion object {
        /**
         * Wearable Data Layer capability the WATCH app declares (in
         * `wearApp/src/main/res/values/wear.xml`) so the phone can detect
         * the app is installed on a paired watch. The phone's own relay
         * capability is `WearAuthRelayProtocol.PHONE_AUTH_CAPABILITY` —
         * same mechanism, opposite direction. Pinned to the XML by
         * `WearCapabilityDeclarationTest` in `:wearApp`.
         */
        const val WATCH_APP_CAPABILITY: String = "garage_watch_app"
    }
}
