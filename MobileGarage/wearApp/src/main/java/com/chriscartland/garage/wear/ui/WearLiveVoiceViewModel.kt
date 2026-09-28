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
     * `isCheckInStale = false` DELIBERATELY: staleness is the server's
     * judgement to make, and it makes it with better information than the
     * watch has.
     *
     * An earlier version of this comment said the watch had no staleness
     * signal to pass. That stopped being true in 0.7.0 — the watch judges
     * check-in staleness now, and the door screen greys when the garage goes
     * quiet. So this is a choice, not a gap, and the maintainer's call
     * (2026-09-27) was to leave the judgement with the server.
     *
     * **Why that is the right division.** The server holds the authoritative
     * door state and the authoritative check-in; the watch holds a mirror
     * that may be minutes old. For "is this command actionable", the server's
     * information is strictly better, so a second opinion computed here could
     * only ever be the worse one. What the CLIENT owes the server is an
     * accurate statement of what it is trying to do — the direction — and
     * `NetworkDoorCommandRepository` sends exactly that (`OPEN` -> "open",
     * `CLOSE` -> "close"), with `VoiceIntent.UNKNOWN` refused before it can
     * reach the wire.
     *
     * Both sides then judge that direction by ONE rule: `VoiceCommandGate`
     * and the server's `DoorCommandGate` are pinned to the same
     * `wire-contracts/doorCommand/verdict_table.json`, so they cannot drift
     * about what a command means.
     *
     * **What this local projection is still for.** It is a fast refusal, not
     * a safety layer — it spares a round trip for commands that are obviously
     * inert, and it keeps the mapper's deny-by-default rules in play: every
     * genuine anomaly (stuck transit, sensor conflict, no event at all)
     * already maps to UNKNOWN, and UNKNOWN refuses every direction. A stale
     * door that slips past it is refused by the server a moment later, which
     * costs one request against a backend the press was about to contact
     * anyway.
     *
     * The gate is additive by construction: `confirmWithServer` can only
     * return a refusal or null, so consulting the server can never turn a
     * locally-refused command into a permitted one.
     */
    fun project(position: DoorPosition?): VoiceDoorState = VoiceDoorStateMapper.project(position, isCheckInStale = false)
}
