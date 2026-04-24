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
 * Unit tests for the extracted pubsubCheckForRemoteButtonErrors
 * handler core. H3 (pubsub portion) of the handler testing plan —
 * see docs/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * The handler has four observable outcomes: missing request,
 * corrupt timestamp, stale request, and fresh request. Each gets a
 * dedicated test plus the A3 throw-on-missing-config contract.
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import * as firebase from 'firebase-admin';

import { handleCheckForRemoteButtonErrors } from '../../../src/functions/pubsub/RemoteButton';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setRemoteButtonRequestDBImpl,
  resetImpl as resetRemoteButtonRequestDBImpl,
} from '../../../src/database/RemoteButtonRequestDatabase';
import {
  setImpl as setRemoteButtonRequestErrorDBImpl,
  resetImpl as resetRemoteButtonRequestErrorDBImpl,
} from '../../../src/database/RemoteButtonRequestErrorDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeRemoteButtonRequestDatabase } from '../../fakes/FakeRemoteButtonRequestDatabase';
import { FakeRemoteButtonRequestErrorDatabase } from '../../fakes/FakeRemoteButtonRequestErrorDatabase';

// Production URL-encoded value. The accessor decodes it — the handler
// sees the decoded form. Using the decoded form here keeps tests
// focused on the handler, not the accessor (ConfigAccessorsTest pins
// that contract).
const REMOTE_BUTTON_BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';

// Frozen "now" — makes the 10-minute staleness threshold deterministic.
const NOW_SECONDS = 1_800_000_000;

const ERROR_THRESHOLD_SECONDS = 60 * 10;

describe('handleCheckForRemoteButtonErrors (pure handler core)', () => {
  let fakeConfig: FakeServerConfigDatabase;
  let fakeRequestDB: FakeRemoteButtonRequestDatabase;
  let fakeErrorDB: FakeRemoteButtonRequestErrorDatabase;

  beforeEach(() => {
    fakeConfig = new FakeServerConfigDatabase();
    fakeRequestDB = new FakeRemoteButtonRequestDatabase();
    fakeErrorDB = new FakeRemoteButtonRequestErrorDatabase();
    setServerConfigDBImpl(fakeConfig);
    setRemoteButtonRequestDBImpl(fakeRequestDB);
    setRemoteButtonRequestErrorDBImpl(fakeErrorDB);
    // Pin Timestamp.now() so the staleness math is reproducible.
    sinon.stub(firebase.firestore.Timestamp, 'now').returns(
      new firebase.firestore.Timestamp(NOW_SECONDS, 0),
    );
    // Config with the production URL-encoded value — the accessor
    // decodes it. The handler sees the decoded string.
    fakeConfig.seed({
      body: { remoteButtonBuildTimestamp: 'Sat%20Apr%2010%2023:57:32%202021' },
    });
  });

  afterEach(() => {
    resetServerConfigDBImpl();
    resetRemoteButtonRequestDBImpl();
    resetRemoteButtonRequestErrorDBImpl();
    sinon.restore();
  });

  it('throws when config has no remoteButtonBuildTimestamp (context: pubsubCheckForRemoteButtonErrors)', async () => {
    fakeConfig.clear();
    fakeConfig.seed({ body: {} });

    let caught: unknown;
    try {
      await handleCheckForRemoteButtonErrors();
    } catch (e) {
      caught = e;
    }
    expect(caught).to.be.instanceOf(Error);
    expect((caught as Error).message).to.match(/pubsubCheckForRemoteButtonErrors.*buildTimestamp missing/);
    expect(fakeErrorDB.saved).to.be.empty;
    expect(fakeRequestDB.saved).to.be.empty;
  });

  it('writes an error entry when no request document exists for the device', async () => {
    // FakeRemoteButtonRequestDatabase.getCurrent returns null for an
    // unseeded buildTimestamp — this is the "device never polled" case.

    await handleCheckForRemoteButtonErrors();

    expect(fakeErrorDB.saved).to.have.lengthOf(1);
    const [savedBuildTimestamp, savedEntry] = fakeErrorDB.saved[0];
    expect(savedBuildTimestamp).to.equal(REMOTE_BUTTON_BUILD_TIMESTAMP);
    expect(savedEntry.error).to.equal(
      'No remote button requests found for build timestamp: ' + REMOTE_BUTTON_BUILD_TIMESTAMP,
    );
  });

  it('writes an error entry when the request lacks a database timestamp (NaN branch)', async () => {
    // Missing FIRESTORE_databaseTimestampSeconds → NaN arithmetic →
    // the dedicated "could not compute" error.
    fakeRequestDB.seed(REMOTE_BUTTON_BUILD_TIMESTAMP, { foo: 'bar' });

    await handleCheckForRemoteButtonErrors();

    expect(fakeErrorDB.saved).to.have.lengthOf(1);
    expect(fakeErrorDB.saved[0][1]).to.deep.equal({
      error: 'Could not compute seconds since last database update',
    });
  });

  it('writes an error entry (with the request attached) when the last update is older than 10 minutes', async () => {
    // One second past the threshold — forces the stale branch.
    const staleSeconds = NOW_SECONDS - ERROR_THRESHOLD_SECONDS - 1;
    const requestDoc = {
      FIRESTORE_databaseTimestampSeconds: staleSeconds,
      queryParams: { session: 'stale-session' },
    };
    fakeRequestDB.seed(REMOTE_BUTTON_BUILD_TIMESTAMP, requestDoc);

    await handleCheckForRemoteButtonErrors();

    expect(fakeErrorDB.saved).to.have.lengthOf(1);
    const savedEntry = fakeErrorDB.saved[0][1];
    expect(savedEntry.error).to.match(
      /Seconds since remote button status was checked is greater than expected: \d+ > 600/,
    );
    // Object.assign(result, remoteButtonRequest) — the original
    // request is merged into the error entry for debugging context.
    expect(savedEntry.FIRESTORE_databaseTimestampSeconds).to.equal(staleSeconds);
    expect(savedEntry.queryParams).to.deep.equal({ session: 'stale-session' });
  });

  it('writes no error entry and logs the all-clear message when the request is fresh', async () => {
    const freshSeconds = NOW_SECONDS - 60; // 1 minute ago — well within threshold.
    fakeRequestDB.seed(REMOTE_BUTTON_BUILD_TIMESTAMP, {
      FIRESTORE_databaseTimestampSeconds: freshSeconds,
    });

    const infoSpy = sinon.spy(console, 'log');

    await handleCheckForRemoteButtonErrors();

    expect(fakeErrorDB.saved).to.be.empty;
    expect(
      infoSpy.calledWith('checkForRemoteButtonErrors did not find any errors'),
      'expected the all-clear log line',
    ).to.be.true;
  });
});
