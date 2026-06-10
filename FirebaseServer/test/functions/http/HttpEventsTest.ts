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
import * as fs from 'fs';
import * as path from 'path';

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

// Wire-contract fixtures shared with Android's KtorNetworkDoorDataSourceTest.
const HISTORY_FIXTURE_DIR = path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'eventHistory');
const FIRST_PAGE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(HISTORY_FIXTURE_DIR, 'response_first_page.json'), 'utf8'),
);
const LAST_PAGE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(HISTORY_FIXTURE_DIR, 'response_last_page.json'), 'utf8'),
);
const EMPTY_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(HISTORY_FIXTURE_DIR, 'response_empty.json'), 'utf8'),
);

// Fixed clock so the 7-day window cutoff is deterministic. EVENT_A/B fall inside
// the window; EVENT_C is older than the cutoff (drives the under-fill probe).
const NOW_MILLIS = 1700000000000;
const WINDOW_SECONDS = 7 * 24 * 60 * 60;
const EXPECTED_SINCE_SECONDS = Math.floor(NOW_MILLIS / 1000) - WINDOW_SECONDS;

const EVENT_A = FIRST_PAGE_FIXTURE.eventHistory[0]; // OPEN @ 1699999000
const EVENT_B = FIRST_PAGE_FIXTURE.eventHistory[1]; // CLOSED @ 1699998000
const EVENT_C = LAST_PAGE_FIXTURE.eventHistory[0]; // OPENING @ 1699000000 (outside window)
const NEXT_TOKEN = FIRST_PAGE_FIXTURE.nextPageToken;

function seedAbc(db: FakeSensorEventDatabase): void {
  db.seedPageEvents(BUILD_TIMESTAMP, [
    { cursor: { seconds: 1699999000, nanoseconds: 0 }, item: EVENT_A },
    { cursor: { seconds: 1699998000, nanoseconds: 0 }, item: EVENT_B },
    { cursor: { seconds: 1699000000, nanoseconds: 0 }, item: EVENT_C },
  ]);
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
  let fakeDB: FakeSensorEventDatabase;

  beforeEach(() => {
    fakeDB = new FakeSensorEventDatabase();
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

  it('returns the windowed first page with pagination tokens (wire contract)', async () => {
    seedAbc(fakeDB);
    const result = await handleEventHistory({
      query: { session: 'fixture-session', buildTimestamp: BUILD_TIMESTAMP, pageSize: '50' },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    expect(result).to.deep.equal({ kind: 'ok', data: FIRST_PAGE_FIXTURE });
  });

  it('pages older via the token to the oldest event, then signals no more (wire contract)', async () => {
    seedAbc(fakeDB);
    const result = await handleEventHistory({
      query: {
        session: 'fixture-session',
        buildTimestamp: BUILD_TIMESTAMP,
        pageSize: '50',
        pageToken: NEXT_TOKEN,
      },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    expect(result).to.deep.equal({ kind: 'ok', data: LAST_PAGE_FIXTURE });
  });

  it('returns an empty page with no tokens when there are no events (wire contract)', async () => {
    const result = await handleEventHistory({
      query: { session: 'fixture-session', buildTimestamp: BUILD_TIMESTAMP, pageSize: '50' },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    expect(result).to.deep.equal({ kind: 'ok', data: EMPTY_FIXTURE });
  });

  it('clamps pageSize to the 50 maximum', async () => {
    const result = await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP, pageSize: '999' },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    expect(result.kind).to.equal('ok');
    expect(fakeDB.pageCalls).to.have.lengthOf(1);
    expect(fakeDB.pageCalls[0].limit).to.equal(50);
  });

  it('decodes the page token into a startAfter cursor and drops the time window', async () => {
    await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP, pageSize: '50', pageToken: NEXT_TOKEN },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    const opts = fakeDB.pageCalls[0];
    expect(opts.direction).to.equal('older');
    expect(opts.startAfter).to.deep.equal({ seconds: 1699998000, nanoseconds: 0 });
    expect(opts.sinceSeconds).to.be.undefined;
  });

  it('applies the 7-day window even when only the legacy eventHistoryMaxCount is sent', async () => {
    await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP, eventHistoryMaxCount: '30' },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    const opts = fakeDB.pageCalls[0];
    expect(opts.direction).to.equal('older');
    expect(opts.limit).to.equal(30);
    expect(opts.sinceSeconds).to.equal(EXPECTED_SINCE_SECONDS);
    expect(opts.startAfter).to.be.undefined;
  });

  it('falls back to a fresh windowed first page when the token is malformed', async () => {
    await handleEventHistory({
      query: { buildTimestamp: BUILD_TIMESTAMP, pageSize: '50', pageToken: 'not-a-valid-token' },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    const opts = fakeDB.pageCalls[0];
    expect(opts.startAfter).to.be.undefined;
    expect(opts.sinceSeconds).to.equal(EXPECTED_SINCE_SECONDS);
  });

  it('ignores a token scoped to a different buildTimestamp', async () => {
    await handleEventHistory({
      query: { buildTimestamp: 'Different Build 2022', pageSize: '50', pageToken: NEXT_TOKEN },
      body: {},
      nowMillis: NOW_MILLIS,
    });
    const opts = fakeDB.pageCalls[0];
    expect(opts.buildTimestamp).to.equal('Different Build 2022');
    expect(opts.startAfter).to.be.undefined;
    expect(opts.sinceSeconds).to.equal(EXPECTED_SINCE_SECONDS);
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
