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
 * Fake-based tests for src/controller/fcm/ResolvedNotificationFCM.ts.
 *
 * Covers the additive resolved-on-close orchestration: the live flag gate,
 * "was a warning sent this episode" decision via the dedup marker, the
 * single-use consume, the stale-marker guard, and send/consume sequencing.
 *
 * Fakes:
 *  - FakeServerConfigDatabase — controls the resolvedOnCloseEnabled flag
 *  - FakeNotificationsDatabase — holds the warned-episode dedup marker
 *
 * sinon stub:
 *  - firebase.messaging().send — intercepts outgoing FCM sends
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import * as firebase from 'firebase-admin';
import * as fs from 'fs';
import * as path from 'path';

import {
  SERVICE as ResolvedNotificationFCMService,
  getResolvedMessage,
} from '../../../src/controller/fcm/ResolvedNotificationFCM';
import {
  setImpl as setNotificationsDBImpl,
  resetImpl as resetNotificationsDBImpl,
} from '../../../src/database/NotificationsDatabase';
import {
  setImpl as setServerConfigDBImpl,
  resetImpl as resetServerConfigDBImpl,
} from '../../../src/database/ServerConfigDatabase';
import { FakeNotificationsDatabase } from '../../fakes/FakeNotificationsDatabase';
import { FakeServerConfigDatabase } from '../../fakes/FakeServerConfigDatabase';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
const EXPECTED_V2_TOPIC = 'door_open_v2-Sat.Mar.13.14.45.00.2021';

const OPEN_TS = 1_800_000_000;
const CLOSE_TS = OPEN_TS + 14 * 60; // 14 minutes later — a normal warned episode.

// Shared wire-contract fixture — the SAME file the Android FcmPayloadParsing
// test decodes. A unilateral rename of any key breaks this deep-equal OR the
// Android strict-mode decode.
const RESOLVED_FIXTURE = JSON.parse(
  fs.readFileSync(
    path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'openDoorResolved', 'payload_resolved.json'),
    'utf8',
  ),
);

function closedEventAt(timestampSeconds: number): SensorEvent {
  return {
    type: SensorEventType.Closed,
    timestampSeconds,
    message: '',
    checkInTimestampSeconds: timestampSeconds,
  };
}

/** A dedup marker as written by OldDataFCM after a real warning send. */
function warnedMarker(openTimestampSeconds: number): any {
  return {
    notificationCurrentEvent: {
      type: SensorEventType.Open,
      timestampSeconds: openTimestampSeconds,
      message: '',
      checkInTimestampSeconds: openTimestampSeconds,
    },
    message: { topic: 'door_open-anything' },
  };
}

describe('sendFCMForResolvedDoor (via fakes)', () => {
  let fakeNotifications: FakeNotificationsDatabase;
  let fakeConfig: FakeServerConfigDatabase;
  let sendStub: sinon.SinonStub;

  before(() => {
    if (firebase.apps.length === 0) {
      firebase.initializeApp({ projectId: 'test-project' });
    }
  });

  beforeEach(() => {
    fakeNotifications = new FakeNotificationsDatabase();
    fakeConfig = new FakeServerConfigDatabase();
    setNotificationsDBImpl(fakeNotifications);
    setServerConfigDBImpl(fakeConfig);
    sendStub = sinon.stub(firebase.messaging(), 'send').resolves('mock-message-id');
  });

  afterEach(() => {
    resetNotificationsDBImpl();
    resetServerConfigDBImpl();
    sinon.restore();
  });

  const enableFlag = () => fakeConfig.seed({ body: { resolvedOnCloseEnabled: true } });

  // --- Flag gate ---

  it('does nothing when the flag is off, even with a valid warned marker', async () => {
    fakeConfig.seed({ body: { resolvedOnCloseEnabled: false } });
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
    expect(fakeNotifications.saved).to.be.empty;
  });

  it('does nothing when the flag key is missing (fail-safe off)', async () => {
    fakeConfig.seed({ body: {} });
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
  });

  // --- "Was a warning sent this episode?" ---

  it('does not send when there is no marker (no warning this episode)', async () => {
    enableFlag();
    // No marker seeded → getCurrent returns {}.

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
    expect(fakeNotifications.saved).to.be.empty;
  });

  it('does not send when the marker was already resolved (single-use)', async () => {
    enableFlag();
    const consumed = warnedMarker(OPEN_TS);
    consumed.resolvedSent = true;
    fakeNotifications.seed(BUILD_TIMESTAMP, consumed);

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
  });

  // --- Happy path ---

  it('sends the data-only resolved and consumes the marker on a warned close', async () => {
    enableFlag();
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    // Sent.
    expect(result).to.not.be.null;
    expect(sendStub.calledOnce).to.be.true;
    const sent = sendStub.firstCall.args[0];
    expect(sent.topic).to.equal(EXPECTED_V2_TOPIC);
    expect(sent).to.not.have.property('notification'); // data-only, never a heads-up payload
    expect(sent.data.kind).to.equal('open_door_resolved');
    expect(sent.data.openTimestampSeconds).to.equal(String(OPEN_TS));
    expect(sent.data.closeTimestampSeconds).to.equal(String(CLOSE_TS));
    expect(sent.android.collapse_key).to.equal('door_not_closed');

    // Consumed: marker re-saved with resolvedSent = true, warned event preserved.
    expect(fakeNotifications.saved).to.have.length(1);
    const [savedBt, savedData] = fakeNotifications.saved[0];
    expect(savedBt).to.equal(BUILD_TIMESTAMP);
    expect(savedData.resolvedSent).to.equal(true);
    expect(savedData.notificationCurrentEvent.timestampSeconds).to.equal(OPEN_TS);
  });

  it('a second close after a resolved does not fire again (consume sticks)', async () => {
    enableFlag();
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const first = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );
    const second = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS + 5),
    );

    expect(first).to.not.be.null;
    expect(second).to.be.null;
    expect(sendStub.calledOnce).to.be.true; // only the first sent
  });

  // --- Stale-marker guard ---

  it('does not send when the marker is stale (duration beyond the cap)', async () => {
    enableFlag();
    const eightDaysAgo = CLOSE_TS - 8 * 24 * 60 * 60;
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(eightDaysAgo));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
    expect(fakeNotifications.saved).to.be.empty; // not consumed; next warning overwrites it
  });

  it('does not send when the close timestamp precedes the open (non-positive duration)', async () => {
    enableFlag();
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(OPEN_TS - 10),
    );

    expect(result).to.be.null;
    expect(sendStub.called).to.be.false;
  });

  // --- Send failure ---

  it('does not consume the marker when the FCM send fails (a duplicate close can retry)', async () => {
    enableFlag();
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));
    sendStub.rejects(new Error('FCM unavailable'));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.be.null;
    expect(sendStub.calledOnce).to.be.true;
    expect(fakeNotifications.saved).to.be.empty; // marker left un-consumed
  });

  // --- Pure message builder ---

  describe('getResolvedMessage', () => {
    it('builds a data-only payload on the v2 topic matching the wire-contract fixture', () => {
      const message = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, CLOSE_TS);
      expect(message.topic).to.equal(EXPECTED_V2_TOPIC);
      expect(message).to.not.have.property('notification');
      expect(message.data).to.deep.equal(RESOLVED_FIXTURE);
      expect(message.android.collapse_key).to.equal('door_not_closed');
    });
  });
});
