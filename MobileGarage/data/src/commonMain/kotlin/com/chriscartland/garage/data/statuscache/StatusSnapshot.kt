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

package com.chriscartland.garage.data.statuscache

/**
 * One persisted last-known value with its freshness metadata
 * (see `MobileGarage/docs/STATUS_CACHE_PLAN.md`).
 *
 * Two timestamps, two purposes:
 *  - [fetchedAtEpochSeconds] — when the payload VALUE was produced by
 *    an accepted server write. Consumers use it to decide whether a
 *    cold-start fetch can be skipped (fetch-TTL).
 *  - [confirmedAtEpochSeconds] — when a server round-trip last
 *    confirmed the value as still true. A successful revalidate that
 *    returns an identical payload refreshes this WITHOUT rewriting the
 *    value. Display-TTLs key off this one: without the distinction, a
 *    stable device whose revalidates are rejected as value-equal would
 *    look permanently stale.
 *
 * [accountEmail] is set on per-user entries so hydration can refuse a
 * snapshot written by a different account (see plan §D4).
 */
data class StatusSnapshot<T>(
    val payload: T,
    val fetchedAtEpochSeconds: Long,
    val confirmedAtEpochSeconds: Long,
    val accountEmail: String? = null,
) {
    /** Age of the payload value; see [ageSeconds] for the skew rule. */
    fun fetchedAgeSeconds(nowEpochSeconds: Long): Long = ageSeconds(fetchedAtEpochSeconds, nowEpochSeconds)

    /** Age of the last server confirmation; see [ageSeconds] for the skew rule. */
    fun confirmedAgeSeconds(nowEpochSeconds: Long): Long = ageSeconds(confirmedAtEpochSeconds, nowEpochSeconds)

    companion object {
        /**
         * A timestamp this far in the future is tolerated as clock
         * jitter (age 0). Beyond it the snapshot is forced stale —
         * without this rule, a backwards wall-clock correction would
         * leave a future-stamped snapshot "fresh" (suppressing
         * revalidation) until the clock caught back up.
         */
        const val CLOCK_SKEW_TOLERANCE_SECONDS: Long = 60L

        internal fun ageSeconds(
            timestampEpochSeconds: Long,
            nowEpochSeconds: Long,
        ): Long =
            if (timestampEpochSeconds > nowEpochSeconds + CLOCK_SKEW_TOLERANCE_SECONDS) {
                Long.MAX_VALUE
            } else {
                maxOf(0L, nowEpochSeconds - timestampEpochSeconds)
            }
    }
}
