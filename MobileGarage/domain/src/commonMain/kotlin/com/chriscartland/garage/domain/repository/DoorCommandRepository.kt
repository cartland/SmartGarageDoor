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

import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorCommandVerdict
import com.chriscartland.garage.domain.model.VoiceIntent

/**
 * Asks the server whether a spoken direction is actionable.
 *
 * Separate from [RemoteButtonRepository] on purpose: that one ACTS on the door
 * and this one only ASKS about it. Keeping them apart is what lets the press
 * path stay exactly as it was — a direction-less toggle — while voice gains a
 * second opinion.
 *
 * Errors are typed rather than folded into the verdict, because "the server
 * says no" and "I could not reach the server" are different facts and the
 * caller treats them differently in its message (though both refuse).
 */
interface DoorCommandRepository {
    suspend fun checkCommand(intent: VoiceIntent): AppResult<DoorCommandVerdict, ActionError>
}
