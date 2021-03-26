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
}

export enum SensorEventType {
  Unknown = 'UNKNOWN',
  ErrorSensorConflict = 'ERROR_SENSOR_CONFLICT',
  Closed = 'CLOSED',
  Closing = 'CLOSING',
  ClosingTooLong = 'CLOSING_TOO_LONG',
  Open = 'OPEN',
  Opening = 'OPENING',
  OpeningTooLong = 'OPENING_TOO_LONG',
}

export function Unknown(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Unknown,
    timestampSeconds: timestampSeconds,
    message: 'Unknown',
  };
}

export function ErrorSensorConflict(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.ErrorSensorConflict,
    timestampSeconds: timestampSeconds,
    message: 'Conflict between open and close sensors',
  };
}

export function Closed(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Closed,
    timestampSeconds: timestampSeconds,
    message: 'Closed',
  };
}

export function Closing(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Closing,
    timestampSeconds: timestampSeconds,
    message: 'Closing',
  };
}

export function ClosingTooLong(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.ClosingTooLong,
    timestampSeconds: timestampSeconds,
    message: 'Closing (check sensor)',
  };
}

export function Open(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Open,
    timestampSeconds: timestampSeconds,
    message: 'Open',
  };
}

export function Opening(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.Opening,
    timestampSeconds: timestampSeconds,
    message: 'Opening',
  };
}

export function OpeningTooLong(timestampSeconds: number): SensorEvent {
  return <SensorEvent>{
    type: SensorEventType.OpeningTooLong,
    timestampSeconds: timestampSeconds,
    message: 'Opening (check sensor)',
  };
}
