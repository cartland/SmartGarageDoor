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

// npm run tests

import { expect } from 'chai';
import { getNewEventOrNull } from '../../src/controller/EventInterpreter';
import { SensorEvent, SensorEventType, Unknown, ErrorSensorConflict, Closed, Closing, ClosingTooLong, Open, Opening, OpeningTooLong } from '../../src/model/SensorEvent';
import { SensorSnapshot } from '../../src/model/SensorSnapshot';

describe('Event', () => {
  it('can be created', () => {
    const expectedType = SensorEventType.ErrorSensorConflict;
    const expectedTimestamp = 1616798536;
    const expectedMessage = 'Test error message';
    const result = <SensorEvent>{
      type: expectedType,
      timestampSeconds: expectedTimestamp,
      message: expectedMessage,
    };
    expect(result.type).to.equal(expectedType);
    expect(result.timestampSeconds).to.equal(expectedTimestamp);
    expect(result.message).to.equal(expectedMessage);
  });
  it('can be Unknown', () => {
    const expected = SensorEventType.Unknown;
    const actual = Unknown(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be ErrorSensorConflict', () => {
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = ErrorSensorConflict(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const expected = SensorEventType.Closed;
    const actual = Closed(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be Closing', () => {
    const expected = SensorEventType.Closing;
    const actual = Closing(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be ClosingTooLong', () => {
    const expected = SensorEventType.ClosingTooLong;
    const actual = ClosingTooLong(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const expected = SensorEventType.Open;
    const actual = Open(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be Opening', () => {
    const expected = SensorEventType.Opening;
    const actual = Opening(0).type;
    expect(actual).to.equal(expected);
  });
  it('can be OpeningTooLong', () => {
    const expected = SensorEventType.OpeningTooLong;
    const actual = OpeningTooLong(0).type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull first event', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = null;
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 0,
    }
    const timestampSeconds = 10;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = null;
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 0,
    }
    const timestampSeconds = 10;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = null;
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 0,
    }
    const timestampSeconds = 10;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Unknown', () => {
    const oldEvent = null;
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 0,
    }
    const timestampSeconds = 10;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Unknown;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from Unknown', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = Unknown(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = Unknown(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = Unknown(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = Unknown(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from ErrorSensorConflict', () => {
  it('can be null', () => {
    const oldEvent = ErrorSensorConflict(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = ErrorSensorConflict(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = ErrorSensorConflict(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Unknown', () => {
    const oldEvent = ErrorSensorConflict(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Unknown;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from Closed', () => {
  it('can be Error', () => {
    const oldEvent = Closed(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = Closed(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = Closed(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Opening', () => {
    const oldEvent = Closed(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Opening;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from Closing', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = Closing(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = Closing(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = Closing(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = Closing(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
  it('can be ClosingTooLong', () => {
    const oldEvent = Closing(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 1000 * 60;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ClosingTooLong;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from ClosingTooLong', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = ClosingTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = ClosingTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = ClosingTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = ClosingTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 1000 * 60;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from Open', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = Open(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = Open(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = Open(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
  it('can be Closing', () => {
    const oldEvent = Open(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closing;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from Opening', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = Opening(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = Opening(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = Opening(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = Opening(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
  it('can be OpeningTooLong', () => {
    const oldEvent = Opening(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 1000 * 60;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.OpeningTooLong;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
});

describe('getNewEventOrNull from OpeningTooLong', () => {
  it('can be ErrorSensorConflict', () => {
    const oldEvent = OpeningTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.ErrorSensorConflict;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Closed', () => {
    const oldEvent = OpeningTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '0',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Closed;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be Open', () => {
    const oldEvent = OpeningTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '0',
      timestampSeconds: 10,
    }
    const timestampSeconds = 20;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = SensorEventType.Open;
    const actual = result.type;
    expect(actual).to.equal(expected);
  });
  it('can be null', () => {
    const oldEvent = OpeningTooLong(0);
    const sensorSnapshot = <SensorSnapshot>{
      sensorA: '1',
      sensorB: '1',
      timestampSeconds: 10,
    }
    const timestampSeconds = 1000 * 60;
    const result = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    const expected = null;
    const actual = result;
    expect(actual).to.equal(expected);
  });
});