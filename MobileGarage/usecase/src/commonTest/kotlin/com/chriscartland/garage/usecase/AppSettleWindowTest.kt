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

    /**
     * Pins the promise to a NUMBER, not just to itself.
     *
     * Every other test here references [AppSettleWindow.SETTLE_WINDOW_MILLIS]
     * symbolically, and `RelayFallbackAuthBridgeTest` pins the two constants
     * only to each other — so raising this to five minutes would keep the
     * whole repo green while the app sat silent for five minutes on every
     * launch. "Five seconds" is the thing that was actually asked for, so
     * five seconds is what gets asserted.
     */
    @Test
    fun theWindowIsFiveSeconds() {
        assertEquals(5_000L, AppSettleWindow.SETTLE_WINDOW_MILLIS)
    }

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
            // Advance well past the window BEFORE asserting. Without this the
            // assertion passed for the wrong reason — the 5s delay simply had
            // not elapsed yet, so it held even with the `isVisible` guard
            // deleted. Running the clock on is what makes it actually prove
            // that the starting `Visibility(false, 0)` is not read as "the
            // window closed": nothing has become visible, so nothing settles,
            // however long we wait.
            advanceTimeBy(window * 2)
            runCurrent()
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
     * THE safety property, and the one the first implementation got wrong:
     * the window may only ever DELAY a warning, never suppress it without
     * bound.
     *
     * A `delay(5_000)` restarted by `collectLatest` on every return only ever
     * closed after five *uninterrupted* visible seconds — and nothing
     * guarantees the app gets five uninterrupted seconds. Someone glancing at
     * the watch for three seconds at a time (`onVisible`/`onHidden` are
     * `ON_START`/`ON_STOP`, so every wrist-down is a departure) restarted the
     * countdown on every glance and would NEVER have been told the garage was
     * unreachable. Same on iOS, where a notification banner reports
     * `.inactive`.
     *
     * So screen time accumulates. Two three-second glances add up to six, and
     * the second glance speaks.
     */
    @Test
    fun screenTimeAccumulatesAcrossGlancesSoShortLooksStillReachStale() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()

            // Glance one: three seconds, then the wrist drops.
            visibility.setVisible(true)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(true, settleWindow.isSettling.value, "3s is not yet 5s")
            visibility.setVisible(false)
            runCurrent()

            // A long gap changes nothing — the countdown is paused, not reset.
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(true, settleWindow.isSettling.value)

            // Glance two: the remaining two seconds are all that is owed.
            visibility.setVisible(true)
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(
                false,
                settleWindow.isSettling.value,
                "accumulated screen time must close the window; a glance pattern must not hold it open forever",
            )
        }

    /**
     * The other half of the same property: a departure must not ADD time
     * either. Being away is neither progress nor a penalty.
     */
    @Test
    fun timeSpentAwayDoesNotCountTowardTheWindow() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            advanceTimeBy(1_000)
            runCurrent()
            visibility.setVisible(false)
            runCurrent()

            advanceTimeBy(window * 10)
            runCurrent()
            assertEquals(
                true,
                settleWindow.isSettling.value,
                "hours in the background must not settle a window nobody was looking at",
            )
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

    /**
     * Idempotent, so a second `AppStartup.run()` cannot stack two timers.
     *
     * The second `start()` deliberately happens AFTER the window has closed,
     * which is the only place a duplicate collector is observable: it would be
     * replayed the current `Visibility(true, 1)` and reopen the window behind
     * the app's back. An earlier version of this test called `start()` three
     * times BEFORE the first `setVisible` and was vacuous — the extra
     * collectors then ran in lockstep with the first (same value, same 5s
     * delay, all writing `false` at the same instant), so deleting the guard
     * in `DefaultAppSettleWindow.start()` left the whole suite green.
     * Empirically verified: the guard was removed and every test still passed.
     */
    @Test
    fun startIsIdempotent() =
        runTest {
            val visibility = AppVisibilityState()
            val settleWindow = newWindow(visibility, backgroundScope)
            settleWindow.start()
            visibility.setVisible(true)
            advanceTimeBy(window + 1)
            runCurrent()
            assertEquals(false, settleWindow.isSettling.value)

            settleWindow.start()
            runCurrent()
            assertEquals(
                false,
                settleWindow.isSettling.value,
                "a second start must not reopen a window that has already closed",
            )
        }
}
