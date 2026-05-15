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
 * H2 of docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md + audit follow-up
 * (auth chain).
 *
 * The fakes cover config + sensor-event reads + auth. sendFCMForOldData
 * is sinon-stubbed because its orchestration (snooze lookup, dedupe,
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
import {
  setImpl as setAuthServiceImpl,
  resetImpl as resetAuthServiceImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeSensorEventDatabase } from '../../fakes/FakeSensorEventDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';
import * as OldDataFCM from '../../../src/controller/fcm/OldDataFCM';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
const ALLOWED_EMAIL = 'allowed@example.com';
const DENIED_EMAIL = 'denied@example.com';
const PUSH_KEY = 'test-push-key';
const OK_TOKEN = 'google-id-token-xyz';

describe('handleCheckForOpenDoorsRequest (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeSensorDB: FakeSensorEventDatabase;
  let fakeAuth: FakeAuthService;
  let sendFcmStub: sinon.SinonStub;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeSensorDB = new FakeSensorEventDatabase();
    fakeAuth = new FakeAuthService();
    setServerConfigDBImpl(fakeConfig);
    setSensorEventDBImpl(fakeSensorDB);
    setAuthServiceImpl(fakeAuth);
    sendFcmStub = sinon.stub(OldDataFCM, 'sendFCMForOldData');
    fakeConfig.seed({
      body: {
        buildTimestamp: BUILD_TIMESTAMP,
        remoteButtonPushKey: PUSH_KEY,
        remoteButtonAuthorizedEmails: [ALLOWED_EMAIL],
      },
    });
    fakeAuth.seedDecoded({ email: ALLOWED_EMAIL });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetSensorEventDBImpl();
    resetAuthServiceImpl();
    sinon.restore();
  });

  it('returns 401 Unauthorized when push key header is missing', async () => {
    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: undefined,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 401,
      body: { error: 'Unauthorized (key).' },
    });
    expect(sendFcmStub.called).to.be.false;
  });

  it('returns 403 Forbidden when push key does not match', async () => {
    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: 'wrong-key',
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 403,
      body: { error: 'Forbidden (key).' },
    });
    expect(sendFcmStub.called).to.be.false;
  });

  it('returns 401 Unauthorized when id-token header is missing', async () => {
    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: PUSH_KEY,
      googleIdTokenHeader: undefined,
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 401,
      body: { error: 'Unauthorized (token).' },
    });
    expect(sendFcmStub.called).to.be.false;
  });

  it('returns 403 Forbidden (user) when email is not in allowlist', async () => {
    fakeAuth.seedDecoded({ email: DENIED_EMAIL });

    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 403,
      body: { error: 'Forbidden (user).' },
    });
    expect(sendFcmStub.called).to.be.false;
  });

  it('throws when production config has no buildTimestamp', async () => {
    // The A3 contract — missing buildTimestamp surfaces as an ERROR
    // log + thrown Error, not a silent fallback. The HTTP wrapper
    // catches this and returns 500.
    fakeConfig.clear();
    fakeConfig.seed({
      body: {
        remoteButtonPushKey: PUSH_KEY,
        remoteButtonAuthorizedEmails: [ALLOWED_EMAIL],
      },
    });

    let caught: unknown;
    try {
      await handleCheckForOpenDoorsRequest({
        pushKeyHeader: PUSH_KEY,
        googleIdTokenHeader: OK_TOKEN,
      });
    } catch (e) {
      caught = e;
    }
    expect(caught).to.be.instanceOf(Error);
    expect((caught as Error).message).to.match(/httpCheckForOpenDoors.*buildTimestamp missing/);
    expect(sendFcmStub.called).to.be.false;
  });

  it('passes buildTimestamp + current event data to sendFCMForOldData', async () => {
    const eventData = { currentEvent: { timestampSeconds: 12345 } };
    fakeSensorDB.seed(BUILD_TIMESTAMP, eventData);
    sendFcmStub.resolves(null);

    await handleCheckForOpenDoorsRequest({
      pushKeyHeader: PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(sendFcmStub.calledOnce).to.be.true;
    const [passedBuildTimestamp, passedEventData] = sendFcmStub.firstCall.args;
    expect(passedBuildTimestamp).to.equal(BUILD_TIMESTAMP);
    expect(passedEventData).to.deep.equal(eventData);
  });

  it('returns the value produced by sendFCMForOldData', async () => {
    const sentinel = { topic: 'door-test', data: { ok: true } };
    sendFcmStub.resolves(sentinel);

    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: PUSH_KEY,
      googleIdTokenHeader: OK_TOKEN,
    });

    expect(result).to.deep.equal({ kind: 'ok', data: sentinel });
  });
});
