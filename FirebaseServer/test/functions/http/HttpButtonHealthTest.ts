/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';

import { handleButtonHealth } from '../../../src/functions/http/ButtonHealth';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setButtonHealthDBImpl,
  resetImpl as resetButtonHealthDBImpl,
} from '../../../src/database/ButtonHealthDatabase';
import {
  setImpl as setRemoteButtonRequestDBImpl,
  resetImpl as resetRemoteButtonRequestDBImpl,
} from '../../../src/database/RemoteButtonRequestDatabase';
import {
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeButtonHealthDatabase } from '../../fakes/FakeButtonHealthDatabase';
import { FakeRemoteButtonRequestDatabase } from '../../fakes/FakeRemoteButtonRequestDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';

const ALLOWED_EMAIL = 'allowed@example.com';
const DENIED_EMAIL = 'denied@example.com';
const PUSH_KEY = 'test-push-key';
const OK_TOKEN = 'google-id-token-xyz';
const BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';

// Wire-contract fixtures shared with Android's KtorButtonHealthDataSourceTest.
const FIXTURE_DIR = path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'buttonHealth');
const ONLINE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_online.json'), 'utf8'),
);
const OFFLINE_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_offline.json'), 'utf8'),
);
const UNKNOWN_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_unknown.json'), 'utf8'),
);
const UNAUTHORIZED_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_unauthorized.json'), 'utf8'),
);
const FORBIDDEN_USER_FIXTURE = JSON.parse(
  fs.readFileSync(path.join(FIXTURE_DIR, 'response_forbidden_user.json'), 'utf8'),
);

// Pinned for deterministic deep-equal against the wire-contract fixtures.
const FIXTURE_LAST_POLL_ONLINE = 1730000500;
const FIXTURE_LAST_POLL_OFFLINE = 1729999700;
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

