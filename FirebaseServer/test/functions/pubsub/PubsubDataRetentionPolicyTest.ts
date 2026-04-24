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
 * Unit tests for the extracted pubsubDataRetentionPolicy handler core.
 * H5 of the handler testing plan —
 * docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * deleteOldData is sinon-stubbed — the handler's job is the config
 * gate and the 2-week cutoff arithmetic; the cleanup logic has its
 * own tests in the controller layer.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleDataRetentionPolicy } from '../../../src/functions/pubsub/DataRetentionPolicy';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import * as DatabaseCleaner from '../../../src/controller/DatabaseCleaner';

describe('handleDataRetentionPolicy (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let deleteStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
    deleteStub = sinon.stub(DatabaseCleaner, 'deleteOldData').resolves({});
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('skips deletion (with the existing info log) when deleteOldDataEnabled is false', async () => {
    fakeConfig.seed({ body: { deleteOldDataEnabled: false } });
    const logSpy = sinon.spy(console, 'log');

    await handleDataRetentionPolicy();

    expect(deleteStub.called).to.be.false;
    expect(
      logSpy.calledWith('Deleting data is disabled'),
      'expected disabled-path log message',
    ).to.be.true;
  });

  it('calls deleteOldData with the 2-week cutoff in seconds and dryRun=false', async () => {
    fakeConfig.seed({ body: { deleteOldDataEnabled: true } });
    // Pin "now" so the cutoff is deterministic.
    const nowMillis = 2_000_000_000_000;
    const expectedCutoffSeconds = (nowMillis - 1000 * 60 * 60 * 24 * 14) / 1000;

    await handleDataRetentionPolicy(nowMillis);

    expect(deleteStub.calledOnce).to.be.true;
    const [cutoffSeconds, dryRun] = deleteStub.firstCall.args;
    expect(cutoffSeconds).to.equal(expectedCutoffSeconds);
    expect(dryRun).to.equal(false);
  });

  it('is a no-op when config is missing the deleteOldDataEnabled flag (defaults to false)', async () => {
    // isDeleteOldDataEnabled returns false when the body key is absent
    // — same effect as explicitly setting the flag to false.
    fakeConfig.seed({ body: {} });

    await handleDataRetentionPolicy();

    expect(deleteStub.called).to.be.false;
  });
});
