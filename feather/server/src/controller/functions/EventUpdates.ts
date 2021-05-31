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

import * as SensorEventDatabase from '../../database/SensorEventDatabase';
import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull, isEventOld, getMessageFromEvent, getFCMDataFromEvent } from '../../controller/EventInterpreter';
import { SensorEvent } from '../../model/SensorEvent';
import { TopicMessage } from '../../model/FCM';

const NOTIFICATIONS_DATABASE = new TimeSeriesDatabase('notificationsCurrent', 'notificationsAll');

const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';
const QUERY_PARAMS_KEY = 'queryParams';
const SENSOR_A_KEY = 'sensorA';
const SENSOR_B_KEY = 'sensorB';
const CURRENT_EVENT_KEY = 'currentEvent';
const PREVIOUS_EVENT_KEY = 'previousEvent';
const NOTIFICATION_CURRENT_EVENT_KEY = 'notificationCurrentEvent';
const NOTIFICATION_MESSAGE_KEY = 'message';
const TIMESTAMP_SECONDS_KEY = 'timestampSeconds';

export async function updateEvent(data, scheduledJob: boolean) {
  if (!data || !(BUILD_TIMESTAMP_PARAM_KEY in data)) {
    console.log('scheduledJob:', scheduledJob,
      'Skipping updateEvent() because data does not have buildTimestamp', data);
    return;
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  const sensorSnapshot = <SensorSnapshot>{
    sensorA: '',
    sensorB: '',
    timestampSeconds: null,
  };
  if (DATABASE_TIMESTAMP_SECONDS_KEY in data) {
    sensorSnapshot.timestampSeconds = data[DATABASE_TIMESTAMP_SECONDS_KEY];
  } else {
    console.error('Missing timestamp key:', DATABASE_TIMESTAMP_SECONDS_KEY, 'data:', data);
  }
  if (QUERY_PARAMS_KEY in data) {
    const queryParams = data[QUERY_PARAMS_KEY];
    if (SENSOR_A_KEY in queryParams) {
      sensorSnapshot.sensorA = queryParams[SENSOR_A_KEY];
    }
    if (SENSOR_B_KEY in queryParams) {
      sensorSnapshot.sensorB = queryParams[SENSOR_B_KEY];
    }
  }
  const now = firebase.firestore.Timestamp.now();
  const timestampSeconds = now.seconds;
  await updateWithParams(buildTimestamp, sensorSnapshot, timestampSeconds, scheduledJob);
}

async function updateWithParams(buildTimestamp, sensorSnapshot, timestampSeconds, scheduledJob: boolean) {
  const oldData = await SensorEventDatabase.DATABASE.get(buildTimestamp);
  let oldEvent: SensorEvent = null;
  if (CURRENT_EVENT_KEY in oldData) {
    oldEvent = oldData[CURRENT_EVENT_KEY];
  }
  const newEvent = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
  if (newEvent !== null) {
    const data = {};
    data[BUILD_TIMESTAMP_PARAM_KEY] = buildTimestamp;
    data[PREVIOUS_EVENT_KEY] = oldEvent;
    data[CURRENT_EVENT_KEY] = newEvent;
    await SensorEventDatabase.DATABASE.set(buildTimestamp, data);
    await sendFCMForSensorEvent(buildTimestamp, newEvent);
  } else {
    if (scheduledJob) {
      // Do nothing. Do not update database during scheduled check unless it results in a new event.
    } else {
      // Saving the old data again will update FIRESTORE_databaseTimestamp and FIRESTORE_databaseTimestampSeconds.
      await SensorEventDatabase.DATABASE.set(buildTimestamp, oldData);
      // Pull the data with the updated timestamp.
      const newData = await SensorEventDatabase.DATABASE.get(buildTimestamp);
      if (!(DATABASE_TIMESTAMP_SECONDS_KEY in newData)) {
        console.error('Missing timestamp key:', DATABASE_TIMESTAMP_SECONDS_KEY, 'data:', newData);
      }
      oldEvent.checkInTimestampSeconds = newData[DATABASE_TIMESTAMP_SECONDS_KEY];
      // Send old event with updated check-in timestamp.
      await sendFCMForSensorEvent(buildTimestamp, oldEvent);
    }
  }
}

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
  const message = getMessageFromEvent(buildTimestamp, currentEvent, timestampSeconds);
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
  console.log('Sending notification', message);
  await firebase.messaging().send(message)
    .then((response) => {
      // Response is a message ID string.
      console.log('Successfully sent message:', response);
    })
    .catch((error) => {
      console.log('Error sending message:', error);
    });
  return message;
}

export async function sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage> {
  const message = getFCMDataFromEvent(buildTimestamp, sensorEvent);
  if (!message) {
    return null;
  }
  console.log('Sending notification', message);
  await firebase.messaging().send(message)
    .then((response) => {
      // Response is a message ID string.
      console.log('Successfully sent message:', response);
    })
    .catch((error) => {
      console.log('Error sending message:', error);
    });
  return message;
}
