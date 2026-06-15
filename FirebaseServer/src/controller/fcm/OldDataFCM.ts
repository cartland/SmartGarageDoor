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

import { DATABASE as NotificationsDatabase } from '../../database/NotificationsDatabase';

import { SensorEvent, SensorEventType } from '../../model/SensorEvent';
import { AndroidMessagePriority, TopicMessage, Notification, NotificationPriority, AndroidConfig, AndroidNotification } from '../../model/FCM';
import { buildTimestampToFcmTopic } from '../../model/FcmTopic';
import { getSnoozeStatus, SnoozeLatestParams } from '../SnoozeNotifications';
import { SnoozeStatus } from '../../model/SnoozeRequest';

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
  const shouldSend: boolean = await shouldSendFcmForOpenDoor(buildTimestamp, currentEvent, timestampSeconds)
  if (!shouldSend) {
    console.info('Decided not to send FCM for open door');
    return null;
  }
  console.debug('Decided to send FCM for open door');
  const message = getDoorNotClosedMessageFromEvent(buildTimestamp, currentEvent, timestampSeconds);
  if (!message) {
    console.error('Could not generate message to send');
    return null;
  }
  const oldNotificationData = await NotificationsDatabase.getCurrent(buildTimestamp);
  if (NOTIFICATION_CURRENT_EVENT_KEY in oldNotificationData
    && TIMESTAMP_SECONDS_KEY in oldNotificationData[NOTIFICATION_CURRENT_EVENT_KEY]) {
    const oldTimestampSeconds = oldNotificationData[NOTIFICATION_CURRENT_EVENT_KEY][TIMESTAMP_SECONDS_KEY];
    const newTimestampSeconds = currentEvent.timestampSeconds;
    console.log('oldTimestampSeconds', oldTimestampSeconds, 'new', currentEvent.timestampSeconds);
    if (oldTimestampSeconds === newTimestampSeconds) {
      console.log('Not sending duplicate notification');
      return null;
    }
  }
  const data = {};
  data[NOTIFICATION_CURRENT_EVENT_KEY] = currentEvent;
  data[NOTIFICATION_MESSAGE_KEY] = message;
  console.log('Sending notification', JSON.stringify(message));
  // Send BEFORE recording the dedup marker. If the send fails we must NOT
  // persist "already notified for this event" — otherwise the duplicate-
  // suppression check above would block every future retry and the user
  // would never be told the door is open. Leaving the marker unsaved lets
  // the next pubsubCheckForOpenDoorsJob tick (every 5 min) re-send while the
  // door stays open: at-most-once becomes at-least-once. Note that "success"
  // here means FCM *accepted* the message, not that the device received it.
  // Full rationale + tradeoffs: docs/NOTIFICATION_RELIABILITY.md (R5).
  try {
    const response = await firebase.messaging().send(message);
    // Response is a message ID string.
    console.log('Successfully sent message:', JSON.stringify(response));
  } catch (error) {
    console.error('Error sending message:', JSON.stringify(error));
    return null; // No dedup marker written → the next tick retries.
  }
  // Record the dedup marker only AFTER a confirmed send. A failure here is
  // rare (a Firestore write) but means the next tick re-sends a duplicate,
  // coalesced by collapse_key 'door_not_closed'. Log loudly so a persistent
  // failure is noticed rather than silently re-notifying every 5 minutes.
  try {
    await NotificationsDatabase.save(buildTimestamp, data);
  } catch (error) {
    console.error(
      'Sent notification but failed to persist dedup marker; may re-send next tick:',
      JSON.stringify(error),
    );
  }
  return message;
}


const TOO_LONG_OPEN_SECONDS = 15 * 60; // 15 minutes.

async function shouldSendFcmForOpenDoor(buildTimestamp: string, currentEvent: SensorEvent, now: number): Promise<boolean> {
  // Check for a snooze request
  const params: SnoozeLatestParams = <SnoozeLatestParams>{
    buildTimestamp: buildTimestamp,
  }
  const snoozeStatus = await getSnoozeStatus(params);
  if (snoozeStatus) {
    if (snoozeStatus.status === SnoozeStatus.ACTIVE) {
      // Snooze request is active. Do not send a notification.
      console.info('shouldSendFcmForOpenDoor: Snooze request is active, do not send a notification');
      return false;
    } else if (snoozeStatus.status === SnoozeStatus.EXPIRED) {
      // Snooze request is expired. Fall through to the duration threshold check.
      console.info('shouldSendFcmForOpenDoor: Snooze request is expired, check if the event is too old');
    } else { // SnoozeStatus.NONE
      // Fall back to checking if the event is too old.
      console.info('shouldSendFcmForOpenDoor: No snooze request, check if the event is too old');
    }
  }
  // If there is no snooze request, check if the event is older than a default threshold.
  const eventDurationSeconds = now - currentEvent.timestampSeconds;
  const isOld = eventDurationSeconds > TOO_LONG_OPEN_SECONDS;
  console.info('shouldSendFcmForOpenDoor: Event duration:', eventDurationSeconds, 'seconds, isOld:', isOld);
  return isOld;
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
