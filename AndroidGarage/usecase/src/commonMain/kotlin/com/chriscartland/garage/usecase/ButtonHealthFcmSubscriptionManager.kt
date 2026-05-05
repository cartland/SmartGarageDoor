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
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.ButtonHealthError
import com.chriscartland.garage.domain.repository.AuthRepository
import com.chriscartland.garage.domain.repository.ButtonHealthFcmRepository
import com.chriscartland.garage.domain.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped manager for the button-health FCM topic subscription
 * (ADR-015 Manager pattern; mirrors [FcmRegistrationManager]).
 *
 * Subscription lifecycle:
 *  - Always-unsubscribe-then-conditionally-subscribe on every state
 *    change. This sidesteps the "did Firebase Task cancel?" question:
 *    Firebase's subscribe Task is not cancellable via coroutine
 *    cancellation, so we just always issue the unsubscribe.
 *  - When (signed in AND server config has buttonBuildTimestamp) →
 *    subscribe to the corresponding topic and trigger one-shot
 *    cold-start fetch.
 *  - On any other state → unsubscribe.
 *
 * Handles buttonBuildTimestamp rotation (firmware reflash): when
 * server config changes, the combine emits and the always-unsubscribe-
 * then-subscribe pattern updates the FCM topic.
 *
 * On cold-start fetch returning Forbidden (user not allowlisted):
 * unsubscribe from the FCM topic so we don't keep receiving updates
 * the user has no business seeing.
 */
class ButtonHealthFcmSubscriptionManager(
    private val authRepository: AuthRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val fcmRepository: ButtonHealthFcmRepository,
    private val fetchButtonHealthUseCase: FetchButtonHealthUseCase,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null

    /** Idempotent — calling twice does not start a second collector. */
    fun start() {
        if (job?.isActive == true) {
            Logger.d { "ButtonHealthFcmSubscriptionManager: already running" }
            return
        }
        job = scope.launch(dispatcher) {
            combine(
                authRepository.authState,
                serverConfigRepository.serverConfig.map { it?.remoteButtonBuildTimestamp },
            ) { auth, buildTimestamp -> auth to buildTimestamp }
                .distinctUntilChanged()
                .collect { (auth, buildTimestamp) ->
                    handleStateChange(auth, buildTimestamp)
                }
        }
    }

    private suspend fun handleStateChange(
        auth: AuthState,
        buildTimestamp: String?,
    ) {
        // Always unsubscribe first — idempotent. Cleans up prior topic if
        // buildTimestamp rotated, or any prior subscription if signing out.
        fcmRepository.unsubscribeAll()
        if (auth !is AuthState.Authenticated) {
            Logger.d { "ButtonHealthFcm: not signed in; staying unsubscribed" }
            return
        }
        if (buildTimestamp.isNullOrEmpty()) {
            Logger.d { "ButtonHealthFcm: no buttonBuildTimestamp in server config; staying unsubscribed" }
            return
        }
        Logger.i { "ButtonHealthFcm: subscribing for buildTimestamp=$buildTimestamp" }
        fcmRepository.subscribe(buildTimestamp)
        // Cold-start fetch. If the server returns Forbidden (user not
        // allowlisted), unsubscribe — we shouldn't keep receiving updates.
        val idToken = auth.user.idToken.idToken
        when (val result = fetchButtonHealthUseCase(idToken)) {
            is AppResult.Success -> {
                Logger.i { "ButtonHealthFcm: cold-start fetch succeeded" }
            }
            is AppResult.Error -> when (result.error) {
                is ButtonHealthError.Forbidden -> {
                    Logger.w { "ButtonHealthFcm: cold-start fetch forbidden; unsubscribing" }
                    fcmRepository.unsubscribeAll()
                }
                is ButtonHealthError.Network -> {
                    Logger.w { "ButtonHealthFcm: cold-start fetch failed (network); keeping subscription" }
                }
            }
        }
    }
}
