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

package com.chriscartland.garage.testcommon

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorCommandRejection
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import com.chriscartland.garage.domain.model.VoiceIntent
import com.chriscartland.garage.domain.repository.DoorCommandRepository

/**
 * Scriptable stand-in for the server's verdict.
 *
 * Defaults to ACCEPTING so a test that does not care about this gate reads the
 * same as it did before the gate existed. Every test that means to exercise a
 * refusal says so out loud.
 */
class FakeDoorCommandRepository : DoorCommandRepository {
    private val _calls = mutableListOf<VoiceIntent>()
    val calls: List<VoiceIntent> get() = _calls
    val callCount: Int get() = _calls.size

    private var next: AppResult<DoorCommandVerdict, ActionError> =
        AppResult.Success(accepted())

    /** The server allows the next command. */
    fun accept() {
        next = AppResult.Success(accepted())
    }

    /** The server refuses the next command, for [rejection]. */
    fun refuse(rejection: DoorCommandRejection) {
        next = AppResult.Success(
            DoorCommandVerdict(
                accepted = false,
                rejection = rejection,
                doorState = "UNKNOWN",
                checkInStale = false,
            ),
        )
    }

    /** The server could not be reached / answered an error. */
    fun fail(error: ActionError = ActionError.NetworkFailed) {
        next = AppResult.Error(error)
    }

    override suspend fun checkCommand(intent: VoiceIntent): AppResult<DoorCommandVerdict, ActionError> {
        _calls.add(intent)
        return next
    }

    private companion object {
        fun accepted() =
            DoorCommandVerdict(
                accepted = true,
                rejection = null,
                doorState = "CLOSED",
                checkInStale = false,
            )
    }
}
