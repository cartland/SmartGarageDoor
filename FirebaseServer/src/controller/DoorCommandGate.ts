/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
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
 */

import { SensorEventType } from '../model/SensorEvent';

/**
 * Whether a directional door command ("open" / "close") is actionable right
 * now. Pure: no Firestore, no clock, no config — every input is a parameter,
 * so the whole decision table is unit-testable and the HTTP handler above it
 * stays a thin read-and-answer.
 *
 * ## Why the server has this at all
 *
 * The remote button is a TOGGLE: one press, no direction. Everything that has
 * ever decided "should I press it?" has done so on the client — first the
 * two-tap confirmation, then the voice gate. That is backwards for this repo,
 * whose organizing principle is that the server owns interpretation and
 * clients stay simple (see CLAUDE.md § Server-Centric Design). It also means
 * the rule is currently implemented once per platform and can drift: the phone
 * refuses on a stale check-in, the watch has no staleness signal to refuse on.
 *
 * Deciding it here fixes both. The client sends what the user asked for and
 * the server answers whether that is a thing the door can do.
 *
 * ## This module cannot press the button
 *
 * It has no imports beyond the sensor-event enum, so it has no route to
 * `RemoteButtonCommandDatabase` — the collection the ESP32 polls — and no way
 * to reach one. A verdict is a value; acting on it is somebody else's job, and
 * today nobody does. `HttpDoorCommandTest` pins that.
 */

/** What the user asked for. The wire values the client sends. */
export enum DoorCommand {
  Open = 'OPEN',
  Close = 'CLOSE',
}

/** Returns the command for a wire value, or null if it is not one. */
export function parseDoorCommand(raw: unknown): DoorCommand | null {
  if (typeof raw !== 'string') return null;
  const upper = raw.toUpperCase();
  if (upper === DoorCommand.Open) return DoorCommand.Open;
  if (upper === DoorCommand.Close) return DoorCommand.Close;
  return null;
}

/**
 * The door as the gate sees it — a projection of the richer sensor-event
 * model, because the gate only needs "is it settled, and which side", plus
 * [Stuck] for the one unsettled case where a direction still applies.
 *
 * Mirrors `VoiceDoorState` in the Kotlin client
 * (`MobileGarage/usecase/.../VoiceCommandController.kt`). The two are kept in
 * step by the shared fixtures in `wire-contracts/doorCommand/`.
 */
export enum DoorGateState {
  Closed = 'CLOSED',
  Open = 'OPEN',
  Moving = 'MOVING',
  Stuck = 'STUCK',
  Unknown = 'UNKNOWN',
}

/** Why a command was refused. `null` rejection means it was accepted. */
export enum DoorCommandRejection {
  AlreadyOpen = 'ALREADY_OPEN',
  AlreadyClosed = 'ALREADY_CLOSED',
  DoorMoving = 'DOOR_MOVING',
  DoorStuck = 'DOOR_STUCK',
  DoorStateUnknown = 'DOOR_STATE_UNKNOWN',
}

/**
 * How long a device check-in stays trustworthy. Mirrors the client's
 * `CheckInStatusMapper.STALE_THRESHOLD_SECONDS` (11 min) — if you change one,
 * change both; `wire-contracts/doorCommand/` is where the agreement is pinned.
 *
 * Past this, the last reported position may no longer describe the real door,
 * and acting on it risks the wrong-direction hazard: the cache says closed, the
 * door is really open, and "open" would actually close it on someone.
 */
export const CHECK_IN_STALE_THRESHOLD_SECONDS = 11 * 60;

/**
 * Project a raw sensor-event type onto the gate's view.
 *
 * Deny-by-default, the standing principle: it is fine to incorrectly refuse,
 * never fine to incorrectly act. Clean terminal positions are actionable,
 * clean transits are Moving, a transit past its deadline is Stuck, and
 * everything else is Unknown.
 *
 * `OpenMisaligned` is Open, not an anomaly: the server emits it only when the
 * closed sensor reads NOT-closed with a flaky open sensor, so the door is
 * definitively not closed. `OpeningTooLong` / `ClosingTooLong` are Stuck for
 * the same family of reason — `EventInterpreter` reaches them only after
 * Closed, Open, and ErrorSensorConflict are each ruled out, so the door is
 * definitively partway. `ErrorSensorConflict` stays Unknown because its
 * sensors actively disagree, which leaves no position to reason from at all.
 */
