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

import co.touchlab.kermit.Logger
import com.chriscartland.garage.domain.model.AppLoggerKeys
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.DoorEvent
import com.chriscartland.garage.domain.model.DoorUpdateStrategyId
import com.chriscartland.garage.domain.model.FetchError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * How the APP-SCOPED host implements the policies — the swappable half of
 * `DoorUpdateManager`.
 *
 * **This interface is not the definition of a strategy.**
 * [DoorUpdateStrategyId] is: it names the policies and states what each
 * one promises. This is one mechanism for honoring them, suited to a host
 * that lives for the whole process and can be handed a coroutine to
 * cancel. A different host is free to honor the same policy its own way
 * and should not be bent into this shape — `WearHomeViewModel` honors
 * `POLL` with a screen-scoped loop whose cadence depends on ViewModel
 * state this layer cannot even read (`:usecase` cannot import
 * `:viewmodel`). That is two hosts agreeing on a promise, which is the
 * point of the enum, not duplication waiting to be factored out.
 *
 * **A strategy decides WHEN to ask, never what the answer means.** Every
 * implementation ends at `DoorRepository.fetchCurrentDoorEvent()` (or at
 * push, which lands in the same repository through
 * `ReceiveFcmDoorEventUseCase`), so the cache, the Room flow, and every
 * screen behave identically no matter which strategy is running. That is
 * the property that makes swapping one at runtime safe: nothing
 * downstream can tell the difference except in timing.
 *
 * [run] is expected to suspend until cancelled. The manager owns the
 * coroutine and cancels it to swap strategies, so an implementation never
 * needs a `stop()`, a flag, or any teardown of its own — structured
 * concurrency is the teardown.
 */
interface DoorUpdateStrategy {
    /** Which strategy this is. Surfaced in Diagnostics; never branched on. */
    val id: DoorUpdateStrategyId

    /** Run until cancelled. */
    suspend fun run()
}

/**
 * [DoorUpdateStrategyId.PUSH] — do nothing, on purpose.
 *
 * Door events arrive through the platform's push handler
 * (`FCMService.onMessageReceived` / `AppDelegate.didReceiveRemoteNotification`)
 * and are applied by `ReceiveFcmDoorEventUseCase`. That path does not run
 * through this object and never has, so the honest implementation of
 * "the server pushes to us" is a coroutine that waits.
 *
 * It is deliberately NOT responsible for FCM topic subscription:
 * `FcmRegistrationManager` owns that, and its topics also carry
 * button-health and resolved-notification traffic that has nothing to do
 * with which door-update strategy is selected. Swapping strategies must
 * not silently unsubscribe a device from its notifications.
 */
class PushDoorUpdateStrategy : DoorUpdateStrategy {
    override val id = DoorUpdateStrategyId.PUSH

    override suspend fun run() {
        Logger.d { "PushDoorUpdateStrategy: relying on push; no client-side timer" }
        awaitCancellation()
    }
}

/**
 * [DoorUpdateStrategyId.POLL] — refresh on every foreground, then every
 * [intervalMillis] for as long as the app stays visible.
 *
 * Visibility-gated because an interval timer is the one strategy that
 * costs something to leave running, and a backgrounded app has no one to
 * show the result to. On iOS the OS suspends the process anyway; on
 * Android nothing would stop this loop, which is exactly why the gate
 * lives in shared code rather than being left to platform behavior.
 *
 * Failures back off geometrically to [maxBackoffMillis] and reset on the
 * first success, so a server outage costs one request every couple of
 * minutes instead of one every [intervalMillis].
 *
 * **Known and accepted: one duplicate request at cold start.** The
 * platform reports visibility at roughly the moment `AppStartup` runs, so
 * this strategy's foreground fetch and `InitialDoorFetchManager`'s
 * one-shot both ask for the current event within milliseconds. They are
 * not fully redundant — the initial fetch also loads door HISTORY, which
 * no strategy does — and suppressing the overlap would mean one of them
 * knowing about the other's timing, which is a worse trade than one
 * idempotent GET per launch.
 */
class PollDoorUpdateStrategy(
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val appVisibility: AppVisibilityState,
    private val logAppEvent: LogAppEventUseCase,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MS,
    private val maxBackoffMillis: Long = DEFAULT_MAX_BACKOFF_MS,
) : DoorUpdateStrategy {
    override val id = DoorUpdateStrategyId.POLL

    override suspend fun run() {
        DoorRefreshLoop.runWhileVisible(
            visibility = appVisibility.visibility,
            intervalMillis = intervalMillis,
            maxBackoffMillis = maxBackoffMillis,
            firstFetchKey = AppLoggerKeys.FOREGROUND_REFRESH_CURRENT_DOOR,
            repeatFetchKey = AppLoggerKeys.POLL_CURRENT_DOOR,
            logAppEvent = logAppEvent,
            fetch = { fetchCurrentDoorEvent() },
        )
    }

    companion object {
        /**
         * 15 seconds. The ESP32 reports on its own cadence and the server
         * interprets state changes within about a minute, so a tighter
         * interval mostly re-reads a value that has not moved; a looser
         * one makes watching the door open feel broken.
         */
        const val DEFAULT_INTERVAL_MS = 15_000L

        /** 2 minutes — the ceiling for the failure backoff. */
        const val DEFAULT_MAX_BACKOFF_MS = 120_000L
    }
}

