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
 * Fake-based tests for src/controller/fcm/OldDataFCM.ts#sendFCMForOldData.
 *
 * Complements the existing OldDataFCMTest.ts which covers the pure
 * `getDoorNotClosedMessageFromEvent` message-building logic. This file
 * covers the orchestration logic inside `sendFCMForOldData`:
 * short-circuit paths, snooze integration, duplicate suppression, and
 * save/FCM sequencing.
 *
 * Fakes:
 *  - FakeNotificationsDatabase — captures notification saves + current state
 *  - FakeSensorEventDatabase — required because getSnoozeStatus reads from it
 *  - FakeSnoozeNotificationsDatabase — required because getSnoozeStatus reads from it
 *
 * External SDK stubs (sinon) — these are the non-deterministic seams the
 * function reaches out to. Stubbing them is the only practical way to
 * pin behavior without running an emulator.
 *  - firebase.messaging() — intercepts outgoing FCM sends
 *  - firebase.firestore.Timestamp.now() — controls "now" for the 15-minute
 *    threshold and for the snooze-expired check
 */

import { expect } from 'chai';
import * as sinon from 'sinon';
import * as firebase from 'firebase-admin';

import { sendFCMForOldData } from '../../../src/controller/fcm/OldDataFCM';
import {
  setImpl as setNotificationsDBImpl,
  resetImpl as resetNotificationsDBImpl,
} from '../../../src/database/NotificationsDatabase';
import {
  setImpl as setSensorEventDBImpl,
  resetImpl as resetSensorEventDBImpl,
} from '../../../src/database/SensorEventDatabase';
import {
  setImpl as setSnoozeDBImpl,
  resetImpl as resetSnoozeDBImpl,
} from '../../../src/database/SnoozeNotificationsDatabase';
import { FakeNotificationsDatabase } from '../../fakes/FakeNotificationsDatabase';
import { FakeSensorEventDatabase } from '../../fakes/FakeSensorEventDatabase';
import { FakeSnoozeNotificationsDatabase } from '../../fakes/FakeSnoozeNotificationsDatabase';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';

const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';

// 15 * 60 + 1 — just past the TOO_LONG_OPEN_SECONDS threshold.
const STALE_EVENT_DURATION_SECONDS = 15 * 60 + 1;

// Arbitrary fixed "now" so the 15-minute threshold is deterministic.
const NOW_SECONDS = 1_800_000_000;

// Event timestamps derived from NOW_SECONDS.
const STALE_EVENT_TS = NOW_SECONDS - STALE_EVENT_DURATION_SECONDS;
const FRESH_EVENT_TS = NOW_SECONDS - 60; // 1 minute old

