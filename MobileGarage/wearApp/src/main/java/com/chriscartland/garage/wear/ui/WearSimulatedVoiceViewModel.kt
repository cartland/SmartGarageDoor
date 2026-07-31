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

import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.SimulatedVoiceCommandEnvironment

/**
 * Settings → Simulated voice: the live voice surface, rehearsed against a
 * pretend door.
 *
 * ## This can never operate the real garage door
 *
 * It runs the same [WearVoiceViewModel] loop the live surface does — same
 * classifier, same door gate, same cancel window, same haptics — and differs in
 * exactly one thing: the environment it is given is an in-memory door. The
 * safety property is *structural*, not a runtime check, and rests on three
 * things that are each pinned by a test:
 *
 *  1. This class has no reference to the remote button whatsoever: no
 *     `PushRemoteButtonUseCase`, no `RemoteButtonRepository`, no
 *     `ButtonStateMachine`. `WearSimulatedVoiceViewModelTest` asserts that over
 *     the constructor by reflection, so wiring one in later fails a test rather
 *     than quietly shipping.
 *  2. It hands the base class a [SimulatedVoiceCommandEnvironment] literally,
 *     here, with nothing injected — so there is no call site that could pass
 *     something else and no binding that could be swapped
 *     (`WearComponentGraphTest`).
 *  3. That environment's `pressButton` touches nothing but its own in-memory
 *     StateFlow (`SimulatedVoiceCommandEnvironmentTest`, in `:usecase`).
 *
 * ## Why it exists
 *
 * To see the real interaction without the real consequence: the recognizer
 * round-trip, the countdown, how a refusal reads on a tiny screen — all of it
 * exercisable at a desk, repeatedly, without a garage door moving. The pretend
 * door also *reacts*, which is what makes the refusal paths reachable at all
 * (open it, and asking again is refused as already-open; speak mid-transit and
 * the gate refuses that too). Reaching those states on the live surface would
 * mean genuinely cycling the door.
 *
 * The screen it drives says so in four independent ways — see
 * [VoiceSurfaceMode.Simulated].
 */
class WearSimulatedVoiceViewModel(
    classifyVoiceIntent: ClassifyVoiceIntentUseCase,
    dispatchers: DispatcherProvider,
) : WearVoiceViewModel(
        classifyVoiceIntent = classifyVoiceIntent,
        dispatchers = dispatchers,
        // Constructed here rather than injected, deliberately: a DI binding is a
        // thing that can be re-bound, and this must not be re-bindable.
        environmentFactory = { scope -> SimulatedVoiceCommandEnvironment(scope) },
    ) {
    /**
     * The pretend door, at its own type, so it can be placed directly.
     *
     * The cast cannot fail: the only thing this class ever hands the base is
     * the literal above, and there is no parameter through which anything else
     * could arrive. Used by tests to reach gate paths that a real door would
     * have to be physically cycled into (moving, or reporting nothing at all).
     */
    internal val demoDoor: SimulatedVoiceCommandEnvironment
        get() = environment as SimulatedVoiceCommandEnvironment
}
