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
import { AndroidMessagePriority, TopicMessage, AndroidConfig } from '../../model/FCM';
import { buildTimestampToFcmTopic } from '../../model/FcmTopic';


/**
 * Send an FCM message when the sensor data changes.
 *
 * This pushes the sensor data to the user's device.
 * This does not post a user-visible notification.
 * The user's device can then update the UI.
 */
export async function sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage> {
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

export function getFCMDataFromEvent(buildTimestamp: string, currentEvent: SensorEvent): TopicMessage {
  const message = <TopicMessage>{};
  message.topic = buildTimestampToFcmTopic(buildTimestamp);
  message.data = SensorEventAsStringMap(currentEvent);
  message.android = <AndroidConfig>{};
  message.android.collapse_key = 'sensor_event_update';
  message.android.priority = AndroidMessagePriority.HIGH;
  return message;
}
