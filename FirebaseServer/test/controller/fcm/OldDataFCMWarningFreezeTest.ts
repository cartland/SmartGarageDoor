/**
 * Golden freeze-test for the open-door WARNING message shape.
 *
 * The warning (`getDoorNotClosedMessageFromEvent`) is a notification-payload
 * message published to `door_open-<bt>`, which FROZEN old app builds (< 2.19.0,
 * un-updatable) subscribe to and render with no app code. Any change to its wire
 * shape is an old-app-observable change, so this file pins the ENTIRE message,
 * per branch, byte-for-byte, and — most importantly — asserts the warning carries
 * NO `tag` and NO `data` block today.
 *
 * Why the no-`tag` / no-`data` assertions are load-bearing: the relaxed-A design
 * (docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md § 9) will add
 * `android.notification.tag = 'garage_door'` to the warning to enable single-card
 * replacement, but ONLY behind a server flag (default off) and ONLY after an
 * on-device re-alert gate passes. This freeze-test guarantees that any edit which
 * adds the tag unconditionally — or otherwise alters the frozen old-app warning —
 * fails CI instead of silently changing what old apps receive. When the flag is
 * wired in, the flag-OFF path must keep passing this test unchanged.
 */
import { expect } from 'chai';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';
import { AndroidMessagePriority, NotificationPriority, TopicMessage } from '../../../src/model/FCM';
import { getDoorNotClosedMessageFromEvent } from '../../../src/controller/fcm/OldDataFCM';

describe('OldDataFCM warning freeze-test (old-app wire shape)', () => {
  const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
  const EXPECTED_TOPIC = 'door_open-Sat.Mar.13.14.45.00.2021';
  const EVENT_SECONDS = 1725781091;
  const NOW = EVENT_SECONDS + 16 * 60; // 16 minutes open → "16 minutes"

  function warningFor(type: SensorEventType): TopicMessage {
    const currentEvent = <SensorEvent>{
      type,
      timestampSeconds: EVENT_SECONDS,
      message: 'Test message',
      checkInTimestampSeconds: EVENT_SECONDS + 1,
    };
    return getDoorNotClosedMessageFromEvent(BUILD_TIMESTAMP, currentEvent, NOW);
  }

  // Every warning-producing branch and its exact user-visible copy. The `default`
  // switch arm is exercised via OpenMisaligned (it has no dedicated case).
  const CASES: Array<{ type: SensorEventType; title: string; body: string }> = [
    { type: SensorEventType.Open, title: 'Garage door open', body: 'Open for more than 16 minutes' },
    { type: SensorEventType.Opening, title: 'Door not closed', body: 'Door not closed for more than 16 minutes' },
    { type: SensorEventType.OpeningTooLong, title: 'Door not closed', body: 'Door not closed for more than 16 minutes' },
    { type: SensorEventType.Closing, title: 'Door not closed', body: 'Door did not close for more than 16 minutes' },
    { type: SensorEventType.ClosingTooLong, title: 'Door not closed', body: 'Door did not close for more than 16 minutes' },
    { type: SensorEventType.ErrorSensorConflict, title: 'Door error', body: 'Door error for longer than 16 minutes' },
    { type: SensorEventType.Unknown, title: 'Unknown door status', body: 'Error not resolved for longer than 16 minutes' },
    { type: SensorEventType.OpenMisaligned, title: 'Unknown door status', body: 'Door error for longer than 16 minutes' },
  ];

  CASES.forEach(({ type, title, body }) => {
    describe(`branch ${type}`, () => {
      it('pins the full transport + notification shape', () => {
        const message = warningFor(type);
        expect(message, 'warning should be produced for this branch').to.not.be.null;
        expect(message.topic).to.equal(EXPECTED_TOPIC);
        expect(message.android.collapse_key).to.equal('door_not_closed');
        expect(message.android.priority).to.equal(AndroidMessagePriority.HIGH);
        expect(message.android.notification.notification_priority).to.equal(NotificationPriority.PRIORITY_MAX);
        expect(message.notification.title).to.equal(title);
        expect(message.notification.body).to.equal(body);
      });

      it('carries NO tag today (guards the flag-gated relaxed-A tag)', () => {
        const message = warningFor(type);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        expect((message.android.notification as any).tag).to.be.undefined;
      });

      it('carries NO data block today (pure notification-payload)', () => {
        const message = warningFor(type);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        expect((message as any).data).to.be.undefined;
      });

      it('sets no channel_id, sound, or click_action (old-app render unchanged)', () => {
        const message = warningFor(type);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const androidNotification = message.android.notification as any;
        expect(androidNotification.channel_id).to.be.undefined;
        expect(androidNotification.sound).to.be.undefined;
        expect(androidNotification.click_action).to.be.undefined;
      });
    });
  });

  it('produces NO warning when the door is closed', () => {
    const message = warningFor(SensorEventType.Closed);
    expect(message).to.be.null;
  });
});
