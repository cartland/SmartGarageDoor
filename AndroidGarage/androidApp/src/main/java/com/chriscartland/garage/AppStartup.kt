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
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.usecase.ButtonHealthFcmSubscriptionManager
import com.chriscartland.garage.usecase.CheckInStalenessManager
import com.chriscartland.garage.usecase.FcmRegistrationManager
import com.chriscartland.garage.usecase.InitialDoorFetchManager
import com.chriscartland.garage.usecase.LiveClock
import com.chriscartland.garage.usecase.LogAppEventUseCase
import com.chriscartland.garage.usecase.RunStartupDiagnosticsMaintenanceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App startup actions. Extracted from MainActivity for testability.
 *
 * Calls UseCases directly (no ViewModels) — startup-time concerns are
 * not screen-scoped and don't belong on a VM. The `externalScope` (the
 * app-wide singleton scope) owns the fire-and-forget launches so they
 * can outlive any Activity recreation.
 *
 * If this grows past simple orchestration into conditional logic,
 * promote individual steps into their own UseCases.
 */
class AppStartup(
    private val fcmRegistrationManager: FcmRegistrationManager,
    private val checkInStalenessManager: CheckInStalenessManager,
    private val liveClock: LiveClock,
    private val logAppEvent: LogAppEventUseCase,
    private val runStartupDiagnosticsMaintenance: RunStartupDiagnosticsMaintenanceUseCase,
    private val buttonHealthFcmSubscriptionManager: ButtonHealthFcmSubscriptionManager,
    private val initialDoorFetchManager: InitialDoorFetchManager,
    private val externalScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
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

        Logger.d { "AppStartup: Starting initial door fetch (one-shot per process)" }
        initialDoorFetchManager.start()
        actions.add("startInitialDoorFetch")

        externalScope.launch(dispatchers.io) {
            logAppEvent(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC)
        }
        actions.add("logFcmSubscribe")

        // Bundled into a single coroutine launch so seed and prune are
        // guaranteed sequential. Separate fire-and-forget launches would
        // race on the IO dispatcher and prune could delete rows the seed
        // wanted to count, locking in a lower lifetime counter for users
        // upgrading from a pre-cap (pre-2.10.4) version. See
        // RunStartupDiagnosticsMaintenanceUseCase KDoc.
        Logger.d { "AppStartup: Running Diagnostics maintenance (seed + prune)" }
        externalScope.launch(dispatchers.io) {
            runStartupDiagnosticsMaintenance()
        }
        actions.add("runDiagnosticsMaintenance")

        return actions
    }
}
