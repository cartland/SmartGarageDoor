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

import { v4 as uuidv4 } from 'uuid';

import * as functions from 'firebase-functions';

import * as Database from '../../database/Database';
import * as SensorEventDatabase from '../../database/SensorEventDatabase';

import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull } from '../../controller/EventInterpreter';


const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';
const QUERY_PARAMS_KEY = 'queryParams';
const SENSOR_A_KEY = 'sensorA';
const SENSOR_B_KEY = 'sensorB';
const CURRENT_EVENT_KEY = 'currentEvent';
const PREVIOUS_EVENT_KEY = 'previousEvent';

/**
 * Get the current air quality observations.
 *
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/echo?key1=value1&key2=value2 --data '{"key3":"value3","key4":"value4"}'
 */
export const echo = functions.https.onRequest(async (request, response) => {
  // Echo query parameters and body.
  const data = {
    queryParams: request.query,
    body: request.body
  };
  // The session ID allows a client to tell the server that multiple requests
  // come from the same session.
  if (SESSION_PARAM_KEY in request.query) {
    // If the client sends a session ID, respond with the session ID.
    data[SESSION_PARAM_KEY] = request.query[SESSION_PARAM_KEY];
  } else {
    // If the client does not send a session ID, create a session ID.
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  const session = data[SESSION_PARAM_KEY];
  // The build timestamp is unique to each device.
  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }

  try {
    await Database.save(session, data);
    const retrievedData = await Database.getCurrent(session);
    await updateEvent(retrievedData);
    response.status(200).send(retrievedData);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});

async function updateEvent(data) {
  if (!(BUILD_TIMESTAMP_PARAM_KEY in data)) {
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
  const oldData = await SensorEventDatabase.getCurrent(buildTimestamp);
  let oldEvent = null;
  if (CURRENT_EVENT_KEY in oldData) {
    oldEvent = oldData[CURRENT_EVENT_KEY];
  }
  const newEvent = getNewEventOrNull(oldEvent, sensorSnapshot, sensorSnapshot.timestampSeconds);
  if (newEvent !== null) {
    data[PREVIOUS_EVENT_KEY] = oldEvent;
    data[CURRENT_EVENT_KEY] = newEvent;
    await SensorEventDatabase.save(buildTimestamp, data);
  } else {
    // Saving the old data again will update FIRESTORE_databaseTimestamp and FIRESTORE_databaseTimestampSeconds.
    await SensorEventDatabase.save(buildTimestamp, oldData);
  }
}
