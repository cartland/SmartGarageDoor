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
import { Message, Notification } from '../model/FCM';
import { buildTimestampToFcmTopic } from '../model/FcmTopic';

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

export function isEventOld(currentEvent: SensorEvent, now: number): boolean {
  const eventDurationSeconds = now - currentEvent.timestampSeconds;
  return eventDurationSeconds > TOO_LONG_OPEN_SECONDS;
}

export function getMessageFromEvent(buildTimestamp: string, currentEvent: SensorEvent, now: number): Message {
  const eventDurationSeconds = now - currentEvent.timestampSeconds;
  const durationMinutes = Math.floor(eventDurationSeconds / 60);
  const durationHours = Math.floor(durationMinutes / 60);
  let durationString = '';
  if (durationMinutes < 60) {
    durationString = durationMinutes.toString() + ' minutes';
  } else {
    durationString = durationHours.toString() + ' hours';
  }
  const message = <Message>{};
  message.notification = <Notification>{};
  message.topic = buildTimestampToFcmTopic(buildTimestamp);
  let type = currentEvent.type;
  switch (type) {
    case SensorEventType.Unknown:
      message.notification.title = 'Unknown door status';
      message.notification.body = 'Error not resolved for longer than ' + durationString;
      return message;
    case SensorEventType.ErrorSensorConflict:
      message.notification.title = 'Door error';
      message.notification.body = 'Door error for longer than ' + durationString;
      return message;
    case SensorEventType.Closed:
      return null;
    case SensorEventType.Closing:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door did not close for more than ' + durationString;
      return message;
    case SensorEventType.ClosingTooLong:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door did not close for more than ' + durationString;
      return message;
    case SensorEventType.Open:
      message.notification.title = 'Garage door open';
      message.notification.body = 'Open for more than ' + durationString;
      return message;
    case SensorEventType.Opening:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door not closed for more than ' + durationString;
      return message;
    case SensorEventType.OpeningTooLong:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door not closed for more than ' + durationString;
      return message;
    default:
      message.notification.title = 'Unknown door status';
      message.notification.body = 'Door error for longer than ' + durationString;
      return message;
  }
}