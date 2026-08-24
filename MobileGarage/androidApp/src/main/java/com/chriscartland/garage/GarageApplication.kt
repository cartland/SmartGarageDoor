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

import android.app.Activity
import android.app.Application
import android.os.Bundle
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chriscartland.garage.di.AppComponent
import com.chriscartland.garage.di.create
import com.chriscartland.garage.fcm.DoorNotificationPresenter

class GarageApplication : Application() {
    /** kotlin-inject component for dependency injection. */
    val component: AppComponent by lazy {
        AppComponent::class.create(this)
    }

    override fun onCreate() {
        super.onCreate()
        configureLogging()
        // Warm the DataStore cache so settings are available before first composition.
        // DataStore reads are local file I/O — typically <10ms.
        // This ensures card expand/collapse state is correct on first render.
        component
        // Create the app-owned "Garage door" notification channel eagerly so the
        // manifest default_notification_channel_id has a real channel for
        // OS-rendered background open-door warnings to land on (M4). Idempotent.
        DoorNotificationPresenter.createChannel(this)
        reportVisibilityToSharedCode()
    }

    /**
     * Tell the shared `AppVisibilityState` when the user can see the app,
     * so a visibility-gated `DoorUpdateStrategy` (polling, foreground
     * refresh) knows when to run.
     *
     * Counting started Activities rather than overriding `MainActivity`'s
     * `onStart`/`onStop` is what makes a configuration change a non-event:
     * Android starts the new Activity before stopping the old one, so the
     * count goes 1 → 2 → 1 and never touches zero. Per-Activity callbacks
     * would report a background/foreground round trip on every rotation and
     * fire a pointless fetch each time.
     *
     * Android ships `DoorUpdateStrategyId.PUSH`, which ignores this
     * entirely; the wiring exists so a developer can switch a phone to
     * polling from Settings → Developer and have it behave correctly.
     */
    private fun reportVisibilityToSharedCode() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private var startedActivities = 0

                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    component.appVisibilityState.setVisible(true)
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    if (startedActivities <= 0) {
                        startedActivities = 0
                        component.appVisibilityState.setVisible(false)
                    }
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * Set a Kermit MinSeverity floor for release builds.
     *
     * Kermit defaults to Verbose, meaning every `Logger.v/d/i` call reaches
     * logcat in release builds. Several of those calls in this codebase
     * render `data class.toString()` for ServerConfig (carries
     * `remoteButtonPushKey`), AuthState (carries user email), and FCM
     * tokens — sensitive material that should not be readable via
     * `adb logcat` on a production install. See the 2026-05-14 security
     * audit, finding H1.
     *
     * Debug builds keep the full Verbose firehose so developers can see
     * every log line during local iteration.
     */
    private fun configureLogging() {
        if (!BuildConfig.DEBUG) {
            Logger.setMinSeverity(Severity.Warn)
        }
    }
}
