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
import * as functions from 'firebase-functions';

firebase.initializeApp();

import { echo } from './controller/functions/content'
import { remoteButton, addRemoteButtonCommand } from './controller/functions/remote'
import { serverConfig } from './controller/functions/config'
import { dataRetentionPolicy, deleteData } from './controller/functions/datapolicy'
import { nextEvent } from './controller/functions/events'
import { updateEvent, sendFCMForOldData } from './controller/functions/EventUpdates';
import { TimeSeriesDatabase } from './database/TimeSeriesDatabase';

const EVENT_DATABASE = new TimeSeriesDatabase('eventsCurrent', 'eventsAll');

/*
 * This file is the main entrace for Cloud Functions for Firebase.
 * It exposes functions that will be deployed to the backend
 */

// This is a trick to improve performance when there are many functions,
// by only exporting the function that is needed by the particular instance.
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'echo') {
  exports.echo = echo;
}

if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'nextEvent') {
  exports.nextEvent = nextEvent;
}

if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'remoteButton') {
  exports.remoteButton = remoteButton;
}

if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'addRemoteButtonCommand') {
  exports.addRemoteButtonCommand = addRemoteButtonCommand;
}

if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'serverConfig') {
  exports.serverConfig = serverConfig;
}

if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'updateEvents') {
  exports.updateEvents = functions.firestore
    .document('updateAll/{docId}')
    .onWrite(async (change, context) => {
      const data = change.after.data();
      const scheduledJob = false;
      await updateEvent(data, scheduledJob);
      return null;
    });
}

exports.checkForDoorErrors = functions.pubsub.schedule('every 1 minutes').onRun(async (context) => {
  const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
  const buildTimestampString = 'Sat Mar 13 14:45:00 2021';
  const scheduledJob = true;
  const data = {};
  data[BUILD_TIMESTAMP_PARAM_KEY] = buildTimestampString;
  await updateEvent(data, scheduledJob);
  return null;
});

exports.checkForOpenDoorsJob = functions.pubsub.schedule('every 5 minutes').onRun(async (context) => {
  const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
  const eventData = await EVENT_DATABASE.getCurrent(buildTimestamp);
  await sendFCMForOldData(buildTimestamp, eventData);
  return null;
});

exports.checkForOpenDoors = functions.https.onRequest(async (request, response) => {
  const buildTimestamp = 'Sat Mar 13 14:45:00 2021';
  const eventData = await EVENT_DATABASE.getCurrent(buildTimestamp);
  const result = await sendFCMForOldData(buildTimestamp, eventData);
  response.status(200).send(result);
});

exports.dataRetentionPolicy = dataRetentionPolicy;

exports.deleteOldData = deleteData;