describe('sendFCMForOldData (via fakes)', () => {
  let fakeNotifications: FakeNotificationsDatabase;
  let fakeSensorEvents: FakeSensorEventDatabase;
  let fakeSnoozeDB: FakeSnoozeNotificationsDatabase;
  let sendStub: sinon.SinonStub;

  // firebase-admin requires an initialized default app before any service
  // call. Initialize once with a minimal config so `firebase.messaging()`
  // returns a real (but unused) Messaging instance whose `send` we can stub.
  before(() => {
    if (firebase.apps.length === 0) {
      firebase.initializeApp({ projectId: 'test-project' });
    }
  });

  beforeEach(() => {
    fakeNotifications = new FakeNotificationsDatabase();
    fakeSensorEvents = new FakeSensorEventDatabase();
    fakeSnoozeDB = new FakeSnoozeNotificationsDatabase();
    setNotificationsDBImpl(fakeNotifications);
    setSensorEventDBImpl(fakeSensorEvents);
    setSnoozeDBImpl(fakeSnoozeDB);

    // Pin "now" so the 15-minute threshold is deterministic.
    sinon.stub(firebase.firestore.Timestamp, 'now').returns({
      seconds: NOW_SECONDS,
      nanoseconds: 0,
      toDate: () => new Date(NOW_SECONDS * 1000),
      toMillis: () => NOW_SECONDS * 1000,
      isEqual: () => false,
      valueOf: () => NOW_SECONDS.toString(),
      toJSON: () => ({ seconds: NOW_SECONDS, nanoseconds: 0 }),
    } as unknown as firebase.firestore.Timestamp);

    // Intercept FCM sends. The Messaging singleton is cached per app, so
    // stubbing `send` on the returned instance applies to every subsequent
    // `firebase.messaging().send(...)` call. sinon.restore() in afterEach
    // removes the stub.
    sendStub = sinon.stub(firebase.messaging(), 'send').resolves('mock-message-id');
  });

  afterEach(() => {
    resetNotificationsDBImpl();
    resetSensorEventDBImpl();
    resetSnoozeDBImpl();
    sinon.restore();
  });

  // --- Short-circuit paths (no DB reads/writes, no FCM) ---

  it('returns null when eventData has no currentEvent key', async () => {
    const result = await sendFCMForOldData(BUILD_TIMESTAMP, {});
    expect(result).to.be.null;
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  it('returns null when currentEvent is falsy', async () => {
    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: null });
    expect(result).to.be.null;
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  // --- shouldSendFcmForOpenDoor branches ---

  it('returns null when the event is fresh (under 15-minute threshold)', async () => {
    const freshOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: FRESH_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    // Seed a current event so getSnoozeStatus has something to read.
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: freshOpen });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: freshOpen });

    expect(result).to.be.null;
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  it('returns null when an ACTIVE snooze suppresses the notification', async () => {
    const staleOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
    // Active snooze: matching event timestamp + future end time.
    fakeSnoozeDB.seed(BUILD_TIMESTAMP, {
      currentEventTimestampSeconds: STALE_EVENT_TS,
      snoozeEndTimeSeconds: NOW_SECONDS + 3600, // 1 hour from now
      snoozeRequestSeconds: NOW_SECONDS - 60,
      snoozeDuration: '1h',
    });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    expect(result).to.be.null;
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  it('sends when snooze is EXPIRED and event is stale', async () => {
    const staleOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
    // Expired snooze: matching event timestamp + past end time.
    fakeSnoozeDB.seed(BUILD_TIMESTAMP, {
      currentEventTimestampSeconds: STALE_EVENT_TS,
      snoozeEndTimeSeconds: NOW_SECONDS - 60, // expired 1 min ago
      snoozeRequestSeconds: NOW_SECONDS - 3600,
      snoozeDuration: '1h',
    });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    expect(result).to.not.be.null;
    expect(fakeNotifications.saved).to.have.length(1);
    expect(sendStub.calledOnce).to.be.true;
  });

  // --- getDoorNotClosedMessageFromEvent short-circuit ---

  it('returns null when the door is Closed (even if stale)', async () => {
    const staleClosed: SensorEvent = {
      type: SensorEventType.Closed,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleClosed });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleClosed });

    expect(result).to.be.null;
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  // --- Happy path ---

  it('saves notification and sends FCM when door is stale-open with no snooze', async () => {
    const staleOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    expect(result).to.not.be.null;
    // Notification saved with the expected shape: notificationCurrentEvent + message.
    expect(fakeNotifications.saved).to.have.length(1);
    const [savedBuildTs, savedData] = fakeNotifications.saved[0];
    expect(savedBuildTs).to.equal(BUILD_TIMESTAMP);
    expect(savedData.notificationCurrentEvent).to.equal(staleOpen);
    expect(savedData.message).to.equal(result);
    // FCM called exactly once with the built message.
    expect(sendStub.calledOnce).to.be.true;
    expect(sendStub.firstCall.args[0]).to.equal(result);
  });

  // --- Duplicate-notification suppression ---

  it('returns null (skip) when the same currentEvent timestamp was already notified', async () => {
    const staleOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
    // Pre-seed the notifications DB with a prior notification for this same event timestamp.
    fakeNotifications.seed(BUILD_TIMESTAMP, {
      notificationCurrentEvent: { timestampSeconds: STALE_EVENT_TS },
      message: { topic: 'anything' },
    });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    expect(result).to.be.null;
    // No NEW save was recorded (seed() does not append to saved[]).
    expect(fakeNotifications.saved).to.be.empty;
    expect(sendStub.called).to.be.false;
  });

  it('sends when a prior notification exists but for a different event timestamp', async () => {
    const staleOpen: SensorEvent = {
      type: SensorEventType.Open,
      timestampSeconds: STALE_EVENT_TS,
      message: '',
      checkInTimestampSeconds: 0,
    };
    fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
    // Pre-seed with a DIFFERENT event timestamp — should not suppress.
    fakeNotifications.seed(BUILD_TIMESTAMP, {
      notificationCurrentEvent: { timestampSeconds: STALE_EVENT_TS - 1000 },
      message: { topic: 'old' },
    });

    const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

    expect(result).to.not.be.null;
    expect(fakeNotifications.saved).to.have.length(1);
    expect(sendStub.calledOnce).to.be.true;
  });

  // --- Failure modes ---

  describe('failure modes', () => {
    async function assertRejects(fn: () => Promise<any>): Promise<Error> {
      try {
        await fn();
      } catch (e) {
        return e as Error;
      }
      throw new Error('expected promise to reject, but it resolved');
    }

    it('propagates NotificationsDatabase.save failure; FCM is NOT called', async () => {
      const staleOpen: SensorEvent = {
        type: SensorEventType.Open,
        timestampSeconds: STALE_EVENT_TS,
        message: '',
        checkInTimestampSeconds: 0,
      };
      fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
      fakeNotifications.failNextSave(new Error('Firestore down: notifications.save'));

      const err = await assertRejects(() => sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen }));
      expect(err.message).to.equal('Firestore down: notifications.save');

      // save() was attempted (captured in audit log even when it threw).
      expect(fakeNotifications.saved).to.have.length(1);
      // FCM must NOT have been called — proves the save error short-circuits.
      expect(sendStub.called).to.be.false;
    });

    it('FCM send() failure is swallowed internally; message still returned', async () => {
      // Pin current behavior: sendFCMForOldData wraps firebase.messaging().send()
      // in a `.catch()` that logs. Callers do not see the failure. This test
      // documents and locks that contract — changing it (to propagate) is a
      // behavior change, not a refactor.
      const staleOpen: SensorEvent = {
        type: SensorEventType.Open,
        timestampSeconds: STALE_EVENT_TS,
        message: '',
        checkInTimestampSeconds: 0,
      };
      fakeSensorEvents.seed(BUILD_TIMESTAMP, { currentEvent: staleOpen });
      sendStub.rejects(new Error('FCM unavailable'));

      const result = await sendFCMForOldData(BUILD_TIMESTAMP, { currentEvent: staleOpen });

      // Result is the built message even though FCM send threw — swallowed.
      expect(result).to.not.be.null;
      // DB save committed before FCM attempt.
      expect(fakeNotifications.saved).to.have.length(1);
      expect(sendStub.calledOnce).to.be.true;
    });
  });
});
