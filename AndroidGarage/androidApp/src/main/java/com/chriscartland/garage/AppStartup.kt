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

package com.chriscartland.garage

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.usecase.AppLoggerViewModel
import com.chriscartland.garage.usecase.ButtonHealthFcmSubscriptionManager
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.LiveClock

/**
 * App startup actions. Extracted from MainActivity for testability.
 *
 * If this grows past simple orchestration into conditional logic,
 * promote to a UseCase.
 */
class AppStartup(
    private val fcmRegistrationManager: FcmRegistrationManager,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val liveClock: LiveClock,
    private val appLoggerViewModel: AppLoggerViewModel,
    private val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager,
) {
    /**
     * Performs startup actions: FCM registration, staleness tracking,
     * live-clock ticker, button-health subscription, and logging.
     * Returns the list of actions taken (for testing/logging).
     */
    fun run(): List<String> {
        val actions = mutableListOf<String>()

        Logger.d { "AppStartup: Starting FCM registration manager" }
        fcmRegistrationManager.start()
        actions.add("startFcmRegistration")

        Logger.d { "AppStartup: Starting check-in staleness manager" }
        checkInStalenessManager.start()
        actions.add("startCheckInStaleness")

        Logger.d { "AppStartup: Starting live clock" }
        liveClock.start()
        actions.add("startLiveClock")

        Logger.d { "AppStartup: Starting button health FCM subscription manager" }
        buttonHealthFcmSubscriptionManager.start()
        actions.add("startButtonHealthFcmSubscription")

        appLoggerViewModel.log(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC)
        actions.add("logFcmSubscribe")

        // One-shot trim of any keys that grew past the per-key cap before
        // the cap was added (existing installs may have ~50K rows). The
        // per-write cap inside log() keeps it bounded going forward, so
        // this only matters for the migration case.
        Logger.d { "AppStartup: Pruning old AppLogger entries" }
        appLoggerViewModel.pruneOldEntries()
        actions.add("pruneAppLogger")

        return actions
    }
}
