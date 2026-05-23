/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
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

// npm run tests

import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';
import { getFCMDataFromEvent } from '../../../src/controller/fcm/EventFCM';
import { SensorEvent, SensorEventType } from '../../../src/model/SensorEvent';
import { AndroidMessagePriority } from '../../../src/model/FCM';

// Wire-contract fixtures shared with Android's FcmPayloadParsingTest.
// A unilateral rename (e.g. `timestampSeconds` → `timestampSec`) on
// either side breaks the deep-equal assertion below OR the Android
// parse — but not both, so unilateral drift becomes a test failure
// on at least one side.
const FIXTURE_DIR = path.join(__dirname, '..', '..', '..', '..', 'wire-contracts', 'fcmDoorEvent');
const CLOSED_FIXTURE = JSON.parse(
    fs.readFileSync(path.join(FIXTURE_DIR, 'payload_closed.json'), 'utf8'),
);
const OPEN_FIXTURE = JSON.parse(
    fs.readFileSync(path.join(FIXTURE_DIR, 'payload_open.json'), 'utf8'),
);
const OPENING_FIXTURE = JSON.parse(
    fs.readFileSync(path.join(FIXTURE_DIR, 'payload_opening.json'), 'utf8'),
);

describe('getFCMDataFromEvent', () => {
    it('returns FCM with correct topic', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.topic;
        const expected = 'door_open-Sat.Mar.13.14.45.00.2021';
        expect(actual).to.equal(expected);
    });
    it('returns FCM with correct collapse_key', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.android.collapse_key;
        const expected = 'sensor_event_update';
        expect(actual).to.equal(expected);
    });
    it('returns FCM with correct priority', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.android.priority;
        const expected = AndroidMessagePriority.HIGH;
        expect(actual).to.equal(expected);
    });
    it('returns FCM with data type', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.data['type'];
        expect(actual).to.equal('CLOSED');
    });
    it('returns FCM with data timestampSeconds', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.data['timestampSeconds'];
        expect(actual).to.equal('1725781091');
    });
    it('returns FCM with data message', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.data['message'];
        expect(actual).to.equal('Test message');
    });
    it('returns FCM with data checkInTimestampSeconds', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781092,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.data['checkInTimestampSeconds'];
        expect(actual).to.equal('1725781092');
    });
    it('returns FCM without an Android notification', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.android.notification;
        expect(actual).to.be.undefined
    });
    it('returns FCM without a notification', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
        const sensorEvent = <SensorEvent>{
            type: SensorEventType.Closed,
            timestampSeconds: 1725781091,
            message: "Test message",
            checkInTimestampSeconds: 1725781091,
        };
        const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
        const actual = event.notification;
        expect(actual).to.be.undefined
    });

    describe('wire-contract fixtures (shared with Android)', () => {
        const buildTimestamp = 'Sat Mar 13 14:45:00 2021';

        it('CLOSED matches payload_closed.json', () => {
            const sensorEvent = <SensorEvent>{
                type: SensorEventType.Closed,
                timestampSeconds: 1725781082,
                message: "The door is closed.",
                checkInTimestampSeconds: 1725781092,
            };
            const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
            expect(event.data).to.deep.equal(CLOSED_FIXTURE);
        });

        it('OPEN matches payload_open.json', () => {
            const sensorEvent = <SensorEvent>{
                type: SensorEventType.Open,
                timestampSeconds: 1710000000,
                message: "The door is open.",
                checkInTimestampSeconds: 1710000060,
            };
            const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
            expect(event.data).to.deep.equal(OPEN_FIXTURE);
        });

        it('OPENING matches payload_opening.json', () => {
            const sensorEvent = <SensorEvent>{
                type: SensorEventType.Opening,
                timestampSeconds: 1710000000,
                message: "The door is opening.",
                checkInTimestampSeconds: 1710000060,
            };
            const event = getFCMDataFromEvent(buildTimestamp, sensorEvent);
            expect(event.data).to.deep.equal(OPENING_FIXTURE);
        });
    });
});
