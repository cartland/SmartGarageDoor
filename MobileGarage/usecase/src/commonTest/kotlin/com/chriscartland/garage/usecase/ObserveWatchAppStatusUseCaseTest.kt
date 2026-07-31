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

import com.chriscartland.garage.domain.model.WatchAppStatus
import com.chriscartland.garage.testcommon.FakeWearCompanionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the Settings "Watch" section against replaying its expand-in
 * animation on every navigation to the tab.
 *
 * The section is wrapped in `AppAnimatedVisibility`, which animates
 * whenever `visible` CHANGES. `DefaultProfileViewModel` is screen-scoped
 * (a fresh instance per `NavBackStackEntry`), so if the watch status is
 * re-derived per screen entry it starts at [WatchAppStatus.Unknown]
 * (section hidden), then flips to the real verdict a frame later
 * (section visible) — an enter animation on every single tab entry.
 *
 * The fix is the process-wide `stateIn` cache inside
 * [ObserveWatchAppStatusUseCase]; these tests pin the properties that
 * make it work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWatchAppStatusUseCaseTest {
    /**
     * THE regression test. A later collector — i.e. the next time the
     * user opens Settings — must see the last known status immediately
     * rather than [WatchAppStatus.Unknown].
     *
     * This is what makes the animation fire only on real changes. It
     * depends on `stateIn` retaining its value after the upstream stops,
     * which holds because `WhileSubscribed`'s `replayExpirationMillis`
     * defaults to `Long.MAX_VALUE`. Passing a finite replay expiration
     * would reset the cache to `initialValue` and fail this test.
     */
    @Test
    fun retainsLastStatusForALaterCollector() =
        runTest {
            val repository = FakeWearCompanionRepository().apply { useColdSource() }
            repository.setWatchAppStatus(WatchAppStatus.InstalledOnWatch())
            val useCase = ObserveWatchAppStatusUseCase(repository, backgroundScope)

            // First Settings entry: collect until the poll result lands.
            val firstEntry = launch { useCase().collect { } }
            runCurrent()
            assertEquals(WatchAppStatus.InstalledOnWatch(), useCase().value)

            // Leave the tab, and wait well past the stop timeout so the
            // upstream is genuinely torn down.
            firstEntry.cancel()
            advanceTimeBy(60_000)
            runCurrent()

            // Second entry reads the cached verdict synchronously. The
            // section is already visible on first composition, so
            // AppAnimatedVisibility has nothing to animate.
            assertEquals(WatchAppStatus.InstalledOnWatch(), useCase().value)
        }

    /**
     * The complement: the poll really does stop when nobody is looking.
     *
     * This is why `WhileSubscribed` was chosen over the `Eagerly` default
     * used by `ComputeButtonHealthDisplayUseCase` — the upstream here is
     * a 15s Play Services polling loop, not a cheap in-memory combine.
     */
    @Test
    fun stopsObservingWhenNoCollectorsRemain() =
        runTest {
            val repository = FakeWearCompanionRepository().apply { useColdSource() }
            repository.setWatchAppStatus(WatchAppStatus.InstalledOnWatch())
            val useCase = ObserveWatchAppStatusUseCase(repository, backgroundScope)

            val entry = launch { useCase().collect { } }
            runCurrent()
            assertEquals(1, repository.observeStartCount)

            entry.cancel()
            advanceTimeBy(60_000)
            runCurrent()

            // Upstream is no longer collected, so a change made while the
            // user is on another tab does not reach the cache. The value
            // stays at the last known verdict (stale-while-revalidate);
            // re-entering restarts the poll and corrects it.
            repository.setWatchAppStatus(WatchAppStatus.NoWatch)
            runCurrent()
            assertEquals(WatchAppStatus.InstalledOnWatch(), useCase().value)

            val reEntry = launch { useCase().collect { } }
            runCurrent()
            assertEquals(2, repository.observeStartCount)
            assertEquals(WatchAppStatus.NoWatch, useCase().value)

            reEntry.cancel()
        }

    /**
     * A genuine change while the user is looking still propagates — the
     * animation is suppressed for repeat entries, not disabled.
     */
    @Test
    fun emitsChangesWhileCollected() =
        runTest {
            val repository = FakeWearCompanionRepository().apply { useColdSource() }
            repository.setWatchAppStatus(WatchAppStatus.WatchNeedsApp)
            val useCase = ObserveWatchAppStatusUseCase(repository, backgroundScope)

            val entry = launch { useCase().collect { } }
            runCurrent()
            assertEquals(WatchAppStatus.WatchNeedsApp, useCase().value)

            // The install completes on the watch.
            repository.setWatchAppStatus(WatchAppStatus.InstalledOnWatch())
            runCurrent()
            assertEquals(WatchAppStatus.InstalledOnWatch(), useCase().value)

            entry.cancel()
        }

    /**
     * Honest starting state: before the very first poll result in a
     * process there is genuinely nothing known, so the first Settings
     * entry after a cold start still animates the section in. That IS a
     * change, so it is the intended behavior — only REPEAT entries are
     * silent.
     */
    @Test
    fun startsUnknownBeforeTheFirstPollResult() =
        runTest {
            val repository = FakeWearCompanionRepository().apply { useColdSource() }
            repository.setWatchAppStatus(WatchAppStatus.InstalledOnWatch())
            val useCase = ObserveWatchAppStatusUseCase(repository, backgroundScope)

            assertEquals(WatchAppStatus.Unknown, useCase().value)
        }
}
