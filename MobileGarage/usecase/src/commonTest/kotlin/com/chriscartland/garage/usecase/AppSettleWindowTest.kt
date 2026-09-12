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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window's own derivation. Consumers get `FakeAppSettleWindow` instead;
 * this suite is the only place the real timing is asserted.
 *
 * There is no fake visibility source: [AppVisibilityState] is a concrete class
 * whose entire API is `setVisible`, so these tests drive the real one. That is
 * a more honest simulation of backgrounding than any fake, and it is the same
 * choice `DoorUpdateStrategyTest` makes for the same reason.
 */
class AppSettleWindowTest {
    private val window = AppSettleWindow.SETTLE_WINDOW_MILLIS

    private fun TestScope.newWindow(
        visibility: AppVisibilityState,
        scope: CoroutineScope,
    ): DefaultAppSettleWindow =
        DefaultAppSettleWindow(
            appVisibilityState = visibility,
            scope = scope,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    /**
     * The first-frame guarantee. A screen composing before the platform has
     * reported anything must read "still settling", or a cold start renders a
     * frame of the full alarm presentation — which is the flash this whole
     * feature exists to remove.
     */
    @Test
    fun startsSettlingBeforeThePlatformHasSaidAnything() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            assertEquals(true, settleWindow.isSettling.value)

            settleWindow.start()
            runCurrent()
            // The starting Visibility(false, 0) that every collector sees must
            // NOT be read as "the window closed": nothing has become visible,
            // so nothing has settled.
            assertEquals(true, settleWindow.isSettling.value)
        }

    @Test
    fun becomingVisibleOpensTheWindowAndItClosesAfterFiveSeconds() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            runCurrent()
            assertEquals(true, settleWindow.isSettling.value)

            advanceTimeBy(window - 1)
            runCurrent()
            assertEquals(
                true,
                settleWindow.isSettling.value,
                "must still be settling one millisecond before the deadline",
            )

            advanceTimeBy(2)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)
        }

    /**
     * The warm-start case, which is the one that was actually reported: come
     * back to an app that has been closed for an hour and the screen gets a
     * fresh grace period, not the leftover "settled" from last time.
     */
    @Test
    fun aReturnReopensTheWindow() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            advanceTimeBy(window + 1)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)

            visibility.setVisible(false)
            runCurrent()
            visibility.setVisible(true)
            runCurrent()
            assertEquals(true, settleWindow.isSettling.value, "a return must reopen the window")

            advanceTimeBy(window + 1)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)
        }

    /**
     * The scar test, inherited from [AppVisibilityState]'s epoch.
     *
     * A collector suspended across BOTH writes of a fast background/foreground
     * round trip sees only the conflated final value. Against a plain
     * `StateFlow<Boolean>` that value equals the one it already had,
     * `collectLatest` never restarts, and the returning user gets no grace
     * period at all — the banner shown a moment ago simply stays up. The epoch
     * makes the conflated value distinct, so the window reopens.
     *
     * Deliberately written with NO `runCurrent()` between the two `setVisible`
     * calls; that is exactly what makes them conflate.
     */
    @Test
    fun aReturnThatConflatesWithItsDepartureStillReopensTheWindow() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            advanceTimeBy(window + 1)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)

            visibility.setVisible(false)
            visibility.setVisible(true)
            runCurrent()

            assertEquals(true, settleWindow.isSettling.value)
        }

    /**
     * Hiding is not an answer. The window is about what a LOOKING user is
     * shown, and freezing rather than closing is what lets the initial `true`
     * survive to the first frame.
     */
    @Test
    fun goingAwayMidWindowDoesNotCloseIt() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            runCurrent()
            visibility.setVisible(false)
            advanceTimeBy(window * 10)
            runCurrent()
            assertEquals(true, settleWindow.isSettling.value)
        }

    /** Idempotent, so a second `AppStartup.run()` cannot stack two timers. */
    @Test
    fun startIsIdempotent() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            settleWindow.start()
            settleWindow.start()
            visibility.setVisible(true)
            advanceTimeBy(window + 1)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)
        }
}
