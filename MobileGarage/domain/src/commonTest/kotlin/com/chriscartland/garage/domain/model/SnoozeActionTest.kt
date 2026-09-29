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

package com.chriscartland.garage.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * `SnoozeAction.of` is the one mapping from a snooze request's typed result
 * to the outcome a surface words — the Settings sheet on both phones and the
 * warning's Snooze action all read it. Pin every arm, and that the arms are
 * not all the same.
 */
class SnoozeActionTest {
    @Test
    fun aSnoozeThatTookIsSetUntilItsEnd() {
        assertEquals(
            SnoozeAction.Succeeded.Set(1_700_003_600),
            SnoozeAction.of(AppResult.Success(SnoozeState.Snoozing(1_700_003_600))),
        )
    }

    @Test
    fun aClearedOrStillLoadingStateReadsAsCleared() {
        assertEquals(SnoozeAction.Succeeded.Cleared, SnoozeAction.of(AppResult.Success(SnoozeState.NotSnoozing)))
        assertEquals(SnoozeAction.Succeeded.Cleared, SnoozeAction.of(AppResult.Success(SnoozeState.Loading)))
    }

    @Test
    fun everyErrorHasItsOwnFailure() {
        assertEquals(SnoozeAction.Failed.NotAuthenticated, SnoozeAction.of(AppResult.Error(ActionError.NotAuthenticated)))
        assertEquals(SnoozeAction.Failed.MissingData, SnoozeAction.of(AppResult.Error(ActionError.MissingData)))
        assertEquals(SnoozeAction.Failed.NetworkError, SnoozeAction.of(AppResult.Error(ActionError.NetworkFailed)))
        assertEquals(SnoozeAction.Failed.EventChanged, SnoozeAction.of(AppResult.Error(ActionError.SnoozeEventChanged)))
        // The snooze repository never answers Forbidden, so it is worded as
        // the generic failure rather than as a refusal nobody can produce.
        assertEquals(SnoozeAction.Failed.NetworkError, SnoozeAction.of(AppResult.Error(ActionError.Forbidden)))
    }

    @Test
    fun theOutcomesAreNotAllTheSame() {
        // Positive control for the equalities above: a mapper that returned
        // one action for everything would satisfy none of these.
        assertNotEquals(
            SnoozeAction.of(AppResult.Error(ActionError.NotAuthenticated)),
            SnoozeAction.of(AppResult.Error(ActionError.SnoozeEventChanged)),
        )
        assertNotEquals(
            SnoozeAction.of(AppResult.Success(SnoozeState.Snoozing(1))),
            SnoozeAction.of(AppResult.Success(SnoozeState.NotSnoozing)),
        )
    }
}
