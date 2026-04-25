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
 * Unit tests for the extracted httpRemoteButton device-poll handler
 * core. H3 (HTTP poll) of docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * The poll endpoint has no auth (ESP32 polls it without credentials).
 * Tests cover the ack-token state machine's three "clear the pending
 * command" branches plus the asymmetric post-save re-read vs
 * pre-save return (flagged by the behavior reviewer) and the
 * always-save-the-request-for-logging invariant.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import * as firebase from 'firebase-admin';

import { handleRemoteButtonPoll } from '../../../src/functions/http/RemoteButton';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setRemoteButtonRequestDBImpl,
  resetImpl as resetRemoteButtonRequestDBImpl,
} from '../../../src/database/RemoteButtonRequestDatabase';
import {
  setImpl as setRemoteButtonCommandDBImpl,
  resetImpl as resetRemoteButtonCommandDBImpl,
} from '../../../src/database/RemoteButtonCommandDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeRemoteButtonRequestDatabase } from '../../fakes/FakeRemoteButtonRequestDatabase';
import { FakeRemoteButtonCommandDatabase } from '../../fakes/FakeRemoteButtonCommandDatabase';

const BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';
const NOW_SECONDS = 1_800_000_000;
const TIMEOUT_SECONDS = 60;
const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe('handleRemoteButtonPoll (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeRequestDB: FakeRemoteButtonRequestDatabase;
  let fakeCommandDB: FakeRemoteButtonCommandDatabase;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeRequestDB = new FakeRemoteButtonRequestDatabase();
    fakeCommandDB = new FakeRemoteButtonCommandDatabase();
    setServerConfigDBImpl(fakeConfig);
    setRemoteButtonRequestDBImpl(fakeRequestDB);
    setRemoteButtonCommandDBImpl(fakeCommandDB);
    sinon.stub(firebase.firestore.Timestamp, 'now').returns(
      new firebase.firestore.Timestamp(NOW_SECONDS, 0),
    );
    fakeConfig.seed({ body: { remoteButtonEnabled: true } });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetRemoteButtonRequestDBImpl();
    resetRemoteButtonCommandDBImpl();
    sinon.restore();
  });

  it('returns 400 Disabled when remoteButtonEnabled is false', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: { remoteButtonEnabled: false } });

    const result = await handleRemoteButtonPoll({ query: {}, body: {} });

    expect(result).to.deep.equal({
      kind: 'error',
      status: 400,
      body: { error: 'Disabled' },
    });
    // No save-for-logging when disabled — verify we bail before the save.
    expect(fakeRequestDB.saved).to.be.empty;
    expect(fakeCommandDB.saved).to.be.empty;
  });

  it('always saves the request for logging (even when no command is pending)', async () => {
    const result = await handleRemoteButtonPoll({
      query: { buildTimestamp: BUILD_TIMESTAMP, session: 'client-sess' },
      body: { payload: 'x' },
    });

    expect(fakeRequestDB.saved).to.have.lengthOf(1);
    const [savedBt, savedData] = fakeRequestDB.saved[0];
    expect(savedBt).to.equal(BUILD_TIMESTAMP);
    expect(savedData.queryParams).to.deep.equal({
      buildTimestamp: BUILD_TIMESTAMP,
      session: 'client-sess',
    });
    expect(savedData.body).to.deep.equal({ payload: 'x' });
    expect(savedData.session).to.equal('client-sess');
    expect(savedData.buildTimestamp).to.equal(BUILD_TIMESTAMP);
    // With no prior command, the response body is the null/undefined
    // return of getCurrent — the poll was just for logging.
    expect(result).to.deep.equal({ kind: 'ok', data: null });
  });

  it('generates a v4 UUID session when the query omits one', async () => {
    await handleRemoteButtonPoll({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
    });

    expect(fakeRequestDB.saved[0][1].session).to.match(UUID_V4_RE);
  });

  it('preserves the "missing buildTimestamp passes through as undefined" quirk', async () => {
    // The pre-extraction code had an empty `else { // Skip. }` so a
    // missing buildTimestamp flowed downstream and the request was
    // saved under key `undefined`. Preserved byte-for-byte.
    await handleRemoteButtonPoll({ query: {}, body: {} });

    expect(fakeRequestDB.saved).to.have.lengthOf(1);
    expect(fakeRequestDB.saved[0][0]).to.equal(undefined as any);
    expect(fakeRequestDB.saved[0][1]).to.not.have.property('buildTimestamp');
  });

  it('returns oldCommand pre-save when no clear-condition fires (fresh pending command, no matching ack)', async () => {
    // Seed a pending command: fresh (now), has an ack token the client
    // is NOT echoing back. None of the three clear conditions fire, so
    // the handler returns the stored command UNCHANGED and does NOT
    // re-read. The returned object is `oldCommand` by reference.
    const pendingCommand = {
      buttonAckToken: 'server-issued-token',
      FIRESTORE_databaseTimestampSeconds: NOW_SECONDS - 5,
    };
    fakeCommandDB.seed(BUILD_TIMESTAMP, pendingCommand);

    const result = await handleRemoteButtonPoll({
      query: { buildTimestamp: BUILD_TIMESTAMP, buttonAckToken: 'different-token' },
      body: {},
    });

    expect(result.kind).to.equal('ok');
    if (result.kind === 'ok') {
      expect(result.data).to.equal(pendingCommand);
    }
    expect(fakeCommandDB.saved, 'no save should occur on this branch').to.be.empty;
  });

  it('clears the pending command (condition 2: ack match) and returns the re-read fresh copy', async () => {
    // Seed a pending command with a known ack token. Client echoes it
    // back in the query — condition 2 fires, handler saves a noop and
    // re-reads. The returned value is the noop, not the pre-save
    // pendingCommand.
    const pendingCommand = {
      buttonAckToken: 'server-issued-token',
      FIRESTORE_databaseTimestampSeconds: NOW_SECONDS - 5,
    };
    fakeCommandDB.seed(BUILD_TIMESTAMP, pendingCommand);

    const result = await handleRemoteButtonPoll({
      query: {
        buildTimestamp: BUILD_TIMESTAMP,
        buttonAckToken: 'server-issued-token',
        session: 'my-session',
      },
      body: {},
    });

    expect(fakeCommandDB.saved).to.have.lengthOf(1);
    const [savedBt, savedCommand] = fakeCommandDB.saved[0];
    expect(savedBt).to.equal(BUILD_TIMESTAMP);
    expect(savedCommand.buttonAckToken).to.equal('');
    expect(savedCommand.commandAcknowledged).to.equal(true);
    expect(savedCommand.commandDidNotContainAckToken).to.equal(false);
    expect(savedCommand.commandTimeout).to.equal(false);
    expect(savedCommand.oldAckToken).to.equal('server-issued-token');
    expect(savedCommand.session).to.equal('my-session');
    // Returned = fresh getCurrent() after save. Our FakeRemoteButtonCommandDatabase
    // returns the last saved value, so result.data === savedCommand.
    if (result.kind === 'ok') {
      expect(result.data).to.equal(savedCommand);
    }
  });

  it('clears the pending command (condition 3: timeout) even without a matching ack', async () => {
    // Pending command is older than REMOTE_BUTTON_COMMAND_TIMEOUT_SECONDS (60s).
    // Client doesn't send an ack token. Condition 3 fires: replace the
    // stale command.
    const pendingCommand = {
      buttonAckToken: 'stale-token',
      FIRESTORE_databaseTimestampSeconds: NOW_SECONDS - TIMEOUT_SECONDS - 1,
    };
    fakeCommandDB.seed(BUILD_TIMESTAMP, pendingCommand);

    await handleRemoteButtonPoll({
      query: { buildTimestamp: BUILD_TIMESTAMP },
      body: {},
    });

    expect(fakeCommandDB.saved).to.have.lengthOf(1);
    expect(fakeCommandDB.saved[0][1].commandTimeout).to.equal(true);
    expect(fakeCommandDB.saved[0][1].commandAcknowledged).to.equal(false);
  });

  it('does NOT save a noop when there is no pending command (oldAckToken is empty — guard trips)', async () => {
    // No seed in the command DB — getCurrent returns null.
    // commandDoesNotContainAckToken is true (condition 1), but the
    // outer guard `oldAckToken !== ''` is false (oldAckToken defaults
    // to '' via `?? ''`), so the else branch runs: no save, return
    // oldCommand (null). This is the exact "first poll on a fresh
    // device" path.
    const result = await handleRemoteButtonPoll({
      query: { buildTimestamp: BUILD_TIMESTAMP, buttonAckToken: 'some-token' },
      body: {},
    });

    expect(fakeCommandDB.saved).to.be.empty;
    expect(result).to.deep.equal({ kind: 'ok', data: null });
  });
});
