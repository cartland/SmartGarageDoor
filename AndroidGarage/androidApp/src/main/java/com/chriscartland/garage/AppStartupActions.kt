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
import com.chriscartland.garage.usecase.FcmRegistrationManager

/**
 * Actions performed on app startup. Extracted from MainActivity for testability.
 *
 * Call [onActivityCreated] from MainActivity.onCreate() after setContent.
 */
class AppStartupActions(
    private val fcmRegistrationManager: FcmRegistrationManager,
    private val appLoggerViewModel: AppLoggerViewModel,
) {
    /**
     * Performs startup actions: FCM registration and logging.
     * Returns the list of actions taken (for testing/logging).
     */
    fun onActivityCreated(): List<String> {
        val actions = mutableListOf<String>()

        Logger.d { "AppStartup: Starting FCM registration manager" }
        fcmRegistrationManager.start()
        actions.add("startFcmRegistration")

        appLoggerViewModel.log(AppLoggerKeys.ON_CREATE_FCM_SUBSCRIBE_TOPIC)
        actions.add("logFcmSubscribe")

        return actions
    }
}
