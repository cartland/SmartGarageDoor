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

package com.chriscartland.garage.wear.ui

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the settings account row is allowed to claim.
 *
 * The interesting case is the one the type system will not catch: `AuthState`
 * has three cases and the obvious implementation has two branches
 * (`is Authenticated` / `else`). That compiles, passes review, and is wrong
 * during exactly the window this app spends resolving a session relayed from
 * the paired phone.
 */
class WearSettingsMappersTest {
    /** A signed-in user is named by their address, not by a canned phrase. */
    @Test
    fun aSignedInAccountIsNamedByItsAddress() {
        val account = WearSettingsMappers.account(
            AuthState.Authenticated(
                User(name = DisplayName("Real User"), email = Email("real@example.com")),
            ),
        )
        assertEquals(AccountDisplay.SignedIn("real@example.com"), account)
    }

    /**
     * The load-bearing one: "we have not found out yet" must NOT render as
     * "Not signed in".
     *
     * Every cold start passes through `Unknown` — on this watch that means
     * waiting on the phone relay, which is visibly not instant. Folding it into
     * the signed-out branch would make the account row assert, for that whole
     * window, that nobody is signed in. That is a false statement on the one
     * surface whose job is to say which account operates the garage, and it
     * would also contradict the hero screen, which already distinguishes the
     * two ("Checking sign-in…").
     */
    @Test
    fun stillResolvingIsNotReportedAsSignedOut() {
        assertEquals(AccountDisplay.Resolving, WearSettingsMappers.account(AuthState.Unknown))
        assertEquals(
            AccountDisplay.SignedOut,
            WearSettingsMappers.account(AuthState.Unauthenticated),
        )
    }

    /** And the two copy cases really do say different things. */
    @Test
    fun theTwoPlaceholdersAreDistinctStrings() {
        assertEquals(
            R.string.settings_account_resolving,
            WearSettingsMappers.placeholderFor(AccountDisplay.Resolving),
        )
        assertEquals(
            R.string.settings_account_signed_out,
            WearSettingsMappers.placeholderFor(AccountDisplay.SignedOut),
        )
    }

    /**
     * A real address never has a placeholder string standing in for it.
     *
     * `placeholderFor` has to be total over the sealed type, so it needs some
     * answer for the signed-in case. The answer must not be a string that could
     * be shown *instead of* the address — if a future refactor lost the
     * data/copy split in the Composable, this pins the fallback to the harmless
     * one rather than to something that leaks the address through a translated
     * resource.
     */
    @Test
    fun aRealAddressIsNeverStoodInForByAResolvingMessage() {
        assertEquals(
            R.string.settings_account_signed_out,
            WearSettingsMappers.placeholderFor(AccountDisplay.SignedIn("real@example.com")),
        )
    }
}
