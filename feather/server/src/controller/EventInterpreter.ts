/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
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

import {
  SensorEvent, SensorEventType,
  Unknown, ErrorSensorConflict, Closed, Closing, ClosingTooLong, Open, Opening, OpeningTooLong
} from '../model/SensorEvent';

import { SensorSnapshot } from '../model/SensorSnapshot';


const TOO_LONG_DURATION_SECONDS = 60;
const TOO_LONG_OPEN_SECONDS = 15 * 60;

export function getNewEventOrNull(oldEvent: SensorEvent, sensorSnapshot: SensorSnapshot, timestampSeconds: number): SensorEvent {
  const NOT_SET = 'NOT_SET';
  const CLOSED = 'CLOSED';
  const NOT_CLOSED = 'NOT_CLOSED';
  const OPEN = 'OPEN';
  const NOT_OPEN = 'NOT_OPEN';
  let closedSensor = NOT_SET;
  if (sensorSnapshot.sensorA === '0') {
    closedSensor = CLOSED;
  } else if (sensorSnapshot.sensorA === '1') {
    closedSensor = NOT_CLOSED;
  }
  let openSensor = NOT_SET;
  if (sensorSnapshot.sensorB === '0') {
    openSensor = OPEN;
  } else if (sensorSnapshot.sensorB === '1') {
    openSensor = NOT_OPEN;
  }
  if (!oldEvent) {
    // ErrorSensorConflict.
    if (closedSensor === CLOSED && openSensor === OPEN) {
      return ErrorSensorConflict(timestampSeconds);
    }
    // Closed.
    if (closedSensor === CLOSED && openSensor !== OPEN) {
      return Closed(timestampSeconds);
    }
    // Open.
    if (closedSensor !== CLOSED && openSensor === OPEN) {
      return Open(timestampSeconds);
    }
    // Unknown.
    return Unknown(timestampSeconds);
  }
  const oldEventDurationSeconds = timestampSeconds - oldEvent.timestampSeconds;
  switch (oldEvent.type) {
    case SensorEventType.Unknown:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.ErrorSensorConflict:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return null; // No change.
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      // Unknown.
      return Unknown(timestampSeconds);
    case SensorEventType.Closed:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return null; // No change.
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      // Opening.
      if (closedSensor === NOT_CLOSED) {
        return Opening(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.Closing:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      // ClosingTooLong.
      if (oldEventDurationSeconds > TOO_LONG_DURATION_SECONDS) {
        return ClosingTooLong(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.ClosingTooLong:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.Open:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return null; // No change.
      }
      // Closing.
      if (openSensor === NOT_OPEN) {
        return Closing(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.Opening:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      // OpeningTooLong.
      if (oldEventDurationSeconds > TOO_LONG_DURATION_SECONDS) {
        return OpeningTooLong(timestampSeconds);
      }
      return null; // No change.
    case SensorEventType.OpeningTooLong:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      return null; // No change.
    default:
      // ErrorSensorConflict.
      if (closedSensor === CLOSED && openSensor === OPEN) {
        return ErrorSensorConflict(timestampSeconds);
      }
      // Closed.
      if (closedSensor === CLOSED && openSensor !== OPEN) {
        return Closed(timestampSeconds);
      }
      // Open.
      if (closedSensor !== CLOSED && openSensor === OPEN) {
        return Open(timestampSeconds);
      }
      // Unknown.
      return Unknown(timestampSeconds);
  }
  // Unreachable code. Switch statement must return a value.
}
