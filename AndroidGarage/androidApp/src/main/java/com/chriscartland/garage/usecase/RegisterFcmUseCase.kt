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

import android.app.Activity
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.toFcmTopic
import com.chriscartland.garage.domain.repository.DoorRepository
import com.chriscartland.garage.fcm.DoorFcmRepository

/**
 * Maps [DoorFcmState] to the simpler [FcmRegistrationStatus] enum.
 */
fun DoorFcmState.toRegistrationStatus(): FcmRegistrationStatus =
    when (this) {
        is DoorFcmState.Registered -> FcmRegistrationStatus.REGISTERED
        DoorFcmState.NotRegistered -> FcmRegistrationStatus.NOT_REGISTERED
        DoorFcmState.Unknown -> FcmRegistrationStatus.UNKNOWN
    }

/**
 * Fetches the current FCM registration status.
 */
class FetchFcmStatusUseCase
    constructor(
        private val doorFcmRepository: DoorFcmRepository,
    ) {
        suspend operator fun invoke(activity: Activity): FcmRegistrationStatus =
            doorFcmRepository.fetchStatus(activity).toRegistrationStatus()
    }

/**
 * Registers for FCM door notifications.
 *
 * Fetches the build timestamp from server config, converts it to an FCM topic,
 * and subscribes. Returns NOT_REGISTERED if build timestamp is unavailable.
 */
class RegisterFcmUseCase
    constructor(
        private val doorRepository: DoorRepository,
        private val doorFcmRepository: DoorFcmRepository,
    ) {
        suspend operator fun invoke(activity: Activity): FcmRegistrationStatus {
            val buildTimestamp = doorRepository.fetchBuildTimestampCached()
                ?: return FcmRegistrationStatus.NOT_REGISTERED
            val result = doorFcmRepository.registerDoor(activity, buildTimestamp.toFcmTopic())
            return result.toRegistrationStatus()
        }
    }

/**
 * Deregisters from FCM door notifications.
 */
class DeregisterFcmUseCase
    constructor(
        private val doorFcmRepository: DoorFcmRepository,
    ) {
        suspend operator fun invoke(activity: Activity): FcmRegistrationStatus =
            doorFcmRepository.deregisterDoor(activity).toRegistrationStatus()
    }
