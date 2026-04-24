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
 * Unit tests for the extracted httpDeleteOldData handler core. H5 of
 * the handler testing plan.
 *
 * deleteOldData is sinon-stubbed — controller-layer concern. The
 * handler's job is the config gate + query-param parsing.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleDeleteOldData } from '../../../src/functions/http/DeleteData';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import * as DatabaseCleaner from '../../../src/controller/DatabaseCleaner';

describe('handleDeleteOldData (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let deleteStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
    deleteStub = sinon.stub(DatabaseCleaner, 'deleteOldData').resolves({ deleted: 7 });
    fakeConfig.seed({ body: { deleteOldDataEnabled: true } });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('returns 400 Disabled when deleteOldDataEnabled is false', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { deleteOldDataEnabled: false } });

    const result = await handleDeleteOldData({ query: {} });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Disabled' },
    });
    expect(deleteStub.called).to.be.false;
  });

  it('parses cutoffTimestampSeconds from query and passes it through', async () => {
    await handleDeleteOldData({ query: { cutoffTimestampSeconds: '1234567890' } });

    expect(deleteStub.calledOnce).to.be.true;
    const [cutoff, dryRun] = deleteStub.firstCall.args;
    expect(cutoff).to.equal(1234567890);
    expect(dryRun).to.equal(false);
  });

  it('sets dryRun=true when the query contains a dryRun key (regardless of value)', async () => {
    // The pre-extraction code uses `'dryRun' in request.query` — key
    // presence is what matters, not its value. Tests pin that.
    await handleDeleteOldData({ query: { dryRun: '' } });

    expect(deleteStub.firstCall.args[1]).to.equal(true);
  });

  it('passes null cutoff when the query omits cutoffTimestampSeconds', async () => {
    await handleDeleteOldData({ query: {} });

    expect(deleteStub.firstCall.args[0]).to.be.null;
  });

  it('returns 200 ok with the { dryRun, summary } shape', async () => {
    const result = await handleDeleteOldData({
      query: { dryRun: '1', cutoffTimestampSeconds: '100' },
    });

    expect(result).to.deep.equal({
      kind: 'ok',
      data: { dryRun: true, summary: { deleted: 7 } },
    });
  });
});
