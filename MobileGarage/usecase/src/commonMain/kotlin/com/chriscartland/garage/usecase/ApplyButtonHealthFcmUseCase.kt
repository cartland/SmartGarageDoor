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

import com.chriscartland.garage.domain.model.ButtonHealth
import com.chriscartland.garage.domain.repository.ButtonHealthRepository

/**
 * Apply a server-pushed update from an FCM data message.
 *
 * Wrapper around [ButtonHealthRepository.applyFcmUpdate] so the
 * FCMService dispatcher consumes a UseCase rather than calling the
 * repository directly (consistent with the door FCM path's
 * ReceiveFcmDoorEventUseCase).
 */
class ApplyButtonHealthFcmUseCase(
    private val repository: ButtonHealthRepository,
) {
    operator fun invoke(update: ButtonHealth) = repository.applyFcmUpdate(update)
}
