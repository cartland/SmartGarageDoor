/*
 * Copyright 2024 Chris Cartland. All rights reserved.
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

import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.repository.AppLoggerRepository
import com.chriscartland.garage.domain.repository.DoorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives a [DoorEvent] that arrived via FCM and persists it through the
 * repository layer, so the rest of the app sees it the same way as a
 * pull-to-refresh fetch — Home/History tabs auto-update via the existing
 * StateFlow observers.
 *
 * Why a UseCase boundary: the FCM service is a thin Android shell that should
 * not reach directly into a repository. Routing FCM-arrived events through a
 * UseCase keeps the Android layer dumb and lets the persistence work survive
 * the FCM service's lifecycle (its `serviceScope` cancels in `onDestroy`,
 * which can fire as soon as `onMessageReceived` returns).
 *
 * Per ADR-019, all repository side-effects MUST run on `externalScope` —
 * implementations dispatch the insert there.
 */
interface ReceiveFcmDoorEventUseCase {
    operator fun invoke(event: DoorEvent)
}

class DefaultReceiveFcmDoorEventUseCase(
    private val doorRepository: DoorRepository,
    private val appLoggerRepository: AppLoggerRepository,
    private val externalScope: CoroutineScope,
) : ReceiveFcmDoorEventUseCase {
    override fun invoke(event: DoorEvent) {
        externalScope.launch {
            doorRepository.insertDoorEvent(event)
            appLoggerRepository.log(AppLoggerKeys.FCM_DOOR_RECEIVED)
        }
    }
}
