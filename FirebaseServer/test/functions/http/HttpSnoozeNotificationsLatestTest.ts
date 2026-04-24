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
 * Unit tests for the extracted httpSnoozeNotificationsLatest handler
 * core. H4 of the handler testing plan — see
 * docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * First handler to use HandlerResult<T>. Verifies that the kind +
 * status + body tuple matches the pre-extraction wrapper's
 * response.status(...).send(...) arguments byte-for-byte.
 *
 * getSnoozeStatus is sinon-stubbed — its internals are covered by
 * SnoozeNotificationsTest.ts.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleSnoozeNotificationsLatest } from '../../../src/functions/http/Snooze';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import * as SnoozeNotifications from '../../../src/controller/SnoozeNotifications';
import { SnoozeStatus } from '../../../src/model/SnoozeRequest';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';

describe('handleSnoozeNotificationsLatest (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let getSnoozeStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    setServerConfigDBImpl(fakeConfig);
    fakeConfig.seed({ body: { snoozeNotificationsEnabled: true } });
    getSnoozeStub = sinon.stub(SnoozeNotifications, 'getSnoozeStatus');
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    sinon.restore();
  });

  it('returns 400 Disabled when config has snoozeNotificationsEnabled=false', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { snoozeNotificationsEnabled: false } });

    const result = await handleSnoozeNotificationsLatest({
      method: 'GET',
      query: { buildTimestamp: BUILD_TIMESTAMP },
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Disabled' },
    });
    expect(getSnoozeStub.called).to.be.false;
  });

  it('returns 405 Method Not Allowed for non-GET methods', async () => {
    const result = await handleSnoozeNotificationsLatest({
      method: 'POST',
      query: { buildTimestamp: BUILD_TIMESTAMP },
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 405,
      body: { error: 'Method Not Allowed.' },
    });
    expect(getSnoozeStub.called).to.be.false;
  });

  it('returns 400 with parameter-name message when buildTimestamp is missing', async () => {
    const result = await handleSnoozeNotificationsLatest({
      method: 'GET',
      query: {},
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Missing required parameter: buildTimestamp' },
    });
    expect(getSnoozeStub.called).to.be.false;
  });

  it('returns 500 with the controller response body when getSnoozeStatus sets error', async () => {
    const errorResponse = { status: SnoozeStatus.NONE, error: 'boom' };
    getSnoozeStub.resolves(errorResponse);

    const result = await handleSnoozeNotificationsLatest({
      method: 'GET',
      query: { buildTimestamp: BUILD_TIMESTAMP },
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 500,
      body: errorResponse,
    });
  });

  it('returns 200 ok with the controller response on success', async () => {
    const okResponse = { status: SnoozeStatus.ACTIVE, snooze: { snoozeEndTimeSeconds: 9999 } };
    getSnoozeStub.resolves(okResponse);

    const result = await handleSnoozeNotificationsLatest({
      method: 'GET',
      query: { buildTimestamp: BUILD_TIMESTAMP },
    });

    expect(result).to.deep.equal({ kind: 'ok', data: okResponse });
    expect(getSnoozeStub.calledOnce).to.be.true;
    expect(getSnoozeStub.firstCall.args[0]).to.deep.equal({ buildTimestamp: BUILD_TIMESTAMP });
  });
});
