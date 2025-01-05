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

export interface SensorEvent {
  type: SensorEventType,
  timestampSeconds: number,
  message: string,
  checkInTimestampSeconds: number,
}

/**
 * Convert a SensorEvent to a map of strings.
 *
 * Example input:
 * const sensorEvent = <SensorEvent>{
 *   type: SensorEventType.Closed,
 *   timestampSeconds: 1725781091,
 *   message: "Test message",
 *   checkInTimestampSeconds: 1725781092,
 * };
 *
 * Example output:
 * {
 *   type: 'CLOSED',
 *   timestampSeconds: '1725781091',
 *   message: 'Test message',
 *   checkInTimestampSeconds: '1725781092'
 * }
 * @param sensorEvent 
 * @return A map of strings representing the SensorEvent.
 */
export function SensorEventAsStringMap(sensorEvent: SensorEvent): { [key: string]: string } {
  const map: { [key: string]: string } = {};
  map['type'] = sensorEvent.type;
  map['timestampSeconds'] = String(sensorEvent.timestampSeconds);
  map['message'] = sensorEvent.message;
  map['checkInTimestampSeconds'] = String(sensorEvent.checkInTimestampSeconds);
  return map;
}

export enum SensorEventType {
  Unknown = 'UNKNOWN',
  ErrorSensorConflict = 'ERROR_SENSOR_CONFLICT',
  Closed = 'CLOSED',
  Closing = 'CLOSING',
  ClosingTooLong = 'CLOSING_TOO_LONG',
  Open = 'OPEN',
  OpenMisaligned = 'OPEN_MISALIGNED',
  Opening = 'OPENING',
  OpeningTooLong = 'OPENING_TOO_LONG',
}

export function Unknown(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Unknown,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'No sensor data.',
  };
}

export function ErrorSensorConflict(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.ErrorSensorConflict,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The sensors say the door is both open and closed at the same time.',
  };
}

export function Closed(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Closed,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door is closed.',
  };
}

export function Closing(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Closing,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door is closing.',
  };
}

export function ClosingTooLong(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.ClosingTooLong,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door was closing but never closed.',
  };
}

export function Open(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Open,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door is open.',
  };
}

export function OpenMisaligned(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.OpenMisaligned,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door is open (misaligned).',
  }
}

export function Opening(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Opening,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door is opening.',
  };
}

export function OpeningTooLong(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.OpeningTooLong,
    timestampSeconds: timestampSeconds,
    checkInTimestampSeconds: timestampSeconds,
    message: 'The door was opening but never successfully opened.',
  };
}
