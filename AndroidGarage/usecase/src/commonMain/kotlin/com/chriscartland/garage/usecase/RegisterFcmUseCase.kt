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

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorFcmState
import com.chriscartland.garage.domain.model.FcmRegistrationStatus
import com.chriscartland.garage.domain.model.toFcmTopic
import com.chriscartland.garage.domain.repository.DoorFcmRepository
import com.chriscartland.garage.domain.repository.DoorRepository

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
class FetchFcmStatusUseCase(
    private val doorFcmRepository: DoorFcmRepository,
) {
    suspend operator fun invoke(): FcmRegistrationStatus = doorFcmRepository.fetchStatus().toRegistrationStatus()
}

/**
 * Registers for FCM door notifications. Single attempt — returns result.
 *
 * Fetches the build timestamp from server config, converts it to an FCM topic,
 * and subscribes. Caller owns retry policy (see ADR-015).
 */
open class RegisterFcmUseCase(
    private val doorRepository: DoorRepository,
    private val doorFcmRepository: DoorFcmRepository,
) {
    open suspend operator fun invoke(): AppResult<Unit, ActionError> {
        val buildTimestamp = doorRepository.fetchBuildTimestampCached()
            ?: return AppResult.Error(ActionError.MissingData)
        val result = doorFcmRepository.registerDoor(buildTimestamp.toFcmTopic())
        return when (result) {
            is DoorFcmState.Registered -> AppResult.Success(Unit)
            DoorFcmState.NotRegistered -> AppResult.Error(ActionError.NetworkFailed)
            DoorFcmState.Unknown -> AppResult.Error(ActionError.NetworkFailed)
        }
    }
}

/**
 * Deregisters from FCM door notifications.
 */
class DeregisterFcmUseCase(
    private val doorFcmRepository: DoorFcmRepository,
) {
    suspend operator fun invoke(): FcmRegistrationStatus = doorFcmRepository.deregisterDoor().toRegistrationStatus()
}
