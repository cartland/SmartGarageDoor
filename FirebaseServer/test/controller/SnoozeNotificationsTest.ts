/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
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

import * as firebase from 'firebase-admin';
import { expect } from 'chai';
import * as sinon from 'sinon';

import { getSnoozeStatus, submitSnoozeNotificationsRequest } from '../../src/controller/SnoozeNotifications';
import { DATABASE as SnoozeNotificationsDatabase } from '../../src/database/SnoozeNotificationsDatabase';
import { DATABASE as SensorEventDatabase } from '../../src/database/SensorEventDatabase';
import { SnoozeRequest, SnoozeStatus } from '../../src/model/SnoozeRequest';
import { SensorEvent } from '../../src/model/SensorEvent';

describe('SnoozeNotifications', () => {
  afterEach(() => {
    sinon.restore();
  });

  describe('getSnoozeStatus', () => {
    it('should return NONE when there is no current event', async () => {
      sinon.stub(SensorEventDatabase, 'get').resolves(null);
      const params = { buildTimestamp: 'test' };
      const result = await getSnoozeStatus(params);
      expect(result.status).to.equal(SnoozeStatus.NONE);
    });

    it('should return NONE when there is no snooze request', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      sinon.stub(SnoozeNotificationsDatabase, 'get').resolves(null);
      const params = { buildTimestamp: 'test' };
      const result = await getSnoozeStatus(params);
      expect(result.status).to.equal(SnoozeStatus.NONE);
    });

    it('should return EXPIRED when the snooze request is expired', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      const snoozeRequest = {
        snoozeEndTimeSeconds: Math.floor(Date.now() / 1000) - 3600, // 1 hour ago
        currentEventTimestampSeconds: 12345,
      };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      sinon.stub(SnoozeNotificationsDatabase, 'get').resolves(snoozeRequest);
      const params = { buildTimestamp: 'test' };
      const result = await getSnoozeStatus(params);
      expect(result.status).to.equal(SnoozeStatus.EXPIRED);
    });

    it('should return ACTIVE when there is an active snooze request', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      const snoozeRequest = {
        snoozeEndTimeSeconds: Math.floor(Date.now() / 1000) + 3600, // 1 hour from now
        currentEventTimestampSeconds: 12345,
      };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      sinon.stub(SnoozeNotificationsDatabase, 'get').resolves(snoozeRequest);
      const params = { buildTimestamp: 'test' };
      const result = await getSnoozeStatus(params);
      expect(result.status).to.equal(SnoozeStatus.ACTIVE);
    });
  });

  describe('submitSnoozeNotificationsRequest', () => {
    it('should return an error when the snooze event timestamp does not match the current event timestamp', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      const params = {
        buildTimestamp: 'test',
        snoozeDuration: '1h',
        snoozeEventTimestamp: '54321',
      };
      const result = await submitSnoozeNotificationsRequest(params);
      expect(result.error).to.equal('Snooze event timestamp does not match current event timestamp');
      expect(result.code).to.equal(404);
    });

    it('should return an error for an invalid snooze duration', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      const params = {
        buildTimestamp: 'test',
        snoozeDuration: 'invalid',
        snoozeEventTimestamp: '12345',
      };
      const result = await submitSnoozeNotificationsRequest(params);
      expect(result.error).to.include('Invalid snooze duration');
      expect(result.code).to.equal(404);
    });

    it('should successfully create a snooze request with a valid duration', async () => {
      const currentEvent = { currentEvent: { timestampSeconds: '12345' } };
      const snoozeRequest = {
        snoozeEndTimeSeconds: Math.floor(Date.now() / 1000) + 3600,
        currentEventTimestampSeconds: 12345,
      };
      sinon.stub(SensorEventDatabase, 'get').resolves(currentEvent);
      sinon.stub(SnoozeNotificationsDatabase, 'set').resolves();
      sinon.stub(SnoozeNotificationsDatabase, 'get').resolves(snoozeRequest);
      const params = {
        buildTimestamp: 'test',
        snoozeDuration: '1h',
        snoozeEventTimestamp: '12345',
      };
      const result = await submitSnoozeNotificationsRequest(params);
      expect(result.snooze).to.deep.equal(snoozeRequest);
    });
  });
});