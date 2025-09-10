/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
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

import { expect } from 'chai';
import * as sinon from 'sinon';

import { updateEvent } from '../../src/controller/EventUpdates';
import { TimeSeriesDatabase } from '../../src/database/TimeSeriesDatabase';
import * as EventInterpreter from '../../src/controller/EventInterpreter';
import * as EventFCM from '../../src/controller/fcm/EventFCM';
import { SensorEvent, SensorEventType } from '../../src/model/SensorEvent';

describe('EventUpdates', () => {
  afterEach(() => {
    sinon.restore();
  });

  it('should do nothing if buildTimestamp is missing', async () => {
    const consoleLogSpy = sinon.spy(console, 'log');
    await updateEvent({}, false);
    expect(consoleLogSpy.calledWith('scheduledJob:', false, 'Skipping updateEvent() because data does not have buildTimestamp', {})).to.be.true;
  });

  it('should create a new event if there is no previous event', async () => {
    const getCurrentStub = sinon.stub(TimeSeriesDatabase.prototype, 'getCurrent').resolves({});
    const saveStub = sinon.stub(TimeSeriesDatabase.prototype, 'save').resolves();
    const sendFCMStub = sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    const newEvent: SensorEvent = { type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0 };
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    const data = { buildTimestamp: 'test' };
    await updateEvent(data, false);

    expect(getCurrentStub.calledOnceWith('test')).to.be.true;
    expect(saveStub.calledOnce).to.be.true;
    expect(sendFCMStub.calledOnceWith('test', newEvent)).to.be.true;
  });

  it('should update the check-in time if the event has not changed', async () => {
    const oldEvent: SensorEvent = { type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0 };
    const getCurrentStub = sinon.stub(TimeSeriesDatabase.prototype, 'getCurrent').resolves({ currentEvent: oldEvent });
    const updateStub = sinon.stub(TimeSeriesDatabase.prototype, 'updateCurrentWithMatchingCurrentEventTimestamp').resolves();
    const sendFCMStub = sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(null);

    const data = { buildTimestamp: 'test' };
    await updateEvent(data, false);

    expect(getCurrentStub.calledOnceWith('test')).to.be.true;
    expect(updateStub.calledOnce).to.be.true;
    expect(sendFCMStub.calledOnce).to.be.true;
  });

  it('should create a new event if the event has changed', async () => {
    const oldEvent: SensorEvent = { type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0 };
    const newEvent: SensorEvent = { type: SensorEventType.Open, timestampSeconds: 54321, message: '', checkInTimestampSeconds: 0 };
    const getCurrentStub = sinon.stub(TimeSeriesDatabase.prototype, 'getCurrent').resolves({ currentEvent: oldEvent });
    const saveStub = sinon.stub(TimeSeriesDatabase.prototype, 'save').resolves();
    const sendFCMStub = sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    const data = { buildTimestamp: 'test' };
    await updateEvent(data, false);

    expect(getCurrentStub.calledOnceWith('test')).to.be.true;
    expect(saveStub.calledOnce).to.be.true;
    expect(sendFCMStub.calledOnceWith('test', newEvent)).to.be.true;
  });
});