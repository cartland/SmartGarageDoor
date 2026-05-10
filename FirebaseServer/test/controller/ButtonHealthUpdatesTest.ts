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

import { handleButtonHealthFromPollWrite } from '../../src/controller/ButtonHealthUpdates';
import {
  setImpl as setButtonHealthDBImpl,
  resetImpl as resetButtonHealthDBImpl,
} from '../../src/database/ButtonHealthDatabase';
import {
  setImpl as setRemoteButtonRequestDBImpl,
  resetImpl as resetRemoteButtonRequestDBImpl,
} from '../../src/database/RemoteButtonRequestDatabase';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../src/database/ServerConfigDatabase';
import {
  setImpl as setButtonHealthFCMImpl,
  resetImpl as resetButtonHealthFCMImpl,
} from '../../src/controller/fcm/ButtonHealthFCM';
import { FakeButtonHealthDatabase } from '../fakes/FakeButtonHealthDatabase';
import { FakeRemoteButtonRequestDatabase } from '../fakes/FakeRemoteButtonRequestDatabase';
import { FakeServerConfigDatabase } from '../fakes/FakeServerConfigDatabase';
import { FakeButtonHealthFCMService } from '../fakes/FakeButtonHealthFCMService';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';

/**
 * Server config that enables the button feature with the production
 * remoteButtonBuildTimestamp shape (URL-encoded — matches the prod config
 * since April 2021).
 */
function configEnabled() {
  return {
    body: {
      remoteButtonEnabled: true,
      // The accessor decodeURIComponents this on read; both encoded and decoded
      // forms work for tests since we pass BUILD_TIMESTAMP (decoded) to the
      // handler too.
      remoteButtonBuildTimestamp: 'Sat%20Apr%2010%2023%3A57%3A32%202021',
    },
  };
}

function configDisabled() {
  return {
    body: {
      remoteButtonEnabled: false,
      remoteButtonBuildTimestamp: 'Sat%20Apr%2010%2023%3A57%3A32%202021',
    },
  };
}

describe('handleButtonHealthFromPollWrite', () => {
  let healthDB: FakeButtonHealthDatabase;
  let requestDB: FakeRemoteButtonRequestDatabase;
  let configDB: FakeServerConfigDatabase;
  let fcm: FakeButtonHealthFCMService;

  beforeEach(() => {
    healthDB = new FakeButtonHealthDatabase();
    requestDB = new FakeRemoteButtonRequestDatabase();
    configDB = new FakeServerConfigDatabase();
    fcm = new FakeButtonHealthFCMService();
    setButtonHealthDBImpl(healthDB);
    setRemoteButtonRequestDBImpl(requestDB);
    setServerConfigDBImpl(configDB);
    setButtonHealthFCMImpl(fcm);
    configDB.seed(configEnabled());
  });

  afterEach(() => {
    resetButtonHealthDBImpl();
    resetRemoteButtonRequestDBImpl();
    resetServerConfigDBImpl();
    resetButtonHealthFCMImpl();
  });

  it('no-ops when buildTimestamp is missing', async () => {
    await handleButtonHealthFromPollWrite('');
    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('no-ops when remoteButtonEnabled is false (kill switch)', async () => {
    configDB.seed(configDisabled());
    requestDB.seed(BUILD_TIMESTAMP, {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: Math.floor(Date.now() / 1000),
    });

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('no-ops when buildTimestamp does not match server config', async () => {
    requestDB.seed('OTHER_DEVICE', {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: Math.floor(Date.now() / 1000),
    });

    await handleButtonHealthFromPollWrite('OTHER_DEVICE');

    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('writes ONLINE + sends FCM on first poll for this buildTimestamp (UNKNOWN -> ONLINE)', async () => {
    const nowSeconds = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: nowSeconds });

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][0]).to.equal(BUILD_TIMESTAMP);
    expect(healthDB.saved[0][1].state).to.equal('ONLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('ONLINE');
    expect(fcm.sends[0].lastPollAtSeconds).to.equal(nowSeconds);
  });

  it('writes ONLINE + sends FCM on OFFLINE -> ONLINE recovery', async () => {
    const nowSeconds = Math.floor(Date.now() / 1000);
    healthDB.seed(BUILD_TIMESTAMP, {
      state: 'OFFLINE',
      stateChangedAtSeconds: nowSeconds - 1200,
    });
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: nowSeconds });

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('ONLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('ONLINE');
    expect(fcm.sends[0].lastPollAtSeconds).to.equal(nowSeconds);
  });

  it('does NOT write or send FCM on ONLINE -> ONLINE no-op', async () => {
    const nowSeconds = Math.floor(Date.now() / 1000);
    healthDB.seed(BUILD_TIMESTAMP, {
      state: 'ONLINE',
      stateChangedAtSeconds: nowSeconds - 100,
    });
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: nowSeconds });

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('re-reads RemoteButtonRequestDatabase rather than trusting stale event payload', async () => {
    // The trigger handler doesn't take the timestamp as a parameter — it always
    // re-reads. So even if a Cloud Functions retry fires LONG after the original
    // poll, the handler sees whatever the latest poll wrote (which is stored
    // separately by the unrelated httpRemoteButton handler).
    const nowSeconds = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: nowSeconds });
    // No prior buttonHealth doc.

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    // Fresh poll → ONLINE.
    expect(healthDB.saved[0][1].state).to.equal('ONLINE');

    // Now imagine the handler fires again after the device went silent for
    // an hour — the fresh re-read would see the OLD poll record (stale).
    healthDB.clear();
    fcm.clear();
    requestDB.seed(BUILD_TIMESTAMP, {
      [DATABASE_TIMESTAMP_SECONDS_KEY]: nowSeconds - 3600,  // an hour stale
    });
    healthDB.seed(BUILD_TIMESTAMP, { state: 'ONLINE', stateChangedAtSeconds: nowSeconds - 100 });

    await handleButtonHealthFromPollWrite(BUILD_TIMESTAMP);

    // Now the latest poll is stale → handler computes OFFLINE, writes the transition.
    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('OFFLINE');
  });
});
