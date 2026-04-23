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
 * Fake-based tests for src/controller/EventUpdates.ts.
 *
 * Replaces the previous sinon-stubbed EventUpdatesTest.ts. The fakes:
 *  - FakeSensorEventDatabase (writes captured in .saved / .updates)
 *  - FakeEventFCMService (calls captured in .sends)
 *
 * getNewEventOrNull is a pure function in EventInterpreter; stubbing it with
 * sinon is acceptable because picking "what does the interpreter decide?"
 * from sensor-snapshot inputs would duplicate EventInterpreter's own tests.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { updateEvent } from '../../src/controller/EventUpdates';
import {
  setImpl as setSensorEventDBImpl,
  resetImpl as resetSensorEventDBImpl,
} from '../../src/database/SensorEventDatabase';
import {
  setImpl as setEventFCMImpl,
  resetImpl as resetEventFCMImpl,
} from '../../src/controller/fcm/EventFCM';
import { FakeSensorEventDatabase } from '../fakes/FakeSensorEventDatabase';
import { FakeEventFCMService } from '../fakes/FakeEventFCMService';
import * as EventInterpreter from '../../src/controller/EventInterpreter';
import { SensorEvent, SensorEventType } from '../../src/model/SensorEvent';

describe('EventUpdates (via fakes)', () => {
  let fakeDB: FakeSensorEventDatabase;
  let fakeFCM: FakeEventFCMService;

  beforeEach(() => {
    fakeDB = new FakeSensorEventDatabase();
    fakeFCM = new FakeEventFCMService();
    setSensorEventDBImpl(fakeDB);
    setEventFCMImpl(fakeFCM);
  });

  afterEach(() => {
    resetSensorEventDBImpl();
    resetEventFCMImpl();
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
    expect(fakeFCM.sends).to.be.empty;
  });

  it('should create a new event if there is no previous event', async () => {
    const newEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    await updateEvent({ buildTimestamp: 'test' }, false);

    // Database: one save with the new event as currentEvent, no update.
    expect(fakeDB.saved).to.have.length(1);
    expect(fakeDB.saved[0][0]).to.equal('test');
    expect(fakeDB.saved[0][1].currentEvent).to.equal(newEvent);
    expect(fakeDB.updates).to.be.empty;

    // FCM: one send with the new event (matches sinon tests' calledOnceWith).
    expect(fakeFCM.sends).to.have.length(1);
    expect(fakeFCM.sends[0]).to.deep.equal({ buildTimestamp: 'test', event: newEvent });
  });

  it('should update the check-in time if the event has not changed', async () => {
    const oldEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    fakeDB.seed('test', { currentEvent: oldEvent });
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(null);

    await updateEvent({ buildTimestamp: 'test' }, false);

    // Database: one update (check-in write) with the seeded data; no save.
    expect(fakeDB.updates).to.have.length(1);
    expect(fakeDB.updates[0][0]).to.equal('test');
    // The seeded currentEvent is mutated in place (checkInTimestampSeconds
    // is updated) and written back via updateCurrent...().
    expect(fakeDB.updates[0][1].currentEvent).to.equal(oldEvent);
    expect(fakeDB.saved).to.be.empty;

    // FCM: one send with the old event (now carrying the new check-in time).
    expect(fakeFCM.sends).to.have.length(1);
    expect(fakeFCM.sends[0]).to.deep.equal({ buildTimestamp: 'test', event: oldEvent });
  });

  it('should create a new event if the event has changed', async () => {
    const oldEvent: SensorEvent = {
      type: SensorEventType.Closed, timestampSeconds: 12345, message: '', checkInTimestampSeconds: 0,
    };
    const newEvent: SensorEvent = {
      type: SensorEventType.Open, timestampSeconds: 54321, message: '', checkInTimestampSeconds: 0,
    };
    fakeDB.seed('test', { currentEvent: oldEvent });
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    await updateEvent({ buildTimestamp: 'test' }, false);

    // Database: one save containing both previous and new events; no update.
    expect(fakeDB.saved).to.have.length(1);
    expect(fakeDB.saved[0][0]).to.equal('test');
    expect(fakeDB.saved[0][1].currentEvent).to.equal(newEvent);
    expect(fakeDB.saved[0][1].previousEvent).to.equal(oldEvent);
    expect(fakeDB.updates).to.be.empty;

    // FCM: one send with the new event.
    expect(fakeFCM.sends).to.have.length(1);
    expect(fakeFCM.sends[0]).to.deep.equal({ buildTimestamp: 'test', event: newEvent });
  });
});