export function projectDoorState(type: SensorEventType | null | undefined): DoorGateState {
  switch (type) {
    case SensorEventType.Closed:
      return DoorGateState.Closed;
    case SensorEventType.Open:
    case SensorEventType.OpenMisaligned:
      return DoorGateState.Open;
    case SensorEventType.Opening:
    case SensorEventType.Closing:
      return DoorGateState.Moving;
    case SensorEventType.OpeningTooLong:
    case SensorEventType.ClosingTooLong:
      return DoorGateState.Stuck;
    default:
      // ErrorSensorConflict, Unknown, an unrecognized string from a future
      // firmware, and no event at all.
      return DoorGateState.Unknown;
  }
}

/**
 * The decision. `null` means the command is actionable.
 *
 * Open is accepted only from Closed; close only from Open — with the one
 * exception that gives Stuck its reason to exist, where close is accepted and
 * open is not. A door that tried to open and never arrived is not going to be
 * made "more open" by another press, and everything here leans toward closed
 * when it is unsure.
 */
export function rejectionFor(
  state: DoorGateState,
  command: DoorCommand,
): DoorCommandRejection | null {
  switch (state) {
    case DoorGateState.Moving:
      return DoorCommandRejection.DoorMoving;
    case DoorGateState.Unknown:
      return DoorCommandRejection.DoorStateUnknown;
    case DoorGateState.Stuck:
      return command === DoorCommand.Open ? DoorCommandRejection.DoorStuck : null;
    case DoorGateState.Open:
      return command === DoorCommand.Open ? DoorCommandRejection.AlreadyOpen : null;
    case DoorGateState.Closed:
      return command === DoorCommand.Close ? DoorCommandRejection.AlreadyClosed : null;
  }
}

/** The full answer, including what it was judged against. */
export interface DoorCommandVerdict {
  command: DoorCommand;
  accepted: boolean;
  rejection: DoorCommandRejection | null;
  /** The projected state the decision used. */
  doorState: DoorGateState;
  /** The raw sensor-event type behind the projection, for diagnosis. */
  sensorEventType: string | null;
  /** True when the reading was too old to trust, which forces Unknown. */
  checkInStale: boolean;
  /** Age of the reading in seconds, or null when there was no reading. */
  checkInAgeSeconds: number | null;
}

/**
 * Judge one command against one door reading.
 *
 * A stale check-in forces Unknown before the position is even consulted,
 * which is what the phone already does locally and what the watch cannot do
 * for itself. Deciding it here is the reason both surfaces can stop trying.
 *
 * @param event the current sensor event, or null/undefined if there is none
 * @param nowSeconds server time, passed in so the decision stays pure
 */
export function judgeDoorCommand(input: {
  event: { type?: string; checkInTimestampSeconds?: number } | null | undefined;
  command: DoorCommand;
  nowSeconds: number;
  staleThresholdSeconds?: number;
}): DoorCommandVerdict {
  const threshold = input.staleThresholdSeconds ?? CHECK_IN_STALE_THRESHOLD_SECONDS;
  const rawType = input.event?.type;
  const checkIn = input.event?.checkInTimestampSeconds;
  const checkInAgeSeconds = typeof checkIn === 'number' ? input.nowSeconds - checkIn : null;
  // No check-in at all is not "fresh by default" — an event with no timestamp
  // tells us nothing about when it was true, so it is treated as stale.
  const checkInStale = checkInAgeSeconds === null || checkInAgeSeconds > threshold;

  const projected = projectDoorState(rawType as SensorEventType | undefined);
  const doorState = checkInStale ? DoorGateState.Unknown : projected;
  const rejection = rejectionFor(doorState, input.command);
  return {
    command: input.command,
    accepted: rejection === null,
    rejection,
    doorState,
    sensorEventType: typeof rawType === 'string' ? rawType : null,
    checkInStale,
    checkInAgeSeconds,
  };
}