describe('handleButtonHealth (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeAuth: FakeAuthService;
  let fakeHealthDB: FakeButtonHealthDatabase;
  let fakeRequestDB: FakeRemoteButtonRequestDatabase;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeAuth = new FakeAuthService();
    fakeHealthDB = new FakeButtonHealthDatabase();
    fakeRequestDB = new FakeRemoteButtonRequestDatabase();
    setServerConfigDBImpl(fakeConfig);
    setAuthServiceImpl(fakeAuth);
    setButtonHealthDBImpl(fakeHealthDB);
    setRemoteButtonRequestDBImpl(fakeRequestDB);
    fakeConfig.seed({
      body: {
        remoteButtonEnabled: true,
        remoteButtonPushKey: PUSH_KEY,
        remoteButtonAuthorizedEmails: [ALLOWED_EMAIL],
      },
    });
    fakeAuth.seedDecoded({ email: ALLOWED_EMAIL });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetAuthServiceImpl();
    resetButtonHealthDBImpl();
    resetRemoteButtonRequestDBImpl();
  });

  const happyInput = (overrides: any = {}) => ({
    query: { buildTimestamp: BUILD_TIMESTAMP },
    pushKeyHeader: PUSH_KEY,
    googleIdTokenHeader: OK_TOKEN,
    ...overrides,
  });

  // ---- Auth chain (mirrors handleAddRemoteButtonCommand byte-for-byte) ----

  it('returns 400 Disabled when remoteButtonEnabled is false', async () => {
    fakeConfig.seed({ body: { remoteButtonEnabled: false } });
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal({ kind: 'error', status: 400, body: { error: 'Disabled' } });
  });

  it('returns 401 Unauthorized (key) when push-key header is missing', async () => {
    const result = await handleButtonHealth(happyInput({ pushKeyHeader: undefined }));
    expect(result.kind).to.equal('error');
    expect(result).to.deep.include({ status: 401 });
  });

  it('returns 403 Forbidden (key) when push-key header does not match', async () => {
    const result = await handleButtonHealth(happyInput({ pushKeyHeader: 'wrong-key' }));
    expect(result.kind).to.equal('error');
    expect(result).to.deep.include({ status: 403 });
  });

  it('returns 401 Unauthorized (token) when googleIdToken header is missing', async () => {
    const result = await handleButtonHealth(happyInput({ googleIdTokenHeader: undefined }));
    expect(result.kind).to.equal('error');
    expect(result).to.deep.equal(
      { kind: 'error', status: 401, body: UNAUTHORIZED_FIXTURE },
    );
  });

  it('returns 403 Forbidden (user) when email is not in allowlist', async () => {
    fakeAuth.seedDecoded({ email: DENIED_EMAIL });
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal(
      { kind: 'error', status: 403, body: FORBIDDEN_USER_FIXTURE },
    );
  });

  it('propagates verifyIdToken throw to the wrapper (matches handleAddRemoteButtonCommand)', async () => {
    fakeAuth.failNextVerify(new Error('bad token'));
    let threw = false;
    try {
      await handleButtonHealth(happyInput());
    } catch (_e) {
      threw = true;
    }
    expect(threw).to.equal(true);
  });

  // ---- Param validation ----

  it('returns 400 when buildTimestamp is missing', async () => {
    const result = await handleButtonHealth(happyInput({ query: {} }));
    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Missing required parameter: buildTimestamp' },
    });
  });

  it('returns 400 when buildTimestamp is empty', async () => {
    const result = await handleButtonHealth(happyInput({ query: { buildTimestamp: '' } }));
    expect(result.kind).to.equal('error');
    expect(result).to.deep.include({ status: 400 });
  });

  // ---- Success cases (deep-equal against shared wire-contract fixtures) ----

  it('returns the ONLINE fixture when buttonHealthCurrent has ONLINE', async () => {
    fakeHealthDB.seed(BUILD_TIMESTAMP, {
      state: 'ONLINE',
      stateChangedAtSeconds: 1730000000,
    });
    fakeRequestDB.seed(BUILD_TIMESTAMP, {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: FIXTURE_LAST_POLL_ONLINE,
    });
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: ONLINE_FIXTURE });
  });

  it('returns the OFFLINE fixture when buttonHealthCurrent has OFFLINE', async () => {
    fakeHealthDB.seed(BUILD_TIMESTAMP, {
      state: 'OFFLINE',
      stateChangedAtSeconds: 1730000000,
    });
    fakeRequestDB.seed(BUILD_TIMESTAMP, {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: FIXTURE_LAST_POLL_OFFLINE,
    });
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: OFFLINE_FIXTURE });
  });

  it('returns the UNKNOWN fixture when no buttonHealthCurrent doc exists', async () => {
    // No fakeHealthDB.seed and no fakeRequestDB.seed — neither doc exists.
    // Both stateChangedAtSeconds and lastPollAtSeconds are null on the wire.
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal({ kind: 'ok', data: UNKNOWN_FIXTURE });
  });

  it('reads lastPollAtSeconds from RemoteButtonRequestDatabase, not from the health doc', async () => {
    // The health doc's stateChangedAtSeconds may be hours old (steady ONLINE);
    // lastPollAtSeconds reflects the freshest poll the server has seen.
    const stateChangeTs = 1700000000;
    const freshPollTs = 1730005000;
    fakeHealthDB.seed(BUILD_TIMESTAMP, {
      state: 'ONLINE',
      stateChangedAtSeconds: stateChangeTs,
    });
    fakeRequestDB.seed(BUILD_TIMESTAMP, {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: freshPollTs,
    });
    const result = await handleButtonHealth(happyInput());
    expect(result).to.deep.equal({
      kind: 'ok',
      data: {
        buildTimestamp: BUILD_TIMESTAMP,
        buttonState: 'ONLINE',
        stateChangedAtSeconds: stateChangeTs,
        lastPollAtSeconds: freshPollTs,
      },
    });
  });

  it('returns lastPollAtSeconds: null when no poll record exists yet (edge: device has never polled)', async () => {
    fakeHealthDB.seed(BUILD_TIMESTAMP, {
      state: 'OFFLINE',
      stateChangedAtSeconds: 1730000000,
    });
    // No requestDB.seed — RemoteButtonRequestDatabase.getCurrent returns null.
    const result = await handleButtonHealth(happyInput());
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data.lastPollAtSeconds).to.equal(null);
  });
});
