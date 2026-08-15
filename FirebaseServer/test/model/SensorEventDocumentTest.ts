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

// npm run tests

/**
 * The stored-document contract: what EventUpdates WRITES is what every reader
 * has to be able to READ.
 *
 * These two sides were uncoupled once and it cost a production feature. The
 * `doorCommand` endpoint read `doc.type` off the wrapper instead of
 * `doc.currentEvent.type`, so it refused every spoken command with
 * DOOR_STATE_UNKNOWN for the whole of server/35. Its tests passed the entire
 * time, because the fixture seeded the same bare shape the reader expected —
 * the fixture and the bug agreed with each other, and nothing else had an
 * opinion.
 *
 * So the round-trip block below is the load-bearing part of this file: it
 * asserts against the WRITER's real output rather than against a shape a test
 * author typed out.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import {
  CURRENT_EVENT_KEY,
  PREVIOUS_EVENT_KEY,
  readCurrentEvent,
} from '../../src/model/SensorEventDocument';
import { SensorEvent, SensorEventType } from '../../src/model/SensorEvent';
import { updateEvent } from '../../src/controller/EventUpdates';
import {
  DATABASE as SensorEventDatabase,
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

const anEvent = (type: SensorEventType): SensorEvent => ({
  type,
  timestampSeconds: 1725781091,
  message: 'Test message',
  checkInTimestampSeconds: 1725781092,
});

describe('SensorEventDocument keys', () => {
  // These strings are the on-disk contract for every document already in
  // Firestore. Changing either one requires a data migration, so pin them.
  it('names the stored keys exactly as production wrote them', () => {
    expect(CURRENT_EVENT_KEY).to.equal('currentEvent');
    expect(PREVIOUS_EVENT_KEY).to.equal('previousEvent');
  });
});

describe('readCurrentEvent', () => {
  it('finds the reading nested inside a stored document', () => {
    const event = anEvent(SensorEventType.Open);
    expect(readCurrentEvent({ buildTimestamp: 'bt', currentEvent: event })).to.deep.equal(event);
  });

  // THE BUG, pinned. A bare event is what a hand-written fixture looks like
  // when its author forgot the wrapper. Production can never store this shape,
  // so it must not read as a live door — otherwise a fixture can agree with a
  // wrong reader and the suite goes green while the feature is dead.
  it('answers null for a BARE event stored without its wrapper', () => {
    const bare = anEvent(SensorEventType.Open);
    expect(readCurrentEvent(bare)).to.equal(null);
  });

  it('answers null for the empty object getCurrent() returns for a missing document', () => {
    expect(readCurrentEvent({})).to.equal(null);
  });

  it('answers null for an absent or explicitly null document', () => {
    expect(readCurrentEvent(null)).to.equal(null);
    expect(readCurrentEvent(undefined)).to.equal(null);
  });

  it('answers null when the wrapper carries no reading', () => {
    expect(readCurrentEvent({ buildTimestamp: 'bt', currentEvent: null })).to.equal(null);
    expect(readCurrentEvent({ buildTimestamp: 'bt', previousEvent: anEvent(SensorEventType.Closed) }))
      .to.equal(null);
  });
});

describe('stored-document round trip (writer -> reader)', () => {
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

  /**
   * The assertion that would have caught the doorCommand bug on the day it was
   * written. Nothing here states the document shape by hand: EventUpdates
   * writes it, getCurrentEvent reads it, and if the two ever disagree about
   * nesting this fails no matter which side moved.
   */
  it('reads back the event that EventUpdates actually wrote', async () => {
    const written = anEvent(SensorEventType.Open);
    sinon.stub(EventInterpreter, 'getNewEventOrNull').returns(written);

    await updateEvent({ buildTimestamp: 'test' }, false);

    const readBack = await SensorEventDatabase.getCurrentEvent('test');
    expect(readBack).to.deep.equal(written);
    expect(readBack.type).to.equal(SensorEventType.Open);
  });

  /**
   * The fake can no longer lie about nesting.
   *
   * getCurrentEvent is derived from the storage interface rather than being a
   * member of it, so a fake installed with setImpl supplies raw storage only
   * and the unwrap always runs. Seeding a bare event therefore reads back as
   * "no event" in tests exactly as it does in production — which is what makes
   * a mis-shaped fixture fail loudly instead of agreeing with a wrong reader.
   */
  it('answers null for a bare-seeded fixture, the way production would', async () => {
    fakeDB.seed('test', anEvent(SensorEventType.Open));

    expect(await SensorEventDatabase.getCurrentEvent('test')).to.equal(null);
  });

  // Positive control for the test above: the same seeding helper, given the
  // real shape, must still produce a live reading. Without this, a
  // getCurrentEvent that returned null unconditionally would satisfy the
  // bare-seed assertion and every doorCommand refusal test in the suite.
  it('answers the reading for a correctly-seeded fixture', async () => {
    const event = anEvent(SensorEventType.Open);
    fakeDB.seed('test', { currentEvent: event });

    expect(await SensorEventDatabase.getCurrentEvent('test')).to.deep.equal(event);
  });
});
