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

import * as firebase from 'firebase-admin';

import { SensorEvent, SensorEventAsStringMap } from '../../model/SensorEvent';
import { AndroidMessagePriority, TopicMessage, AndroidConfig, ApnsConfig, ApnsPushType } from '../../model/FCM';
import { buildTimestampToFcmTopic } from '../../model/FcmTopic';

/**
 * Side-effecting FCM dispatch for sensor events.
 *
 * Shape matches src/database/*Database.ts (interface + default impl + swappable
 * singleton + setImpl/resetImpl). Tests use FakeEventFCMService to capture
 * calls without touching Firebase messaging.
 */
export interface EventFCMService {
  sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage>;
}

class DefaultEventFCMService implements EventFCMService {
  async sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage> {
    const message = getFCMDataFromEvent(buildTimestamp, sensorEvent);
    if (!message) {
      return null;
    }
    console.log('Sending notification', JSON.stringify(message));
    await firebase.messaging().send(message)
      .then((response) => {
        // Response is a message ID string.
        console.log('Successfully sent message:', JSON.stringify(response));
      })
      .catch((error) => {
        console.log('Error sending message:', JSON.stringify(error));
      });
    return message;
  }
}

let _instance: EventFCMService = new DefaultEventFCMService();

export const SERVICE: EventFCMService = {
  sendFCMForSensorEvent: (t, e) => _instance.sendFCMForSensorEvent(t, e),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: EventFCMService): void { _instance = impl; }

/** TEST-ONLY: restore the default (Firebase-dispatching) implementation. */
export function resetImpl(): void { _instance = new DefaultEventFCMService(); }

/**
 * Pure helper — builds the FCM payload for a sensor event. No side effects.
 * Covered by EventFCMTest.ts and reused by DefaultEventFCMService.
 *
 * `apns` (docs/DOOR_UPDATE_STRATEGY.md § Phase 2, added alongside the
 * client-side door-update-strategy seam): a data-only FCM message reaches
 * an Apple device only when it carries `apns.payload.aps['content-available']
 * = 1`. Without this block the message was accepted by FCM and delivered
 * to every subscribed Android device exactly as before, but silently
 * dropped for iOS — there was nothing for APNs to forward. Purely
 * additive: Android ignores an `apns` block entirely, and FCM applies
 * `android` / `apns` configs only to the platform they name, so this
 * changes nothing about what already reaches Android devices on the same
 * topic.
 *
 * No `alert` / `sound` / `badge` on the `aps` dict, deliberately — this is
 * a silent background wake, not a user-visible notification. The client's
 * `AppDelegate.didReceiveRemoteNotification` (shared `FcmPayloadParser` →
 * `ReceiveFcmDoorEventUseCase`) has been correct since #915; it was always
 * this block that was missing.
 */
export function getFCMDataFromEvent(buildTimestamp: string, currentEvent: SensorEvent): TopicMessage {
  const message = <TopicMessage>{};
  message.topic = buildTimestampToFcmTopic(buildTimestamp);
  message.data = SensorEventAsStringMap(currentEvent);
  message.android = <AndroidConfig>{};
  message.android.collapse_key = 'sensor_event_update';
  message.android.priority = AndroidMessagePriority.HIGH;
  message.apns = <ApnsConfig>{};
  message.apns.headers = {
    'apns-push-type': ApnsPushType.BACKGROUND,
    'apns-priority': '5',
  };
  message.apns.payload = { aps: { 'content-available': 1 } };
  return message;
}