/**
 * [DoorUpdateStrategyId.PUSH_WITH_FOREGROUND_REFRESH] — push carries the
 * app while it is away; becoming visible buys exactly one request.
 *
 * The destination for iOS once door pushes actually arrive there. The
 * refresh is not redundancy for its own sake: iOS throttles silent pushes
 * against a per-app budget and suppresses them entirely in Low Power
 * Mode, so the app can return to the foreground holding state that push
 * quietly failed to update. One request at the only moment a user is
 * looking closes that hole without a timer.
 */
class PushWithForegroundRefreshDoorUpdateStrategy(
    private val fetchCurrentDoorEvent: FetchCurrentDoorEventUseCase,
    private val appVisibility: AppVisibilityState,
    private val logAppEvent: LogAppEventUseCase,
    private val maxBackoffMillis: Long = DEFAULT_MAX_BACKOFF_MS,
) : DoorUpdateStrategy {
    override val id = DoorUpdateStrategyId.PUSH_WITH_FOREGROUND_REFRESH

    override suspend fun run() {
        DoorRefreshLoop.runWhileVisible(
            visibility = appVisibility.visibility,
            intervalMillis = null,
            maxBackoffMillis = maxBackoffMillis,
            firstFetchKey = AppLoggerKeys.FOREGROUND_REFRESH_CURRENT_DOOR,
            repeatFetchKey = null,
            logAppEvent = logAppEvent,
            fetch = { fetchCurrentDoorEvent() },
        )
    }

    companion object {
        /**
         * 1 minute. A failed foreground refresh keeps retrying — with no
         * interval to fall back on, giving up would leave the screen
         * showing whatever push last managed to deliver, which is the
         * state this strategy exists to correct. Retrying stops on the
         * first success.
         */
        const val DEFAULT_MAX_BACKOFF_MS = 60_000L
    }
}

/**
 * The visibility gate, the interval, and the backoff — written once so
 * that every strategy that fetches on a schedule cancels, retries, and
 * gives up in exactly the same way. Two strategies differing in one
 * argument beats two strategies differing in a hand-copied `while` loop.
 *
 * `internal`: the parameterization is an implementation detail of the
 * strategies above, not a configuration surface. The supported way to
 * change behavior is to pick a different [DoorUpdateStrategy].
 */
internal object DoorRefreshLoop {
    /**
     * Fetch once each time the app becomes visible, then — when
     * [intervalMillis] is non-null — keep fetching on that interval until
     * the app stops being visible.
     *
     * `collectLatest` is what makes going invisible instant: the in-flight
     * delay (or fetch) is cancelled outright rather than allowed to finish
     * and fire one last request into the background.
     */
    suspend fun runWhileVisible(
        visibility: StateFlow<AppVisibilityState.Visibility>,
        intervalMillis: Long?,
        maxBackoffMillis: Long,
        firstFetchKey: String,
        repeatFetchKey: String?,
        logAppEvent: LogAppEventUseCase,
        fetch: suspend () -> AppResult<DoorEvent, FetchError>,
    ) {
        // Collected as (isVisible, epoch), not a bare Boolean: the epoch is
        // what guarantees a fetch on EVERY return, including one whose
        // departure was conflated away while this collector was suspended —
        // see AppVisibilityState.Visibility. collectLatest then treats each
        // return as a fresh value: cancel whatever was in flight (usually a
        // mid-interval delay) and start over with the immediate fetch.
        visibility.collectLatest { v ->
            if (!v.isVisible) return@collectLatest
            var backoffMillis = 0L
            var first = true
            while (true) {
                val key = if (first) firstFetchKey else repeatFetchKey
                if (key != null) logAppEvent(key)
                backoffMillis = when (val result = fetch()) {
                    is AppResult.Success -> 0L
                    is AppResult.Error -> {
                        Logger.w { "DoorRefreshLoop: fetch failed (${result.error}); backing off" }
                        nextBackoff(backoffMillis, intervalMillis, maxBackoffMillis)
                    }
                }
                first = false
                // No interval and nothing to retry: the foreground refresh
                // is done until the app goes away and comes back.
                if (intervalMillis == null && backoffMillis == 0L) return@collectLatest
                delay(if (backoffMillis > 0L) backoffMillis else intervalMillis!!)
            }
        }
    }

    /**
     * Double the current backoff, capped. Seeds from [intervalMillis]
     * when there is one — a poll that fails should first retry no sooner
     * than it would have polled anyway.
     */
    private fun nextBackoff(
        current: Long,
        intervalMillis: Long?,
        maxMillis: Long,
    ): Long {
        if (maxMillis <= 0L) return 0L
        val seed = intervalMillis ?: DEFAULT_BACKOFF_SEED_MS
        val next = if (current <= 0L) seed else current * 2
        return next.coerceAtMost(maxMillis)
    }

    /** 5 seconds — the first retry delay for a strategy with no interval of its own. */
    private const val DEFAULT_BACKOFF_SEED_MS = 5_000L
}
