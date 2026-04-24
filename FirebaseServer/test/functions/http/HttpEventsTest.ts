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
 * Unit tests for the three extracted Events HTTP handler cores. H5 of
 * the handler testing plan.
 *
 * Fakes cover SensorEventDatabase. getNewEventOrNull is sinon-stubbed
 * because EventInterpreter has its own tests (EventInterpreterTest.ts).
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import {
  handleCurrentEventData,
  handleEventHistory,
  handleNextEvent,
} from '../../../src/functions/http/Events';
import {
  setImpl as setSensorEventDBImpl,
  resetImpl as resetSensorEventDBImpl,
} from '../../../src/database/SensorEventDatabase';
import { FakeSensorEventDatabase } from '../../fakes/FakeSensorEventDatabase';
import * as EventInterpreter from '../../../src/controller/EventInterpreter';
import { SensorEventType } from '../../../src/model/SensorEvent';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';

// Loosen the real FakeSensorEventDatabase.getRecentForBuildTimestamp
// default (returns []) only where the test needs a history payload.
class HistoryFake extends FakeSensorEventDatabase {
  private recent: any[] = [];
  setRecent(items: any[]): void { this.recent = items; }
  async getRecentForBuildTimestamp(_b: string, _n: number): Promise<any> {
    return this.recent;
  }
}

describe('handleCurrentEventData (pure handler core)', () => {
  let fakeDB: FakeSensorEventDatabase;

  beforeEach(() => {
    fakeDB = new FakeSensorEventDatabase();
    setSensorEventDBImpl(fakeDB);
  });

  afterEach(() => {
    resetSensorEventDBImpl();
  });

  it('returns 400 with { error } when buildTimestamp is missing', async () => {
    const result = await handleCurrentEventData({
      query: { session: 'abc' },
      body: {},
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Invalid buildTimestamp' },
    });
  });

  it('echoes the session from the query and returns current event data', async () => {
    const currentEvent = { currentEvent: { timestampSeconds: 12345 } };
    fakeDB.seed(BUILD_TIMESTAMP, currentEvent);

    const result = await handleCurrentEventData({
      query: { session: 'client-session', buildTimestamp: BUILD_TIMESTAMP },
      body: { payload: 1 },
    });

    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data.session).to.equal('client-session');
      expect(result.data.buildTimestamp).to.equal(BUILD_TIMESTAMP);
      expect(result.data.currentEventData).to.deep.equal(currentEvent);
      expect(result.data.queryParams).to.deep.equal({
        session: 'client-session',
        buildTimestamp: BUILD_TIMESTAMP,
      });
    }
  });

  it('generates a session when none is provided', async () => {
    fakeDB.seed(BUILD_TIMESTAMP, {});
    const result = await handleCurrentEventData({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
    });
    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data.session).to.match(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      );
    }
  });
});

describe('handleEventHistory (pure handler core)', () => {
  let fakeDB: HistoryFake;

  beforeEach(() => {
    fakeDB = new HistoryFake();
    setSensorEventDBImpl(fakeDB);
  });

  afterEach(() => {
    resetSensorEventDBImpl();
  });

  it('returns 400 when buildTimestamp is missing', async () => {
    const result = await handleEventHistory({ query: {}, body: {} });
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.status).to.equal(400);
      expect(result.body).to.deep.equal({ error: 'Invalid buildTimestamp' });
    }
  });

  it('returns the recent history list with the count included', async () => {
    const history = [{ id: 1 }, { id: 2 }, { id: 3 }];
    fakeDB.setRecent(history);

    const result = await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP, eventHistoryMaxCount: '3' },
      body: {},
    });

    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data.eventHistory).to.deep.equal(history);
      expect(result.data.eventHistoryCount).to.equal(3);
    }
  });

  it('falls back to the 12-item default when the max-count param is absent', async () => {
    fakeDB.setRecent([]);
    // Pin the default by spying on the DB method.
    const getRecentSpy = sinon.spy(fakeDB, 'getRecentForBuildTimestamp');

    await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
    });

    expect(getRecentSpy.calledOnce).to.be.true;
    expect(getRecentSpy.firstCall.args).to.deep.equal([BUILD_TIMESTAMP, 12]);
  });
});

describe('handleNextEvent (pure handler core)', () => {
  let fakeDB: FakeSensorEventDatabase;

  beforeEach(() => {
    fakeDB = new FakeSensorEventDatabase();
    setSensorEventDBImpl(fakeDB);
  });

  afterEach(() => {
    resetSensorEventDBImpl();
    sinon.restore();
  });

  it('returns 200 with { oldEvent: {}, newEvent: null } when the interpreter decides nothing changed', async () => {
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(null);

    const result = await handleNextEvent({
      query: { buildTimestamp: BUILD_TIMESTAMP, sensorA: 'open' },
      body: {},
    });

    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data.newEvent).to.be.null;
      expect(fakeDB.saved).to.be.empty;
    }
  });

  it('saves the newly-interpreted event when the interpreter returns one', async () => {
    const newEvent = {
      type: SensorEventType.Closed,
      timestampSeconds: 99,
      message: '',
      checkInTimestampSeconds: 0,
    };
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(newEvent);

    const result = await handleNextEvent({
      query: {
        buildTimestamp: BUILD_TIMESTAMP,
        sensorA: 'closed',
        sensorB: 'closed',
        timestampSeconds: '99',
      },
      body: {},
    });

    expect(result.kind).to.equal('ok');
    expect(fakeDB.saved).to.have.lengthOf(1);
    expect(fakeDB.saved[0]).to.deep.equal([BUILD_TIMESTAMP, newEvent]);
  });

  it('passes sensor-snapshot values through to the interpreter as strings + parsed timestamp', async () => {
    const stub = sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(null);
    fakeDB.seed(BUILD_TIMESTAMP, { some: 'old' });

    await handleNextEvent({
      query: {
        buildTimestamp: BUILD_TIMESTAMP,
        sensorA: 'open',
        sensorB: 'closed',
        timestampSeconds: '42',
      },
      body: {},
    });

    expect(stub.calledOnce).to.be.true;
    const [oldEvent, snapshot, ts] = stub.firstCall.args;
    expect(oldEvent).to.deep.equal({ some: 'old' });
    expect(snapshot).to.deep.equal({ sensorA: 'open', sensorB: 'closed', timestampSeconds: 0 });
    expect(ts).to.equal(42);
  });
});
