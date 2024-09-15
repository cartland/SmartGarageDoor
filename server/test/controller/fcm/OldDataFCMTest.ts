
import { expect } from 'chai';
import { getFCMDataFromEvent } from '../../../src/controller/fcm/EventFCM';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';
import { AndroidMessagePriority, NotificationPriority, TopicMessage } from '../../../src/model/FCM';
import { getDoorNotClosedMessageFromEvent } from '../../../src/controller/fcm/OldDataFCM';

describe('getDoorNotClosedMessageFromEvent', () => {
    it('does not return message if door is closed', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.be.null;
    });
    it('returns message if door is open', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door status is unknown', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Unknown,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door has an error', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.ErrorSensorConflict,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door is closing', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Closing,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door is closing for a long time', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.ClosingTooLong,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door is misaligned', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.OpenMisaligned,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door is opening', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Opening,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message if door is opening for a long time', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.OpeningTooLong,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message;
        expect(actual).to.not.be.null;
    });
    it('returns message with correct topic', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.topic;
        const expected = 'door_open-Sat.Mar.13.14.45.00.2021';
        expect(actual).to.equal(expected);
    });
    it('returns message with correct collapse_key', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.android.collapse_key;
        const expected = 'door_not_closed';
        expect(actual).to.equal(expected);
    });
    it('returns message with correct priority', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.android.priority;
        const expected = AndroidMessagePriority.HIGH;
        expect(actual).to.equal(expected);
    });
    it('returns message with Android max priority notification', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.android.notification.notification_priority;
        const expected = NotificationPriority.PRIORITY_MAX;
        expect(actual).to.equal(expected);
    });
    it('returns message with notification title', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.notification.title;
        expect(actual).to.not.be.null;
        expect(actual).to.not.be.empty;
    });
    it('returns message with notification body', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const currentEvent = <SensorEvent>{
            type: SensorEventType.Open,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const now = 1725781091 + 20 * 60;
        const message: TopicMessage = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, now);
        const actual = message.notification.body;
        expect(actual).to.not.be.null;
        expect(actual).to.not.be.empty;
    });
});
