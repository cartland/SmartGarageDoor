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

/**
 * Parallel test file for src/controller/EventUpdates.ts that uses
 * FakeSensorEventDatabase (via SensorEventDatabase.setImpl) instead of
 * sinon.stub(TimeSeriesDatabase.prototype, ...). Runs alongside
 * EventUpdatesTest.ts — if both pass, we have independent verification
 * that the production code works regardless of test-double strategy.
 *
 * The old file will be deleted in a follow-up once these tests are trusted.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { updateEvent } from '../../src/controller/EventUpdates';
import { setImpl, resetImpl } from '../../src/database/SensorEventDatabase';
import { FakeSensorEventDatabase } from '../fakes/FakeSensorEventDatabase';
import * as EventInterpreter from '../../src/controller/EventInterpreter';
import * as EventFCM from '../../src/controller/fcm/EventFCM';
import { SensorEvent, SensorEventType } from '../../src/model/SensorEvent';

describe('EventUpdates (via FakeSensorEventDatabase)', () => {
  let fakeDB: FakeSensorEventDatabase;

  beforeEach(() => {
    fakeDB = new FakeSensorEventDatabase();
    setImpl(fakeDB);
  });

  afterEach(() => {
    resetImpl();
    sinon.restore();
  });

  it('should do nothing if buildTimestamp is missing', async () => {
    const consoleLogSpy = sinon.spy(console, 'log');
    await updateEvent({}, false);
    expect(consoleLogSpy.calledWith(
      'scheduledJob:', false,
      'Skipping updateEvent() because data does not have buildTimestamp', {})).to.be.true;
    expect(fakeDB.saved).to.be.empty;
    expect(fakeDB.updates).to.be.empty;
  });

  it('should create a new event if there is no previous event', async () => {
    const newEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    await updateEvent({ buildTimestamp: 'test' }, false);

    expect(fakeDB.saved).to.have.length(1);
    expect(fakeDB.saved[0][0]).to.equal('test');
    expect(fakeDB.saved[0][1].currentEvent).to.equal(newEvent);
    expect(fakeDB.updates).to.be.empty;
  });

  it('should update the check-in time if the event has not changed', async () => {
    const oldEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    fakeDB.seed('test', { currentEvent: oldEvent });
    sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(null);

    await updateEvent({ buildTimestamp: 'test' }, false);

    expect(fakeDB.updates).to.have.length(1);
    expect(fakeDB.updates[0][0]).to.equal('test');
    // The seeded currentEvent object gets its checkInTimestampSeconds
    // mutated in place and written back via updateCurrent...().
    expect(fakeDB.updates[0][1].currentEvent).to.equal(oldEvent);
    expect(fakeDB.saved).to.be.empty;
  });

  it('should create a new event if the event has changed', async () => {
    const oldEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    const newEvent: SensorEvent = {
      type: SensorEventType.Open, timestampSeconds: 54321, message: '', checkInTimestampSeconds: 0,
    };
    fakeDB.seed('test', { currentEvent: oldEvent });
    sinon.stub(EventFCM, 'sendFCMForSensorEvent').resolves();
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    await updateEvent({ buildTimestamp: 'test' }, false);

    expect(fakeDB.saved).to.have.length(1);
    expect(fakeDB.saved[0][0]).to.equal('test');
    expect(fakeDB.saved[0][1].currentEvent).to.equal(newEvent);
    expect(fakeDB.saved[0][1].previousEvent).to.equal(oldEvent);
    expect(fakeDB.updates).to.be.empty;
  });
});
