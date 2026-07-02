/**
 * Tests the flag-gated `replaceTagEnabled` parameter of the open-door warning
 * (relaxed-A single-card design, docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md §9).
 *
 * The warning is what frozen old apps (< 2.19.0) render, so the ONLY thing the
 * flag may change is the drawer tag: when off (default) the message is
 * byte-identical to today (pinned by OldDataFCMWarningFreezeTest); when on it
 * gains `android.notification.tag = 'garage_door'` and nothing else. These tests
 * pin exactly that delta so the flag can never smuggle in a broader old-app change.
 */
import { expect } from 'chai';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';
import { TopicMessage } from '../../../src/model/FCM';
import { getDoorNotClosedMessageFromEvent } from '../../../src/controller/fcm/OldDataFCM';

describe('OldDataFCM warning replace-tag (relaxed-A, flag-gated)', () => {
  const BUILD_TIMESTAMP = 'Sat Mar 13 14:45:00 2021';
  const EVENT_SECONDS = 1725781091;
  const NOW = EVENT_SECONDS + 16 * 60;

  // Every warning-producing branch (Closed returns null and is covered separately).
  const WARNING_TYPES: SensorEventType[] = [
    SensorEventType.Open,
    SensorEventType.Opening,
    SensorEventType.OpeningTooLong,
    SensorEventType.Closing,
    SensorEventType.ClosingTooLong,
    SensorEventType.ErrorSensorConflict,
    SensorEventType.Unknown,
    SensorEventType.OpenMisaligned,
  ];

  function warningFor(type: SensorEventType, replaceTagEnabled?: boolean): TopicMessage {
    const currentEvent = <SensorEvent>{
      type,
      timestampSeconds: EVENT_SECONDS,
      message: 'Test message',
      checkInTimestampSeconds: EVENT_SECONDS + 1,
    };
    return replaceTagEnabled === undefined
      ? getDoorNotClosedMessageFromEvent(BUILD_TIMESTAMP, currentEvent, NOW)
      : getDoorNotClosedMessageFromEvent(BUILD_TIMESTAMP, currentEvent, NOW, replaceTagEnabled);
  }

  it('defaults to NO tag when the flag arg is omitted (today\'s behavior)', () => {
    WARNING_TYPES.forEach((type) => {
      const message = warningFor(type);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      expect((message.android.notification as any).tag, `branch ${type}`).to.be.undefined;
    });
  });

  it('adds NO tag when the flag is explicitly false', () => {
    WARNING_TYPES.forEach((type) => {
      const message = warningFor(type, false);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      expect((message.android.notification as any).tag, `branch ${type}`).to.be.undefined;
    });
  });

  it('adds tag=garage_door on every warning branch when the flag is true', () => {
    WARNING_TYPES.forEach((type) => {
      const message = warningFor(type, true);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      expect((message.android.notification as any).tag, `branch ${type}`).to.equal('garage_door');
    });
  });

  it('changes ONLY the tag — every other field is identical on vs off', () => {
    WARNING_TYPES.forEach((type) => {
      const off = warningFor(type, false);
      const on = warningFor(type, true);
      expect(on.topic, `branch ${type} topic`).to.equal(off.topic);
      expect(on.notification.title, `branch ${type} title`).to.equal(off.notification.title);
      expect(on.notification.body, `branch ${type} body`).to.equal(off.notification.body);
      expect(on.android.collapse_key, `branch ${type} collapse_key`).to.equal(off.android.collapse_key);
      expect(on.android.priority, `branch ${type} priority`).to.equal(off.android.priority);
      expect(
        on.android.notification.notification_priority,
        `branch ${type} notification_priority`,
      ).to.equal(off.android.notification.notification_priority);
    });
  });

  it('produces NO message for a closed door regardless of the flag', () => {
    expect(warningFor(SensorEventType.Closed, true)).to.be.null;
    expect(warningFor(SensorEventType.Closed, false)).to.be.null;
  });
});
