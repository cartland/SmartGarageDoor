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

package com.chriscartland.garage.fcm

import com.chriscartland.garage.data.LocalDoorDataSource
import com.chriscartland.garage.domain.model.SnoozeAction
import com.chriscartland.garage.domain.model.SnoozeDurationServerOption
import com.chriscartland.garage.usecase.SnoozeNotificationsUseCase
import kotlinx.coroutines.flow.first

/**
 * The warning's Snooze action, decided: what a tap on "Snooze 1 hour" does
 * (strategy 3.5). Kept out of the receiver so it runs on the JVM with fakes.
 *
 * - **Room's first emission, not the repository's `StateFlow`.** The tap can
 *   land in a process that exists only to answer it, where
 *   `DoorRepository.currentDoorEvent` is still its seed (null) — the same
 *   cold-process read the widget and the iOS intent use.
 * - **The shared use case does the rest.** `SnoozeNotificationsUseCase` gates
 *   on sign-in, refuses a door it cannot date, and binds the snooze to the
 *   event the server will judge it against; `SnoozeAction.of` is the one
 *   mapping from its result to a worded outcome, so this action and the
 *   Settings sheet cannot disagree.
 * - **One hour, fixed.** A notification action has nowhere to put a picker;
 *   an hour is long enough to matter and short enough to be safe to tap
 *   without one. The sheet still offers the rest.
 *
 * It holds nothing that can press. `SnoozeActionReceiverSafetyTest` reads
 * this file and refuses any route to the button.
 */
object SnoozeFromNotification {
    /** The one duration a notification action offers. */
    val DURATION: SnoozeDurationServerOption = SnoozeDurationServerOption.HOURS_1

    suspend fun perform(
        localDoorDataSource: LocalDoorDataSource,
        snoozeNotifications: SnoozeNotificationsUseCase,
    ): SnoozeAction {
        val event = localDoorDataSource.currentDoorEvent.first()
        val result =
            snoozeNotifications(
                snoozeDurationHours = DURATION.duration,
                lastChangeTimeSeconds = event?.lastChangeTimeSeconds,
            )
        return SnoozeAction.of(result)
    }
}
