/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Tests for handleCheckButtonHealth — the 10-min pubsub.
 *
 * Per the doc convention (BUTTON_HEALTH_ARCHITECTURE.md > Test plan):
 * the test class MUST enumerate the state-machine table rows 1:1 by
 * name. Reading this file alongside the doc table verifies completeness
 * in seconds.
 */

import { expect } from 'chai';

import { handleCheckButtonHealth } from '../../../src/functions/pubsub/ButtonHealth';
import {
  setImpl as setButtonHealthDBImpl,
  resetImpl as resetButtonHealthDBImpl,
} from '../../../src/database/ButtonHealthDatabase';
import {
  setImpl as setRemoteButtonRequestDBImpl,
  resetImpl as resetRemoteButtonRequestDBImpl,
} from '../../../src/database/RemoteButtonRequestDatabase';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import {
  setImpl as setButtonHealthFCMImpl,
  resetImpl as resetButtonHealthFCMImpl,
} from '../../../src/controller/fcm/ButtonHealthFCM';
import { FakeButtonHealthDatabase } from '../../fakes/FakeButtonHealthDatabase';
import { FakeRemoteButtonRequestDatabase } from '../../fakes/FakeRemoteButtonRequestDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { FakeButtonHealthFCMService } from '../../fakes/FakeButtonHealthFCMService';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const BUILD_TIMESTAMP = 'Sat Apr 10 23:57:32 2021';

function configEnabled() {
  return {
    body: {
      remoteButtonEnabled: true,
      remoteButtonBuildTimestamp: 'Sat%20Apr%2010%2023%3A57%3A32%202021',
    },
  };
}

describe('handleCheckButtonHealth (pubsub state-machine rows 1:1)', () => {
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

  it('row 1: pubsub fresh poll (<= 60s ago) + prior ONLINE -> ONLINE no-op (no write, no FCM)', async () => {
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now - 30 });
    healthDB.seed(BUILD_TIMESTAMP, { state: 'ONLINE', stateChangedAtSeconds: now - 600 });

    await handleCheckButtonHealth();

    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('row 2: pubsub stale poll (> 60s ago) + prior ONLINE -> OFFLINE + FCM', async () => {
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now - 120 });
    healthDB.seed(BUILD_TIMESTAMP, { state: 'ONLINE', stateChangedAtSeconds: now - 600 });

    await handleCheckButtonHealth();

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('OFFLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('OFFLINE');
  });

  it('row 3: pubsub no poll record + no prior buttonHealth doc -> OFFLINE + FCM', async () => {
    // Bootstrap case: device never polled, no prior health state.
    await handleCheckButtonHealth();

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('OFFLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('OFFLINE');
  });

  // Additional coverage beyond the table — kill switch and bootstrap edges.

  it('kill switch: no-op when remoteButtonEnabled is false', async () => {
    configDB.seed({ body: { remoteButtonEnabled: false } });
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now });

    await handleCheckButtonHealth();

    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });

  it('bootstrap: pubsub fresh poll + no prior health doc -> ONLINE + FCM', async () => {
    // Recovery case: a poll arrived between trigger failure and pubsub run.
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now - 10 });

    await handleCheckButtonHealth();

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('ONLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('ONLINE');
  });

  it('OFFLINE -> ONLINE recovery (the pubsub-side fallback for missed trigger)', async () => {
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now - 10 });
    healthDB.seed(BUILD_TIMESTAMP, { state: 'OFFLINE', stateChangedAtSeconds: now - 1200 });

    await handleCheckButtonHealth();

    expect(healthDB.saved).to.have.length(1);
    expect(healthDB.saved[0][1].state).to.equal('ONLINE');
    expect(fcm.sends).to.have.length(1);
    expect(fcm.sends[0].record.state).to.equal('ONLINE');
  });

  it('preserves stateChangedAtSeconds on no-op writes (OFFLINE -> OFFLINE)', async () => {
    const now = Math.floor(Date.now() / 1000);
    requestDB.seed(BUILD_TIMESTAMP, { [DATABASE_TIMESTAMP_SECONDS_KEY]: now - 600 });
    healthDB.seed(BUILD_TIMESTAMP, { state: 'OFFLINE', stateChangedAtSeconds: now - 1800 });

    await handleCheckButtonHealth();

    // No save, no FCM — and the prior stateChangedAtSeconds is preserved.
    expect(healthDB.saved).to.be.empty;
    expect(fcm.sends).to.be.empty;
  });
});
