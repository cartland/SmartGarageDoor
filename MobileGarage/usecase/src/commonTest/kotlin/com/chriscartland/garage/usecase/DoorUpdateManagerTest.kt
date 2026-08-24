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

import com.chriscartland.garage.domain.model.AppConfig
import com.chriscartland.garage.domain.model.DoorUpdateStrategyId
import com.chriscartland.garage.domain.model.DoorUpdateStrategyOverride
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAppSettingsRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.FakeDoorRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The swap itself: which strategy runs, and that the previous one really
 * stops when it does.
 *
 * The two platform defaults are exercised through the same manager, with
 * only [AppConfig.defaultDoorUpdateStrategy] differing — which is the
 * claim the rollout rests on (Android keeps push, iOS polls, one code
 * path).
 */
class DoorUpdateManagerTest {
    private val interval = PollDoorUpdateStrategy.DEFAULT_INTERVAL_MS

    private fun appConfig(default: DoorUpdateStrategyId) =
        AppConfig(
            baseUrl = "https://example.invalid",
            recentEventCount = 100,
            serverConfigKey = "",
            snoozeNotificationsOption = true,
            remoteButtonPushEnabled = true,
            defaultDoorUpdateStrategy = default,
        )

    private fun TestScope.createManager(
        doorRepo: FakeDoorRepository,
        visibility: AppVisibilityState,
        settings: FakeAppSettingsRepository,
        default: DoorUpdateStrategyId,
    ): DoorUpdateManager {
        val logAppEvent = LogAppEventUseCase(FakeAppLoggerRepository(), FakeDiagnosticsCountersRepository())
        val fetchCurrent = FetchCurrentDoorEventUseCase(doorRepo)
        return DoorUpdateManager(
            pushStrategy = PushDoorUpdateStrategy(),
            pollStrategy = PollDoorUpdateStrategy(fetchCurrent, visibility, logAppEvent),
            pushWithForegroundRefreshStrategy =
                PushWithForegroundRefreshDoorUpdateStrategy(fetchCurrent, visibility, logAppEvent),
            appSettings = settings,
            appConfig = appConfig(default),
            scope = backgroundScope,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @Test
    fun androidDefaultRunsPushAndNeverPolls() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val manager = createManager(doorRepo, visibility, FakeAppSettingsRepository(), DoorUpdateStrategyId.PUSH)

            manager.start()
            visibility.setVisible(true)
            advanceTimeBy(interval * 10)
            runCurrent()

            assertEquals(DoorUpdateStrategyId.PUSH, manager.activeStrategy.value)
            // Android's behavior is unchanged by this whole mechanism: no
            // request the app was not already making.
            assertEquals(0, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun iosDefaultRunsPolling() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val manager = createManager(doorRepo, visibility, FakeAppSettingsRepository(), DoorUpdateStrategyId.POLL)

            manager.start()
            visibility.setVisible(true)
            runCurrent()
            advanceTimeBy(interval + 1)
            runCurrent()

            assertEquals(DoorUpdateStrategyId.POLL, manager.activeStrategy.value)
            assertEquals(2, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun anOverrideSwapsTheRunningStrategyWithoutARestart() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val settings = FakeAppSettingsRepository()
            val manager = createManager(doorRepo, visibility, settings, DoorUpdateStrategyId.PUSH)

            manager.start()
            visibility.setVisible(true)
            advanceTimeBy(interval * 3)
            runCurrent()
            assertEquals(0, doorRepo.fetchCurrentDoorEventCount)

            settings.doorUpdateStrategy.set(DoorUpdateStrategyOverride.POLL)
            runCurrent()

            assertEquals(DoorUpdateStrategyId.POLL, manager.activeStrategy.value)
            // The new strategy starts immediately — the app was already
            // visible, so its foreground refresh is due now.
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun swappingBackToPushStopsTheRunningPoll() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val settings = FakeAppSettingsRepository()
            settings.doorUpdateStrategy.set(DoorUpdateStrategyOverride.POLL)
            val manager = createManager(doorRepo, visibility, settings, DoorUpdateStrategyId.PUSH)

            manager.start()
            visibility.setVisible(true)
            advanceTimeBy(interval + 1)
            runCurrent()
            val whilePolling = doorRepo.fetchCurrentDoorEventCount

            settings.doorUpdateStrategy.set(DoorUpdateStrategyOverride.PUSH)
            runCurrent()
            advanceTimeBy(interval * 10)
            runCurrent()

            // This is the property that makes the flag safe to flip at
            // runtime: cancelling the old strategy really cancels its timer,
            // rather than leaving two strategies fetching at once.
            assertEquals(DoorUpdateStrategyId.PUSH, manager.activeStrategy.value)
            assertEquals(whilePolling, doorRepo.fetchCurrentDoorEventCount)
        }

    @Test
    fun platformDefaultOverrideFollowsTheBuildDefault() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val settings = FakeAppSettingsRepository()
            settings.doorUpdateStrategy.set(DoorUpdateStrategyOverride.POLL)
            val manager = createManager(doorRepo, visibility, settings, DoorUpdateStrategyId.PUSH)

            manager.start()
            runCurrent()
            settings.doorUpdateStrategy.set(DoorUpdateStrategyOverride.PLATFORM_DEFAULT)
            runCurrent()

            // "No opinion" resolves to the build's default, which is what
            // lets a later release move iOS off polling without stranding
            // devices whose owner once opened the picker.
            assertEquals(DoorUpdateStrategyId.PUSH, manager.activeStrategy.value)
        }

    @Test
    fun startIsIdempotent() =
        runTest {
            val doorRepo = FakeDoorRepository()
            val visibility = AppVisibilityState()
            val manager = createManager(doorRepo, visibility, FakeAppSettingsRepository(), DoorUpdateStrategyId.POLL)

            manager.start()
            manager.start()
            manager.start()
            visibility.setVisible(true)
            runCurrent()

            // Three starts, one poll — otherwise an Activity restart would
            // multiply the app's request rate.
            assertEquals(1, doorRepo.fetchCurrentDoorEventCount)
        }
}
