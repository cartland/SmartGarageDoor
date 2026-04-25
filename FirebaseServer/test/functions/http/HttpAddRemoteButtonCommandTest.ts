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
 * Unit tests for the extracted httpAddRemoteButtonCommand handler
 * core. H3 (HTTP add-command portion) of
 * docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * Tests pin three audit findings:
 *  - L232 missing-return quirk: 400 is returned to the client but
 *    `save(undefined, data)` still fires into Firestore.
 *  - `verifyIdToken` is NOT wrapped in try/catch — a throw propagates
 *    out of the pure function (wrapper catches, returns 500).
 *  - Auth-order byte-identical to httpSnoozeNotificationsRequest.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import * as firebase from 'firebase-admin';

import { handleAddRemoteButtonCommand } from '../../../src/functions/http/RemoteButton';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setRemoteButtonCommandDBImpl,
  resetImpl as resetRemoteButtonCommandDBImpl,
} from '../../../src/database/RemoteButtonCommandDatabase';
import {
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeRemoteButtonCommandDatabase } from '../../fakes/FakeRemoteButtonCommandDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';
import {
  setupAuthHappyPath,
  DEFAULT_PUSH_KEY,
  DEFAULT_EMAIL,
} from '../../helpers/AuthTestHelper';

const BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';
const NOW_SECONDS = 1_800_000_000;
const MIN_PERIOD_SECONDS = 10;

const OK_TOKEN = 'google-id-token-xyz';

describe('handleAddRemoteButtonCommand (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeCommandDB: FakeRemoteButtonCommandDatabase;
  let fakeAuth: FakeAuthService;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeCommandDB = new FakeRemoteButtonCommandDatabase();
    fakeAuth = new FakeAuthService();
    setServerConfigDBImpl(fakeConfig);
    setRemoteButtonCommandDBImpl(fakeCommandDB);
    setAuthServiceImpl(fakeAuth);
    sinon.stub(firebase.firestore.Timestamp, 'now').returns(
      new firebase.firestore.Timestamp(NOW_SECONDS, 0),
    );
    setupAuthHappyPath(fakeConfig, fakeAuth);
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetRemoteButtonCommandDBImpl();
    resetAuthServiceImpl();
    sinon.restore();
  });

  it('returns 400 Disabled when remoteButtonEnabled is false', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { remoteButtonEnabled: false } });

    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });
    expect(result).to.deep.equal({ kind: 'error', status: 400, body: { error: 'Disabled' } });
  });

  it('returns 401 Unauthorized (key) when pushKey header is missing', async () => {
    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
      pushKeyHeader: undefined,
      googleIdTokenHeader: OK_TOKEN,
    });
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (key).' } });
  });

  it('returns 403 Forbidden (key) when pushKey does not match config', async () => {
    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
      pushKeyHeader: 'wrong-key',
      googleIdTokenHeader: OK_TOKEN,
    });
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (key).' } });
  });

  it('returns 401 Unauthorized (token) when googleIdToken header is missing', async () => {
    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: undefined,
    });
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('PROPAGATES (does NOT catch) verifyIdToken errors — preserved asymmetry vs Snooze', async () => {
    // Snooze catches and returns 401. AddCommand does NOT — the throw
    // escapes to the wrapper's outer catch → 500. Tests pin this
    // by asserting the pure function itself throws.
    const boom = new Error('auth/invalid-id-token');
    fakeAuth.failNextVerify(boom);

    let caught: unknown;
    try {
      await handleAddRemoteButtonCommand({
        query: { buildTimestamp: BUILD_TIMESTAMP },
        body: {},
        pushKeyHeader: DEFAULT_PUSH_KEY,
        googleIdTokenHeader: 'bad',
      });
    } catch (e) {
      caught = e;
    }
    expect(caught).to.equal(boom);
  });

  it('returns 403 Forbidden (user) when decoded email is not in the allowlist', async () => {
    fakeAuth.seedDecoded({ email: 'stranger@example.com' });

    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (user).' } });
  });

  it('returns 409 Conflict when the previous command is less than 10s old (rate limit)', async () => {
    fakeCommandDB.seed(BUILD_TIMESTAMP, {
      FIRESTORE_databaseTimestampSeconds: NOW_SECONDS - (MIN_PERIOD_SECONDS - 1),
    });

    const result = await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP, buttonAckToken: 't1' },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });
    expect(result).to.deep.equal({
      kind: 'error',
      status: 409,
      body: { error: 'Conflict (too many recent requests).' },
    });
    expect(fakeCommandDB.saved, 'no new save on rate-limit').to.be.empty;
  });

  it('saves the command with email attached + returns the fresh re-read on the happy path', async () => {
    const result = await handleAddRemoteButtonCommand({
      query: {
        buildTimestamp: BUILD_TIMESTAMP,
        buttonAckToken: 'client-token',
        session: 'my-session',
      },
      body: { trigger: 'android' },
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(fakeCommandDB.saved).to.have.lengthOf(1);
    const [savedBt, savedData] = fakeCommandDB.saved[0];
    expect(savedBt).to.equal(BUILD_TIMESTAMP);
    expect(savedData.buttonAckToken).to.equal('client-token');
    expect(savedData.session).to.equal('my-session');
    expect(savedData.buildTimestamp).to.equal(BUILD_TIMESTAMP);
    expect(savedData.email).to.equal(DEFAULT_EMAIL);
    expect(savedData.queryParams).to.include.keys('buildTimestamp', 'buttonAckToken', 'session');
    expect(savedData.body).to.deep.equal({ trigger: 'android' });
    // 200 with re-read
    if (result.kind === 'ok') {
      expect(result.data).to.equal(savedData);
    } else {
      expect.fail('expected ok result');
    }
  });

  it('preserves the missing-buildTimestamp quirk: returns 400 BUT still save(undefined, data) fires', async () => {
    // The pre-extraction handler sent 400 without returning, so
    // execution continued and save(undefined, data) ran. Preserved.
    const result = await handleAddRemoteButtonCommand({
      query: { buttonAckToken: 'x' },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Missing required parameter: buildTimestamp' },
    });
    // CRITICAL: the save still fires with undefined key. This is the
    // latent bug being preserved byte-for-byte.
    expect(fakeCommandDB.saved).to.have.lengthOf(1);
    expect(fakeCommandDB.saved[0][0]).to.equal(undefined as any);
  });

  it('generates a UUID session when missing (happy path)', async () => {
    await handleAddRemoteButtonCommand({
      query: { buildTimestamp: BUILD_TIMESTAMP, buttonAckToken: 't' },
      body: {},
      pushKeyHeader: DEFAULT_PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    expect(fakeCommandDB.saved[0][1].session).to.match(UUID_V4_RE);
  });
});
