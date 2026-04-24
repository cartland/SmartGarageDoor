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
 * Unit tests for the extracted httpSnoozeNotificationsRequest
 * handler core. H4 (write) of
 * docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * Key contrast with handleAddRemoteButtonCommand: the verifyIdToken
 * call here IS wrapped in try/catch → returns 401 on throw (not 500
 * by propagation).
 */

import { expect } from 'chai';
import * as sinon from 'sinon';

import { handleSnoozeNotificationsRequest } from '../../../src/functions/http/Snooze';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';
import {
  setupAuthHappyPath,
  DEFAULT_PUSH_KEY,
} from '../../helpers/AuthTestHelper';
import * as SnoozeNotifications from '../../../src/controller/SnoozeNotifications';
import { SnoozeStatus } from '../../../src/model/SnoozeRequest';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
const OK_TOKEN = 'google-id-token-xyz';

const FULL_QUERY = {
  buildTimestamp: BUILD_TIMESTAMP,
  snoozeDuration: '2h',
  snoozeEventTimestamp: '12345',
};

describe('handleSnoozeNotificationsRequest (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeAuth: FakeAuthService;
  let submitStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeAuth = new FakeAuthService();
    setServerConfigDBImpl(fakeConfig);
    setAuthServiceImpl(fakeAuth);
    setupAuthHappyPath(fakeConfig, fakeAuth);
    submitStub = sinon.stub(SnoozeNotifications, 'submitSnoozeNotificationsRequest');
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetAuthServiceImpl();
    sinon.restore();
  });

  const happyInput = (overrides: any = {}) => ({
    method: 'POST',
    query: FULL_QUERY,
    pushKeyHeader: DEFAULT_PUSH_KEY,
    googleIdTokenHeader: OK_TOKEN,
    ...overrides,
  });

  it('returns 400 Disabled when snoozeNotificationsEnabled is false', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { snoozeNotificationsEnabled: false } });

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 400, body: { error: 'Disabled' } });
  });

  it('returns 405 Method Not Allowed for non-POST methods', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({ method: 'GET' }));
    expect(result).to.deep.equal({ kind: 'error', status: 405, body: { error: 'Method Not Allowed.' } });
  });

  it('returns 401 Unauthorized (key) when pushKey header is missing', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({ pushKeyHeader: undefined }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (key).' } });
  });

  it('returns 403 Forbidden (key) when pushKey does not match config', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({ pushKeyHeader: 'wrong' }));
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (key).' } });
  });

  it('returns 401 Unauthorized (token) when googleIdToken header is missing', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({ googleIdTokenHeader: undefined }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('CATCHES verifyIdToken throws and returns 401 — preserved asymmetry vs AddCommand', async () => {
    // Preserved quirk: Snooze wraps verifyIdToken in try/catch and
    // returns 401 on throw. AddCommand does NOT wrap; throw propagates
    // → 500. Pins the asymmetry.
    fakeAuth.failNextVerify(new Error('auth/invalid-id-token'));

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('returns 403 Forbidden (user) when decoded email is not in the allowlist', async () => {
    fakeAuth.seedDecoded({ email: 'stranger@example.com' });

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (user).' } });
  });

  it('returns 400 with the parameter name when buildTimestamp is missing', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({
      query: { snoozeDuration: '1h', snoozeEventTimestamp: '1' },
    }));
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.status).to.equal(400);
      expect(result.body).to.deep.equal({ error: 'Missing required parameter: buildTimestamp' });
    }
  });

  it('returns 400 when snoozeDuration is missing', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({
      query: { buildTimestamp: BUILD_TIMESTAMP, snoozeEventTimestamp: '1' },
    }));
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.body).to.deep.equal({ error: 'Missing required parameter: snoozeDuration' });
    }
  });

  it('returns 400 when snoozeEventTimestamp is missing', async () => {
    const result = await handleSnoozeNotificationsRequest(happyInput({
      query: { buildTimestamp: BUILD_TIMESTAMP, snoozeDuration: '1h' },
    }));
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.body).to.deep.equal({ error: 'Missing required parameter: snoozeEventTimestamp' });
    }
  });

  it('returns the controller-provided code + response body when submit returns an error', async () => {
    const errorResponse = { error: 'invalid duration', code: 418 };
    submitStub.resolves(errorResponse);

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 418, body: errorResponse });
  });

  it('defaults to 500 when the controller returns an error without a code', async () => {
    const errorResponse = { error: 'unspecified' };
    submitStub.resolves(errorResponse);

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result.kind).to.equal('error');
    if (result.kind === 'error') {
      expect(result.status).to.equal(500);
    }
  });

  it('returns 200 ok with the snooze payload on success', async () => {
    const snooze = { snoozeEndTimeSeconds: 9999, snoozeDuration: '2h' };
    submitStub.resolves({ snooze, status: SnoozeStatus.ACTIVE });

    const result = await handleSnoozeNotificationsRequest(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: snooze });
    // Controller called with the extracted params
    expect(submitStub.calledOnce).to.be.true;
    expect(submitStub.firstCall.args[0]).to.deep.equal(FULL_QUERY);
  });
});
