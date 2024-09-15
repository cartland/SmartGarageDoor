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

import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

import { SensorEvent, SensorEventType } from '../../model/SensorEvent';
import { AndroidMessagePriority, TopicMessage, Notification, NotificationPriority, AndroidConfig, AndroidNotification } from '../../model/FCM';
import { buildTimestampToFcmTopic } from '../../model/FcmTopic';

const NOTIFICATIONS_DATABASE = new TimeSeriesDatabase('notificationsCurrent', 'notificationsAll');

const CURRENT_EVENT_KEY = 'currentEvent';
const NOTIFICATION_CURRENT_EVENT_KEY = 'notificationCurrentEvent';
const NOTIFICATION_MESSAGE_KEY = 'message';
const TIMESTAMP_SECONDS_KEY = 'timestampSeconds';

/**
 * Send an FCM message if the current event is old and the door is not closed.
 *
 * If the garage door is left open for too long, send an FCM message to users.
 * Also send messages for other error conditions.
 * This FCM will be a user-visible notification.
 * Do not send a message if the door is closed.
 */
export async function sendFCMForOldData(buildTimestamp: string, eventData): Promise<TopicMessage> {
  if (!(CURRENT_EVENT_KEY in eventData)) {
    console.log('Latest event does not have key:', CURRENT_EVENT_KEY);
    return null;
  }
  const currentEvent: SensorEvent = eventData[CURRENT_EVENT_KEY];
  if (!currentEvent) {
    console.log('Latest event does not have current event');
    return null;
  }
  const now = firebase.firestore.Timestamp.now();
  const timestampSeconds = now.seconds;
  if (!isEventOld(currentEvent, timestampSeconds)) {
    return null;
  }
  const message = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, timestampSeconds);
  if (!message) {
    return null;
  }
  const oldNotificationData = await NOTIFICATIONS_DATABASE.getCurrent(buildTimestamp);
  if (NOTIFICATION_CURRENT_EVENT_KEY in oldNotificationData
    && TIMESTAMP_SECONDS_KEY in oldNotificationData[NOTIFICATION_CURRENT_EVENT_KEY]) {
    const oldTimestampSeconds = oldNotificationData[NOTIFICATION_CURRENT_EVENT_KEY][TIMESTAMP_SECONDS_KEY];
    const newTimestampSeconds = currentEvent.timestampSeconds;
    console.log('oldTimestampSeconds', oldTimestampSeconds, 'new', currentEvent.timestampSeconds);
    if (oldTimestampSeconds === newTimestampSeconds) {
      return null;
    }
  }
  const data = {};
  data[NOTIFICATION_CURRENT_EVENT_KEY] = currentEvent;
  data[NOTIFICATION_MESSAGE_KEY] = message;
  await NOTIFICATIONS_DATABASE.save(buildTimestamp, data);
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


const TOO_LONG_OPEN_SECONDS = 15 * 60;

function isEventOld(currentEvent: SensorEvent, now: number): boolean {
  const eventDurationSeconds = now - currentEvent.timestampSeconds;
  return eventDurationSeconds > TOO_LONG_OPEN_SECONDS;
}

/**
 * Create FCM message to users if the door is not closed.
 *
 * @param buildTimestamp Client build timestamp to identify the sensors.
 * @param currentEvent Current status of the door.
 * @param now Unix time in seconds.
 * @return FCM message to send to users. Return null if no message is needed.
 */
export function getDoorNotClosedMessageFromEvent(buildTimestamp: string, currentEvent: SensorEvent, now: number): TopicMessage {
  const eventDurationSeconds = now - currentEvent.timestampSeconds;
  const durationMinutes = Math.floor(eventDurationSeconds / 60);
  const durationHours = Math.floor(durationMinutes / 60);
  let durationString = '';
  if (durationMinutes < 60) {
    durationString = durationMinutes.toString() + ' minutes';
  } else {
    durationString = durationHours.toString() + ' hours';
  }
  const message = <TopicMessage>{};
  message.notification = <Notification>{};
  message.android = <AndroidConfig>{};
  message.android.notification = <AndroidNotification>{};
  message.topic = buildTimestampToFcmTopic(buildTimestamp);
  message.android.collapse_key = 'door_not_closed';
  message.android.priority = AndroidMessagePriority.HIGH;
  message.android.notification.notification_priority = NotificationPriority.PRIORITY_MAX;
  const type = currentEvent.type;
  switch (type) {
    case SensorEventType.Unknown:
      message.notification.title = 'Unknown door status';
      message.notification.body = 'Error not resolved for longer than ' + durationString;
      return message;
    case SensorEventType.ErrorSensorConflict:
      message.notification.title = 'Door error';
      message.notification.body = 'Door error for longer than ' + durationString;
      return message;
    case SensorEventType.Closed:
      return null;
    case SensorEventType.Closing:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door did not close for more than ' + durationString;
      return message;
    case SensorEventType.ClosingTooLong:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door did not close for more than ' + durationString;
      return message;
    case SensorEventType.Open:
      message.notification.title = 'Garage door open';
      message.notification.body = 'Open for more than ' + durationString;
      return message;
    case SensorEventType.Opening:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door not closed for more than ' + durationString;
      return message;
    case SensorEventType.OpeningTooLong:
      message.notification.title = 'Door not closed';
      message.notification.body = 'Door not closed for more than ' + durationString;
      return message;
    default:
      message.notification.title = 'Unknown door status';
      message.notification.body = 'Door error for longer than ' + durationString;
      return message;
  }
}
