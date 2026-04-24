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
 * Unit tests for the extracted httpCheckForOpenDoors handler core.
 * H2 of docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * The fakes cover config + sensor-event reads. sendFCMForOldData is
 * sinon-stubbed because its orchestration (snooze lookup, dedupe,
 * firebase.messaging().send) is covered by OldDataFCMFakeTest.ts —
 * re-verifying it here would duplicate that coverage.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleCheckForOpenDoorsRequest } from '../../../src/functions/http/OpenDoor';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setSensorEventDBImpl,
  resetImpl as resetSensorEventDBImpl,
} from '../../../src/database/SensorEventDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeSensorEventDatabase } from '../../fakes/FakeSensorEventDatabase';
import * as OldDataFCM from '../../../src/controller/fcm/OldDataFCM';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';

describe('handleCheckForOpenDoorsRequest (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeSensorDB: FakeSensorEventDatabase;
  let sendFcmStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeSensorDB = new FakeSensorEventDatabase();
    setServerConfigDBImpl(fakeConfig);
    setSensorEventDBImpl(fakeSensorDB);
    sendFcmStub = sinon.stub(OldDataFCM, 'sendFCMForOldData');
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetSensorEventDBImpl();
    sinon.restore();
  });

  it('throws when production config has no buildTimestamp', async () => {
    // The A3 contract — missing buildTimestamp surfaces as an ERROR
    // log + thrown Error, not a silent fallback. The HTTP wrapper
    // catches this and returns 500.
    fakeConfig.seed({ body: {} });

    let caught: unknown;
    try {
      await handleCheckForOpenDoorsRequest();
    } catch (e) {
      caught = e;
    }
    expect(caught).to.be.instanceOf(Error);
    expect((caught as Error).message).to.match(/httpCheckForOpenDoors.*buildTimestamp missing/);
    expect(sendFcmStub.called).to.be.false;
  });

  it('passes buildTimestamp + current event data to sendFCMForOldData', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });
    const eventData = { currentEvent: { timestampSeconds: 12345 } };
    fakeSensorDB.seed(BUILD_TIMESTAMP, eventData);
    sendFcmStub.resolves(null);

    await handleCheckForOpenDoorsRequest();

    expect(sendFcmStub.calledOnce).to.be.true;
    const [passedBuildTimestamp, passedEventData] = sendFcmStub.firstCall.args;
    expect(passedBuildTimestamp).to.equal(BUILD_TIMESTAMP);
    expect(passedEventData).to.deep.equal(eventData);
  });

  it('returns the value produced by sendFCMForOldData', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });
    const sentinel = { topic: 'door-test', data: { ok: true } };
    sendFcmStub.resolves(sentinel);

    const result = await handleCheckForOpenDoorsRequest();

    expect(result).to.equal(sentinel);
  });
});
