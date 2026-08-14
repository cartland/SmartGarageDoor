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

import androidx.lifecycle.viewModelScope
import com.chriscartland.garage.domain.coroutines.DispatcherProvider
import com.chriscartland.garage.domain.model.DoorPosition
import com.chriscartland.garage.usecase.ButtonAckToken
import com.chriscartland.garage.usecase.CheckDoorCommandUseCase
import com.chriscartland.garage.usecase.ClassifyVoiceIntentUseCase
import com.chriscartland.garage.usecase.ObserveDoorEventsUseCase
import com.chriscartland.garage.usecase.PushRemoteButtonUseCase
import com.chriscartland.garage.usecase.RemoteButtonVoiceCommandEnvironment
import com.chriscartland.garage.usecase.VoiceDoorState
import com.chriscartland.garage.usecase.VoiceDoorStateMapper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The watch's LIVE voice surface: a committed command presses the REAL garage
 * button, against the REAL observed door.
 *
 * This is the whole feature rather than a demonstration of it, so it earns the
 * same protections the hold-to-confirm button has, and gets them from exactly
 * the same places:
 *
 *  - **Auth.** [PushRemoteButtonUseCase] refuses before touching the network
 *    unless the session is authenticated (ADR-027).
 *  - **The grammar.** Only a HIGH-confidence imperative arms anything; a
 *    sentence merely *about* the door is refused.
 *  - **The door gate, twice.** [LiveVoiceDoor] projects the real door, so a
 *    command that contradicts it ("open" while open, anything while moving,
 *    anything while the state is unknown) never arms — and the controller
 *    re-checks at the moment of commit, so a door that moved during the
 *    countdown cancels the press instead of completing it.
 *  - **The cancel window.** Three seconds, the controller's maximum, during
 *    which a tap anywhere on the screen calls it off.
 *
 * Rehearse it without consequences via Settings → Simulated voice, which runs
 * this same loop against a pretend door ([WearSimulatedVoiceViewModel]).
 */
class WearLiveVoiceViewModel(
    classifyVoiceIntent: ClassifyVoiceIntentUseCase,
    observeDoorEvents: ObserveDoorEventsUseCase,
    pushRemoteButton: PushRemoteButtonUseCase,
    checkDoorCommand: CheckDoorCommandUseCase,
    dispatchers: DispatcherProvider,
    appVersion: String,
) : WearVoiceViewModel(
        classifyVoiceIntent = classifyVoiceIntent,
        dispatchers = dispatchers,
        environmentFactory = { scope ->
            RemoteButtonVoiceCommandEnvironment(
                doorState = observeDoorEvents
                    .current()
                    .map { event -> LiveVoiceDoor.project(event?.doorPosition) }
                    // Eagerly, and seeded from the cached value below, so the
                    // very first utterance after opening the screen is gated
                    // against the door we already know about. A cold Flow would
                    // answer UNKNOWN until its first emission arrived, which is
                    // a refusal the user cannot explain — the hero screen one
                    // swipe away is showing the door plainly.
                    .stateIn(
                        scope = scope,
                        started = SharingStarted.Eagerly,
                        initialValue = LiveVoiceDoor.project(
                            observeDoorEvents.current().value?.doorPosition,
                        ),
                    ),
                pushRemoteButton = pushRemoteButton,
                // The watch gains the most from this: the server judges
                // check-in staleness, which is the one gate the watch has
                // never been able to apply for itself (LiveVoiceDoor passes
                // isCheckInStale = false because no such signal exists here).
                checkDoorCommand = checkDoorCommand,
                createButtonAckToken = {
                    // The `-voice` marker rides in the appVersion slot so server
                    // logs can tell a spoken press from a held one. The server
                    // compares the token for ack equality only, so the format is
                    // opaque to it. Mirrors the phone's Home wiring.
                    ButtonAckToken.create(
                        currentTimeMillis = System.currentTimeMillis(),
                        appVersion = "$appVersion-voice",
                    )
                },
            )
        },
    ) {
    private val _doorStartedMoving = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Fires when the real door begins to move — the cue to get out of the way.
     *
     * The voice screen is a means, not a destination: you go there to say a
     * sentence, and the thing you actually wanted to see is the door. Once it
     * starts moving, this screen is a microphone sitting on top of the
     * animation the user asked for. So the app leaves, and `WearApp` sends the
     * user back to the door.
     *
     * **A transition, not a state.** Emitted only when the door *becomes*
     * MOVING, so arriving here while it is already moving does not bounce you
     * straight back out — which would make the mic unreachable for the whole
     * of a transit, exactly when someone might want to reverse it.
     *
     * **Regardless of who moved it.** A door opened from the wall button or the
     * phone is just as worth watching as one this watch opened, and a voice
     * command could not have been committed against a moving door anyway.
     *
     * Live only, and it exists on this class rather than the base for the
     * reason the two classes exist at all: leaving the *rehearsal* because a
     * pretend door moved would drop the user onto the real door, which is the
     * one screen a simulation must never hand them.
     */
    val doorStartedMoving: Flow<Unit> = _doorStartedMoving

    init {
        viewModelScope.launch(dispatchers.default) {
            doorState
                // Whatever the door was on arrival is context, not news.
                .drop(1)
                .filter { it == VoiceDoorState.MOVING }
                // `replay = 0`: a dismissal nobody is around to act on is
                // stale by the time anyone is, and the door screen is where a
                // returning user lands anyway.
                .collect { _doorStartedMoving.tryEmit(Unit) }
        }
    }
}

/** How the live surface projects the real door into the gate's view. */
internal object LiveVoiceDoor {
    /**
     * `isCheckInStale = false` because the watch has no staleness signal to
     * pass: `CheckInStalenessManager` is phone-only, and the watch's door
     * mirror is refreshed by foreground polling rather than by push.
     *
     * This is NOT a claim that the reading is fresh. It is the absence of the
     * extra suspicion the phone layers on top of the mapper's own
     * deny-by-default rules, which still apply in full: every genuine anomaly
     * (stuck transit, sensor conflict, no event at all) already maps to
     * UNKNOWN, and UNKNOWN refuses every direction.
     *
     * The residual gap is a door whose last known position is clean but whose
     * device has stopped reporting. Voice inherits exactly the exposure the
     * hold-to-confirm button already has there, which is the right bar: both
     * act on the same mirror, so neither should be more trusting than the
     * other. Closing it means giving the watch a staleness signal, which is a
     * change to the door surface as a whole and not to voice.
     */
    fun project(position: DoorPosition?): VoiceDoorState = VoiceDoorStateMapper.project(position, isCheckInStale = false)
}
