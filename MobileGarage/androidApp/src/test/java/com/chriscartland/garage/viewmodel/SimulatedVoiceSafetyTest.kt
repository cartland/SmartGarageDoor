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

package com.chriscartland.garage.viewmodel

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → Developer → Simulated voice must stay simulated.
 *
 * The phone has two voice surfaces that share everything except their
 * environment: Home is LIVE (a committed command presses the real
 * remote button) and the Settings sheet is a rehearsal against a
 * pretend door. What keeps them apart is that [DefaultProfileViewModel]
 * has no way to reach the real button at all.
 *
 * Asserted over the constructor rather than by observing behaviour,
 * because behaviour only proves the paths a test happens to walk — this
 * fails the moment someone *wires in* a real-door dependency, which is
 * the actual regression to guard. Mirrors
 * `WearVoiceViewModelTest.cannotReachTheRealRemoteButton`, which makes
 * the same promise for the watch.
 *
 * Lives in `:androidApp` because `:viewmodel`'s tests are `commonTest`
 * (Android + iOS), where JVM reflection is unavailable.
 */
class SimulatedVoiceSafetyTest {
    @Test
    fun theSimulatedVoiceSurfaceCannotReachTheRealButton() {
        val forbidden = setOf(
            "PushRemoteButtonUseCase",
            "RemoteButtonRepository",
            "NetworkButtonDataSource",
            "ButtonStateMachine",
            "RemoteButtonVoiceCommandEnvironment",
            "HomeViewModel",
            "DefaultHomeViewModel",
        )
        val dependencies = DefaultProfileViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.simpleName }
            .toSet()
        val violations = dependencies intersect forbidden
        assertTrue(
            "DefaultProfileViewModel must not depend on $violations. The Settings " +
                "voice surface is a simulation; the real remote button is reachable " +
                "only from the Home tab.",
            violations.isEmpty(),
        )
    }
}
