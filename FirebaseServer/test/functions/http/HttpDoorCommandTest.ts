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
 * The door-command endpoint core. The load-bearing test is
 * `has no route to the door` — everything else here is ordinary gate coverage,
 * but that one is the whole reason this endpoint is safe to deploy today.
 */
import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';

import { handleDoorCommand } from '../../../src/functions/http/DoorCommand';
import { DoorCommandRejection, DoorGateState } from '../../../src/controller/DoorCommandGate';
import { SensorEventType } from '../../../src/model/SensorEvent';
import {
  setImpl as setConfigDBImpl,
  resetImpl as resetConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setSensorDBImpl,
  resetImpl as resetSensorDBImpl,
} from '../../../src/database/SensorEventDatabase';
import {
  setImpl as setAuthImpl,
  resetImpl as resetAuthImpl,
} from '../../../src/controller/AuthService';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeSensorEventDatabase } from '../../fakes/FakeSensorEventDatabase';
import { FakeAuthService } from '../../fakes/FakeAuthService';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
const PUSH_KEY = 'SUPER_SECRET';
const EMAIL = 'owner@example.com';
const TOKEN = 'any-id-token';
const NOW = 1_700_000_000;

function config(overrides: any = {}) {
  return {
    body: {
      buildTimestamp: BUILD_TIMESTAMP,
      remoteButtonPushKey: PUSH_KEY,
      remoteButtonAuthorizedEmails: [EMAIL],
      remoteButtonEnabled: true,
      ...overrides,
    },
  };
}

/** A well-formed request; individual tests override the one field under test. */
function request(overrides: any = {}) {
  return {
    method: 'POST',
    body: { command: 'close' },
    query: {},
    pushKeyHeader: PUSH_KEY,
    googleIdTokenHeader: TOKEN,
    nowSeconds: NOW,
    ...overrides,
  };
}

