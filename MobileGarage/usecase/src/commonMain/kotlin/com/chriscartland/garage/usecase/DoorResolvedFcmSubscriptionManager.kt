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

package com.chriscartland.garage.usecase

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.repository.DoorResolvedFcmRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped manager for the additive resolved-on-close FCM topic subscription
 * (`door_open_v2-*`). ADR-015 Manager pattern; mirrors
 * [ButtonHealthFcmSubscriptionManager] but **without** an auth gate — the
 * legacy door subscription ([FcmRegistrationManager]) is not auth-gated either,
 * and the resolved notification is purely additive.
 *
 * Subscribes whenever server config has a door `buildTimestamp`; re-subscribes
 * on rotation (firmware reflash) via the always-unsubscribe-then-subscribe in
 * the repository. This is **additive** — it runs alongside the unchanged
 * [FcmRegistrationManager], so the new build is subscribed to both
 * `door_open-` (state-sync + warning) and `door_open_v2-` (resolved, data-only,
 * no double notification because v2 carries no warning in Phase 1).
 *
 * No cold-start fetch: the resolved notification is purely push-driven — there
 * is nothing to fetch. A failure in this manager can never affect the primary
 * door push-data path.
 */
class DoorResolvedFcmSubscriptionManager(
    private val serverConfigRepository: ServerConfigRepository,
    private val fcmRepository: DoorResolvedFcmRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null

    /** Idempotent — calling twice does not start a second collector. */
    fun start() {
        if (job?.isActive == true) {
            Logger.d { "DoorResolvedFcmSubscriptionManager: already running" }
            return
        }
        job = scope.launch(dispatcher) {
            serverConfigRepository.serverConfig
                .map { it?.buildTimestamp }
                .distinctUntilChanged()
                .collect { buildTimestamp -> handleStateChange(buildTimestamp) }
        }
    }

    private suspend fun handleStateChange(buildTimestamp: String?) {
        if (buildTimestamp.isNullOrEmpty()) {
            Logger.d { "DoorResolvedFcm: no buildTimestamp in server config; staying unsubscribed" }
            fcmRepository.unsubscribeAll()
            return
        }
        Logger.i { "DoorResolvedFcm: subscribing for buildTimestamp=$buildTimestamp" }
        fcmRepository.subscribe(buildTimestamp)
    }
}
