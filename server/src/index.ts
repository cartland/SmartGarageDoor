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

firebase.initializeApp();

// HTTP Functions.
import { httpEcho } from './functions/http/Echo'
import { httpCurrentEventData, httpNextEvent } from './functions/http/Events'
import { httpRemoteButton, httpAddRemoteButtonCommand } from './functions/http/RemoteButton'
import { httpCheckForOpenDoors } from './functions/http/OpenDoor'
import { httpDeleteOldData } from './functions/http/DeleteData'
import { httpServerConfig } from './functions/http/ServerConfig'

// Pubsub Functions.
import { pubsubCheckForDoorErrors } from './functions/pubsub/DoorErrors'
import { pubsubCheckForOpenDoorsJob } from './functions/pubsub/OpenDoor'
import { pubsubCheckForRemoteButtonErrors } from './functions/pubsub/RemoteButton'
import { pubsubDataRetentionPolicy } from './functions/pubsub/datapolicy'

// Firestore Functions.
import { firestoreUpdateEvents } from './functions/firestore/Events'

/*
 * This file is the main entrace for Cloud Functions for Firebase.
 * It exposes functions that will be deployed to the backend.
 */

// Functions are guarded by the process environment variables to
// improve performance when there are many functions.
// By only exporting the function that is needed by the particular instance,
// Firebase can execute more quickly.
//
// Example:
//
// if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'echo') {
//   exports.echo = httpEcho;
// }


/**
 * 1. Devices send raw sensor data to the "echo" endpoint.
 *
 * Trigger Type: HTTP
 *
 * Sensor data is sent when the sensor value changes.
 * Sensor data is sent every 10 minutes if there are no changes.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'echo') {
  exports.echo = httpEcho;
}

/**
 * 2. Raw data is converted to an event.
 *
 * Trigger Type: Firestore
 *
 * Firestore triggers a function whenever the database changes.
 * This will trigger a data FCM so the Android app receives updated event data.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'updateEvents') {
  exports.updateEvents = firestoreUpdateEvents;
}

/**
 * Debugging function: Trigger a new event by sending sensor data directly.
 *
 * Trigger Type: HTTP
 *
 * EventInterpreterTest.ts
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'nextEvent') {
  exports.nextEvent = httpNextEvent;
}

/**
 * 3. Clients can fetch the latest event.
 *
 * Trigger Type: HTTP
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'currentEventData') {
  exports.currentEventData = httpCurrentEventData;
}

/**
 * 4. Check for door errors.
 *
 * Trigger Type: PubSub Job
 *
 * If an error is found, an event will be generated, and a data FCM will be sent.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'pubsubCheckForDoorErrors') {
  exports.pubsubCheckForDoorErrors = pubsubCheckForDoorErrors;
}

/**
 * 5. Check for open doors.
 *
 * Trigger Type: PubSub Job
 *
 * If the door is open too long, send a notification FCM to users.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'pubsubCheckForOpenDoorsJob') {
  exports.pubsubCheckForOpenDoorsJob = pubsubCheckForOpenDoorsJob;
}

/**
 * Manually check for open doors.
 *
 * Trigger Type: HTTP
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'checkForOpenDoors') {
  exports.checkForOpenDoors = httpCheckForOpenDoors;
}

/**
 * 6. Remote button checks for button commands.
 *
 * Trigger Type: HTTP
 *
 * If the correct remote button information is returned, the remote will active.
 * This will open or close the garage door.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'remoteButton') {
  exports.remoteButton = httpRemoteButton;
}

/**
 * 7. The Android client can request to push the garage remote button.
 *
 * Trigger Type: HTTP
 *
 * This request is authenticated, has a rate limit, and an expiration time for each request.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'addRemoteButtonCommand') {
  exports.addRemoteButtonCommand = httpAddRemoteButtonCommand;
}

/**
 * 8. Check for remote button errors.
 *
 * Trigger Type: PubSub Job
 *
 * If an error is found, it is saved in the database.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'pubsubCheckForRemoteButtonErrors') {
  exports.pubsubCheckForRemoteButtonErrors = pubsubCheckForRemoteButtonErrors;
}

/**
 * 9. Data retention policy deletes old data.
 *
 * Trigger Type: PubSub Job
 *
 * Specific database types are deleted after the data reaches a certain age.
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'pubsubDataRetentionPolicy') {
  exports.pubsubDataRetentionPolicy = pubsubDataRetentionPolicy;
}

/**
 * Manually trigger a delete event.
 *
 * Trigger Type: HTTP
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'deleteOldData') {
  exports.deleteOldData = httpDeleteOldData;
}

/**
 * Modify the server configuration.
 *
 * Trigger Type: HTTP
 */
if (!process.env.FUNCTION_NAME || process.env.FUNCTION_NAME === 'serverConfig') {
  exports.serverConfig = httpServerConfig;
}