describe('DoorCommand endpoint core', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeSensor: FakeSensorEventDatabase;
  let fakeAuth: FakeAuthService;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeSensor = new FakeSensorEventDatabase();
    fakeAuth = new FakeAuthService();
    setConfigDBImpl(fakeConfig);
    setSensorDBImpl(fakeSensor);
    setAuthImpl(fakeAuth);
    fakeConfig.seed(config());
    fakeAuth.seedDecoded({ email: EMAIL, email_verified: true });
    // An open door with a fresh check-in: "close" is accepted from here, so any
    // test that gets a refusal got it from the thing it was testing.
    fakeSensor.seed(BUILD_TIMESTAMP, {
      type: SensorEventType.Open,
      checkInTimestampSeconds: NOW - 5,
    });
  });

  afterEach(() => {
    resetConfigDBImpl();
    resetSensorDBImpl();
    resetAuthImpl();
  });

  // --- The reason this endpoint is safe to ship -----------------------------

  it('has no route to the door', () => {
    // The device opens the garage by polling RemoteButtonCommandDatabase. This
    // endpoint must not be able to write there, and the cheapest durable proof
    // is that it does not import it — a value it cannot name is a value it
    // cannot call. When execution is deliberately added, this test is meant to
    // fail; re-point it then, do not delete it.
    const source = fs.readFileSync(
      path.join(__dirname, '../../../src/functions/http/DoorCommand.ts'),
      'utf8',
    );
    const imports = source
      .split('\n')
      .filter((line) => line.trimStart().startsWith('import') || line.includes("from '"));
    const forbidden = imports.filter((line) => line.includes('RemoteButtonCommandDatabase'));
    expect(forbidden, 'DoorCommand.ts must not import the command database').to.deep.equal([]);

    // Positive control: the check above is a substring match over lines that
    // were actually collected, so prove the collection is non-empty and that
    // the same predicate does fire on a name the file really does import.
    expect(imports.length, 'no import lines were read at all').to.be.greaterThan(0);
    expect(
      imports.some((line) => line.includes('SensorEventDatabase')),
      'the import scan cannot see imports it should see',
    ).to.be.true;
  });

  it('never reports having executed anything', async () => {
    const result = await handleDoorCommand(request());
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data.executed).to.be.false;
    expect(result.data.verdict.accepted, 'this fixture should be an accepted command').to.be.true;
    // An ACCEPTED command is the dangerous case: it is the one where a future
    // careless edit would act. Nothing was written anywhere.
    expect(fakeSensor.saved).to.deep.equal([]);
    expect(fakeConfig.saved).to.deep.equal([]);
  });

  // --- The wire shape both clients decode -----------------------------------

  function fixture(name: string): any {
    return JSON.parse(
      fs.readFileSync(path.join(__dirname, `../../../../wire-contracts/doorCommand/${name}.json`), 'utf8'),
    );
  }

  it('matches the accepted-response fixture byte-shape', async () => {
    const result = await handleDoorCommand(request({ body: { command: 'close' } }));
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data).to.deep.equal(fixture('response_accepted'));
  });

  it('matches the rejected-response fixture byte-shape', async () => {
    fakeSensor.seed(BUILD_TIMESTAMP, {
      type: SensorEventType.OpeningTooLong,
      checkInTimestampSeconds: NOW - 5,
    });
    const result = await handleDoorCommand(request({ body: { command: 'open' } }));
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data).to.deep.equal(fixture('response_rejected_stuck'));
  });

  // --- Verdicts -------------------------------------------------------------

  it('accepts close when the door is open', async () => {
    const result = await handleDoorCommand(request({ body: { command: 'close' } }));
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data.verdict.accepted).to.be.true;
    expect(result.data.verdict.rejection).to.equal(null);
    expect(result.data.verdict.doorState).to.equal(DoorGateState.Open);
  });

  it('refuses open when the door is already open, with 200 not 4xx', async () => {
    const result = await handleDoorCommand(request({ body: { command: 'open' } }));
    // A refusal is a real answer to a well-formed question. 4xx is reserved for
    // "you may not ask", which the client must be able to tell apart.
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data.verdict.accepted).to.be.false;
    expect(result.data.verdict.rejection).to.equal(DoorCommandRejection.AlreadyOpen);
  });

  it('lets a stuck door be closed but not opened', async () => {
    fakeSensor.seed(BUILD_TIMESTAMP, {
      type: SensorEventType.OpeningTooLong,
      checkInTimestampSeconds: NOW - 5,
    });
    const close = await handleDoorCommand(request({ body: { command: 'close' } }));
    expect(close.kind === 'ok' && close.data.verdict.accepted, 'close').to.be.true;
    const open = await handleDoorCommand(request({ body: { command: 'open' } }));
    expect(open.kind === 'ok' && open.data.verdict.rejection, 'open')
      .to.equal(DoorCommandRejection.DoorStuck);
  });

  it('refuses everything when the check-in is stale', async () => {
    fakeSensor.seed(BUILD_TIMESTAMP, {
      type: SensorEventType.Open,
      checkInTimestampSeconds: NOW - 3600,
    });
    const result = await handleDoorCommand(request({ body: { command: 'close' } }));
    expect(result.kind).to.equal('ok');
    if (result.kind !== 'ok') return;
    expect(result.data.verdict.checkInStale).to.be.true;
    expect(result.data.verdict.rejection).to.equal(DoorCommandRejection.DoorStateUnknown);
  });

  it('reads the command from the query string too', async () => {
    const result = await handleDoorCommand(request({ body: {}, query: { command: 'close' } }));
    expect(result.kind === 'ok' && result.data.verdict.accepted).to.be.true;
  });

  // --- Request validation ---------------------------------------------------

  it('405 for a non-POST', async () => {
    const result = await handleDoorCommand(request({ method: 'GET' }));
    expect(result).to.deep.equal({ kind: 'error', status: 405, body: { error: 'Method Not Allowed.' } });
  });

  it('400 for a missing or unrecognized command', async () => {
    for (const body of [{}, { command: 'toggle' }, { command: '' }]) {
      const result = await handleDoorCommand(request({ body }));
      expect(result.kind, JSON.stringify(body)).to.equal('error');
      if (result.kind !== 'error') return;
      expect(result.status).to.equal(400);
    }
  });

  it('400 when the remote button is disabled in config', async () => {
    fakeConfig.seed(config({ remoteButtonEnabled: false }));
    const result = await handleDoorCommand(request());
    expect(result).to.deep.equal({ kind: 'error', status: 400, body: { error: 'Disabled' } });
  });

  // --- Auth, mirrored from the push-button endpoint -------------------------

  it('401 without a push key', async () => {
    const result = await handleDoorCommand(request({ pushKeyHeader: undefined }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (key).' } });
  });

  it('403 for the wrong push key', async () => {
    const result = await handleDoorCommand(request({ pushKeyHeader: 'WRONG' }));
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (key).' } });
  });

  it('401 without an id token', async () => {
    const result = await handleDoorCommand(request({ googleIdTokenHeader: undefined }));
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('401 when the token will not verify', async () => {
    fakeAuth.failNextVerify(new Error('bad token'));
    const result = await handleDoorCommand(request());
    // Deliberately different from addRemoteButtonCommand, which lets this throw
    // and become a 500. That quirk is preserved there and not copied here.
    expect(result).to.deep.equal({ kind: 'error', status: 401, body: { error: 'Unauthorized (token).' } });
  });

  it('403 for an email that is not on the allowlist', async () => {
    fakeAuth.seedDecoded({ email: 'stranger@example.com', email_verified: true });
    const result = await handleDoorCommand(request());
    expect(result).to.deep.equal({ kind: 'error', status: 403, body: { error: 'Forbidden (user).' } });
  });

  it('checks auth before it will answer anything about the door', async () => {
    // Order matters: an unauthorized caller must not learn the door's state
    // from a verdict, so the refusal has to come before the sensor read.
    fakeSensor.failNextGetCurrent(new Error('should never be reached'));
    const result = await handleDoorCommand(request({ pushKeyHeader: 'WRONG' }));
    expect(result.kind).to.equal('error');
  });

  it('throws when config has no buildTimestamp, for the wrapper to make a 500', async () => {
    fakeConfig.seed(config({ buildTimestamp: undefined }));
    let threw = false;
    try {
      await handleDoorCommand(request());
    } catch {
      threw = true;
    }
    expect(threw, 'a config with no buildTimestamp must not be silently tolerated').to.be.true;
  });
});
