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
import { buildTimestampToFcmTopic } from '../../../src/model/FcmTopic';
import { AndroidMessagePriority } from '../../../src/model/FCM';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
const EXPECTED_V2_TOPIC = 'door_open_v2-Sat.Mar.13.14.45.00.2021';
// The LEGACY door topic old app builds subscribe to. The resolved feature must
// NEVER target this — that is the boundary that keeps the feature isolated to
// new (door_open_v2-) builds. See docs/RESOLVED_NOTIFICATION_PLAN.md.
const LEGACY_DOOR_TOPIC = buildTimestampToFcmTopic(BUILD_TIMESTAMP);

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

  // --- Combined notification+data resolved (relaxed-A, resolvedNotificationPayloadEnabled) ---
  // docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md §9. Requires BOTH flags on.

  it('sends a COMBINED message (notification + shared tag + HIGH priority) when the payload flag is on', async () => {
    fakeConfig.seed({ body: { resolvedOnCloseEnabled: true, resolvedNotificationPayloadEnabled: true } });
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.not.be.null;
    const sent = sendStub.firstCall.args[0];
    // OS-renderable notification block with the shared drawer tag.
    expect(sent.notification.title).to.equal('Resolved: garage door closed');
    expect(sent.notification.body).to.equal('Was open for 14 minutes'); // timezone-free duration
    expect(sent.android.notification.tag).to.equal('garage_door');
    // HIGH delivery priority so the never-woken replacement reaches a dozing
    // device. No notification_priority lever: it can't quiet the all-clear (the
    // HIGH channel importance wins on Android 8+, device gate 2026-07-02), and the
    // buzzing all-clear is accepted — so the field is simply not set.
    expect(sent.android.priority).to.equal(AndroidMessagePriority.HIGH);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((sent.android.notification as any).notification_priority).to.be.undefined;
    // Data block UNCHANGED — still drives the rich device-local foreground body.
    expect(sent.data).to.deep.equal(RESOLVED_FIXTURE);
    expect(sent.topic).to.equal(EXPECTED_V2_TOPIC);
  });

  it('stays DATA-ONLY when resolvedOnCloseEnabled is on but the payload flag is off', async () => {
    fakeConfig.seed({ body: { resolvedOnCloseEnabled: true, resolvedNotificationPayloadEnabled: false } });
    fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

    const result = await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
      BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
    );

    expect(result).to.not.be.null;
    const sent = sendStub.firstCall.args[0];
    expect(sent).to.not.have.property('notification');
    expect(sent.android.priority).to.equal(AndroidMessagePriority.HIGH);
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

  // --- Old-app isolation invariant ---
  // The resolved feature is isolated to NEW builds (door_open_v2- subscribers)
  // purely because it only ever targets the v2 topic. Old builds (< 2.19.0)
  // subscribe only to the legacy door_open- topic, so a message that never goes
  // there can never reach them. These tests pin that invariant so it can't
  // silently regress (e.g. a refactor swapping the topic builder). Combined with
  // the "flag off → no send, no save" test above (which proves server/31 is inert
  // for everyone until the flag is flipped), this is the backwards-compatibility
  // guarantee in code. See docs/RESOLVED_NOTIFICATION_PLAN.md § Isolation guarantee.

  describe('old-app isolation (never targets the legacy door_open- topic)', () => {
    it('sends the resolved ONLY to the v2 topic, never to the legacy door topic', async () => {
      enableFlag();
      fakeNotifications.seed(BUILD_TIMESTAMP, warnedMarker(OPEN_TS));

      await ResolvedNotificationFCMService.sendFCMForResolvedDoor(
        BUILD_TIMESTAMP, closedEventAt(CLOSE_TS),
      );

      expect(sendStub.calledOnce).to.be.true;
      const sentTopic = sendStub.firstCall.args[0].topic;
      expect(sentTopic).to.equal(EXPECTED_V2_TOPIC);
      expect(sentTopic.startsWith('door_open_v2-')).to.be.true;
      expect(sentTopic).to.not.equal(LEGACY_DOOR_TOPIC); // old builds never hear this
    });

    it('getResolvedMessage targets the v2 topic, never the legacy door topic', () => {
      const topic = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, CLOSE_TS).topic;
      expect(topic).to.equal(EXPECTED_V2_TOPIC);
      expect(topic).to.not.equal(LEGACY_DOOR_TOPIC);
    });
  });

  // --- Pure message builder ---

  describe('getResolvedMessage', () => {
    it('builds a data-only payload on the v2 topic matching the wire-contract fixture', () => {
      const message = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, CLOSE_TS);
      expect(message.topic).to.equal(EXPECTED_V2_TOPIC);
      expect(message).to.not.have.property('notification');
      expect(message.data).to.deep.equal(RESOLVED_FIXTURE);
      expect(message.android.collapse_key).to.equal('door_not_closed');
      expect(message.android.priority).to.equal(AndroidMessagePriority.HIGH);
    });

    it('is data-only + HIGH when includeNotificationPayload is false (byte-identical to today)', () => {
      const message = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, CLOSE_TS, false);
      expect(message).to.not.have.property('notification');
      expect(message.android.priority).to.equal(AndroidMessagePriority.HIGH);
      expect(message.data).to.deep.equal(RESOLVED_FIXTURE);
    });

    it('adds notification + tag + HIGH priority when includeNotificationPayload is true, data unchanged', () => {
      const message = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, CLOSE_TS, true);
      expect(message.topic).to.equal(EXPECTED_V2_TOPIC);
      expect(message.notification.title).to.equal('Resolved: garage door closed');
      expect(message.notification.body).to.equal('Was open for 14 minutes');
      expect(message.android.priority).to.equal(AndroidMessagePriority.HIGH);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      expect((message.android.notification as any).notification_priority).to.be.undefined;
      expect(message.android.notification.tag).to.equal('garage_door');
      // Data block is identical across both shapes — the wire contract never changes.
      expect(message.data).to.deep.equal(RESOLVED_FIXTURE);
      expect(message.android.collapse_key).to.equal('door_not_closed');
    });

    it('formats the duration as hours past 60 minutes', () => {
      const message = getResolvedMessage(BUILD_TIMESTAMP, OPEN_TS, OPEN_TS + 90 * 60, true);
      expect(message.notification.body).to.equal('Was open for 1 hours');
    });
  });
});
