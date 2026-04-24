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
 * Unit tests for the extracted pubsubCheckForOpenDoorsJob handler core.
 * H2 of docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * Same inputs as the HTTP variant; different context label (used in
 * the thrown-error message + ERROR log when config is missing).
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleCheckForOpenDoorsJob } from '../../../src/functions/pubsub/OpenDoor';
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

describe('handleCheckForOpenDoorsJob (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeSensorDB: FakeSensorEventDatabase;
  let sendFcmStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeSensorDB = new FakeSensorEventDatabase();
    setServerConfigDBImpl(fakeConfig);
    setSensorEventDBImpl(fakeSensorDB);
    sendFcmStub = sinon.stub(OldDataFCM, 'sendFCMForOldData').resolves(null);
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetSensorEventDBImpl();
    sinon.restore();
  });

  it('throws when production config has no buildTimestamp (context label: pubsubCheckForOpenDoorsJob)', async () => {
    // A failed pubsub tick throws — Firebase marks the run failed and
    // retries on the next scheduled invocation. See ConfigAccessors
    // docstring for the rationale.
    fakeConfig.seed({ body: {} });

    let caught: unknown;
    try {
      await handleCheckForOpenDoorsJob();
    } catch (e) {
      caught = e;
    }
    expect(caught).to.be.instanceOf(Error);
    expect((caught as Error).message).to.match(/pubsubCheckForOpenDoorsJob.*buildTimestamp missing/);
    expect(sendFcmStub.called).to.be.false;
  });

  it('invokes sendFCMForOldData with the config-derived buildTimestamp and current event', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });
    const eventData = { currentEvent: { timestampSeconds: 99999 } };
    fakeSensorDB.seed(BUILD_TIMESTAMP, eventData);

    await handleCheckForOpenDoorsJob();

    expect(sendFcmStub.calledOnce).to.be.true;
    const [passedBuildTimestamp, passedEventData] = sendFcmStub.firstCall.args;
    expect(passedBuildTimestamp).to.equal(BUILD_TIMESTAMP);
    expect(passedEventData).to.deep.equal(eventData);
  });

  it('resolves with no return value on the happy path', async () => {
    fakeConfig.seed({ body: { buildTimestamp: BUILD_TIMESTAMP } });

    const result = await handleCheckForOpenDoorsJob();

    // Pubsub handlers return null from onRun(); the pure function
    // resolves to undefined (its body has no return statement). The
    // wrapper explicitly returns null.
    expect(result).to.be.undefined;
  });
});
