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

package com.chriscartland.garage.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared contract for [HomeAlertMapper] (ADR-031). Moved from the Android-only
 * `HomeMapperTest.toHomeAlerts` region so the banner-selection logic is guarded
 * on every platform.
 */
class HomeAlertMapperTest {
    // Every case below that asserts a banner APPEARS passes
    // DataFreshness.STALE, because that is the only verdict under which a
    // freshness banner exists at all. The settle window's own effect — the
    // same inputs producing no banner — is asserted in its own tests at the
    // bottom of this file.

    @Test
    fun cleanStateEmpty() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(emptyList(), alerts)
    }

    @Test
    fun staleOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = true,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(listOf(HomeAlert.Stale), alerts)
    }

    @Test
    fun permissionOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = false,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(1, alerts.size)
        val pm = alerts[0] as HomeAlert.PermissionMissing
        assertEquals(0, pm.attemptCount)
    }

    @Test
    fun permissionAttemptCountPassesThrough() {
        // The mapper passes the count through verbatim; each UI's resolver
        // appends escalation lines at counts 3+, 4+, 5+.
        val firstAttempt = HomeAlertMapper
            .toHomeAlerts(
                isServerUnreachable = false,
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 0,
                freshness = DataFreshness.STALE,
            ).first() as HomeAlert.PermissionMissing
        val manyAttempts = HomeAlertMapper
            .toHomeAlerts(
                isServerUnreachable = false,
                isCheckInStale = false,
                notificationPermissionGranted = false,
                notificationRequestCount = 5,
                freshness = DataFreshness.STALE,
            ).first() as HomeAlert.PermissionMissing
        assertEquals(0, firstAttempt.attemptCount)
        assertEquals(5, manyAttempts.attemptCount)
    }

    @Test
    fun serverUnreachableOnly() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = true,
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(listOf(HomeAlert.ServerUnreachable), alerts)
    }

    @Test
    fun allThreeInDocumentedOrder() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = true,
            isCheckInStale = true,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(3, alerts.size)
        assertTrue(alerts[0] is HomeAlert.Stale, "[0] should be Stale")
        assertTrue(alerts[1] is HomeAlert.PermissionMissing, "[1] should be PermissionMissing")
        assertTrue(alerts[2] is HomeAlert.ServerUnreachable, "[2] should be ServerUnreachable")
    }

    @Test
    fun aHealthyConnectionSaysNothing() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.STALE,
        )
        assertEquals(emptyList(), alerts)
    }

    /**
     * The bug this gate was added for: a warm start on a door whose check-in
     * has aged out used to raise the Stale banner — and its Retry button —
     * during the second it took the return fetch to land. Same inputs as
     * [staleOnly]; the only difference is that the app has just arrived.
     */
    @Test
    fun settlingSuppressesTheStaleBanner() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = true,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.SETTLING,
        )
        assertEquals(emptyList(), alerts)
    }

    /** Same rule for the other Retry-carrying banner. */
    @Test
    fun settlingSuppressesTheServerUnreachableBar() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = true,
            isCheckInStale = false,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.SETTLING,
        )
        assertEquals(emptyList(), alerts)
    }

    /**
     * The permission banner is NOT a freshness statement: a missing
     * notification permission is a standing configuration fact with nothing
     * in flight that could resolve it, so waiting would only delay the fix.
     *
     * This is also this file's positive control. Without it, a
     * `freshness.isSpoken` that had degenerated to always-false would satisfy
     * both suppression tests above and the suite would go green while Home
     * had quietly lost the ability to report anything at all.
     */
    @Test
    fun settlingDoesNotSuppressThePermissionBanner() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = false,
            isCheckInStale = true,
            notificationPermissionGranted = false,
            notificationRequestCount = 0,
            freshness = DataFreshness.SETTLING,
        )
        assertEquals(1, alerts.size)
        assertTrue(alerts[0] is HomeAlert.PermissionMissing, "[0] should be PermissionMissing")
    }

    /**
     * FRESH cannot produce a freshness banner either, whatever the raw flags
     * say — the verdict is the single gate, and these two inputs are already
     * folded into it upstream by `DataFreshnessMapper`. Pins that the mapper
     * reads `freshness` and not `isCheckInStale` directly.
     */
    @Test
    fun freshSuppressesFreshnessBannersToo() {
        val alerts = HomeAlertMapper.toHomeAlerts(
            isServerUnreachable = true,
            isCheckInStale = true,
            notificationPermissionGranted = true,
            notificationRequestCount = 0,
            freshness = DataFreshness.FRESH,
        )
        assertEquals(emptyList(), alerts)
    }
}
