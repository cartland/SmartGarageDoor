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

import androidx.annotation.StringRes
import com.chriscartland.garage.R
import com.chriscartland.garage.domain.model.SnoozeAction

/**
 * Words for how the warning's Snooze action went, said where the warning
 * was. Which outcome it is was decided in shared code (`SnoozeAction.of`);
 * this only picks the resources — and the failure bodies are the Settings
 * sheet's own `snooze_failed_*` strings, so the two surfaces cannot word the
 * same failure differently.
 */
object SnoozeOutcomeWords {
    /** The outcome card's title, or null when there is nothing to say (nothing in flight ever reaches a card). */
    @StringRes
    fun title(action: SnoozeAction): Int? =
        when (action) {
            SnoozeAction.Idle, SnoozeAction.Sending -> null
            is SnoozeAction.Succeeded -> R.string.notification_snoozed_title
            is SnoozeAction.Failed -> R.string.notification_snooze_failed_title
        }

    /**
     * The card's body, or null. For [SnoozeAction.Succeeded.Set] the resource
     * takes the formatted end time as its one argument.
     */
    @StringRes
    fun body(action: SnoozeAction): Int? =
        when (action) {
            SnoozeAction.Idle, SnoozeAction.Sending -> null
            is SnoozeAction.Succeeded.Set -> R.string.settings_notifications_subtitle_snoozing_until
            SnoozeAction.Succeeded.Cleared -> R.string.notification_snooze_cleared
            SnoozeAction.Failed.NotAuthenticated -> R.string.snooze_failed_not_authenticated
            SnoozeAction.Failed.MissingData -> R.string.snooze_failed_missing_data
            SnoozeAction.Failed.NetworkError -> R.string.snooze_failed_network
            SnoozeAction.Failed.EventChanged -> R.string.snooze_failed_event_changed
        }

    /**
     * Whether the outcome REPLACES the warning card or sits beside it. A
     * success replaces it: the door is still open, but the user just said
     * they know. A failure leaves it: the warning still stands, and the card
     * beside it says why the snooze did not.
     */
    fun replacesWarning(action: SnoozeAction): Boolean = action is SnoozeAction.Succeeded
}
