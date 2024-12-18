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

import { DATABASE as SensorEventDatabase } from '../../database/SensorEventDatabase';

import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull } from '../../controller/EventInterpreter';

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EVENT_HISTORY_MAX_COUNT_PARAM_KEY = "eventHistoryMaxCount";
const EVENT_HISTORY_MAX_COUNT_DEFAULT_VALUE = 12;

const SENSOR_A_PARAM_KEY = "sensorA";
const SENSOR_B_PARAM_KEY = "sensorB";
const TIMESTAMP_SECONDS_PARAM_KEY = "timestampSeconds";

const CURRENT_EVENT_DATA_KEY = "currentEventData";
const EVENT_HISTORY_KEY = "eventHistory";
const EVENT_HISTORY_COUNT_KEY = "eventHistoryCount";
const NEW_EVENT_KEY = "newEvent";
const OLD_EVENT_KEY = "oldEvent";

// const EVENT_DATABASE = new TimeSeriesDatabase('eventsCurrent', 'eventsAll');

/**
 * curl -H "Content-Type: application/json" http://localhost:5001/PROJECT-ID/us-central1/currentEventData?session=ABC&buildTimestamp=123&eventHistoryMaxCount=12
 */
export const httpCurrentEventData = functions.https.onRequest(async (request, response) => {
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

  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp) {
    response.status(400).send({
      'error': 'Invalid buildTimestamp'
    });
    return;
  }

  try {
    const currentData = await SensorEventDatabase.get(buildTimestamp);
    data[CURRENT_EVENT_DATA_KEY] = currentData;
    response.status(200).send(data);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5001/PROJECT-ID/us-central1/eventHistory?session=ABC&buildTimestamp=123&eventHistoryMaxCount=20
 */
export const httpEventHistory = functions.https.onRequest(async (request, response) => {
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

  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp) {
    response.status(400).send({
      'error': 'Invalid buildTimestamp'
    });
    return;
  }

  if (EVENT_HISTORY_MAX_COUNT_PARAM_KEY in request.query) {
    data[EVENT_HISTORY_MAX_COUNT_PARAM_KEY] = request.query[EVENT_HISTORY_MAX_COUNT_PARAM_KEY];
  } else {
    // Skip.
  }
  let eventHistoryMaxCount = parseInt(data[EVENT_HISTORY_MAX_COUNT_PARAM_KEY]);
  if (!eventHistoryMaxCount) {
    eventHistoryMaxCount = EVENT_HISTORY_MAX_COUNT_DEFAULT_VALUE;
  }

  try {
    const allData = await SensorEventDatabase.getRecentForBuildTimestamp(buildTimestamp, eventHistoryMaxCount);
    data[EVENT_HISTORY_KEY] = allData;
    data[EVENT_HISTORY_COUNT_KEY] = allData.length;
    response.status(200).send(data);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/event?session=ABC
 */
export const httpNextEvent = functions.https.onRequest(async (request, response) => {
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

  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];

  const sensorSnapshot = <SensorSnapshot>{
    sensorA: null,
    sensorB: null,
    timestampSeconds: 0,
  }
  if (SENSOR_A_PARAM_KEY in request.query) {
    sensorSnapshot.sensorA = String(request.query[SENSOR_A_PARAM_KEY])
  }
  if (SENSOR_B_PARAM_KEY in request.query) {
    sensorSnapshot.sensorB = String(request.query[SENSOR_B_PARAM_KEY])
  }
  let timestampSeconds: number = null;
  if (TIMESTAMP_SECONDS_PARAM_KEY in request.query) {
    timestampSeconds = parseInt(String(request.query[TIMESTAMP_SECONDS_PARAM_KEY]));
  }
  try {
    const oldEvent = await SensorEventDatabase.get(buildTimestamp);
    const newEvent = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    if (newEvent !== null) {
      await SensorEventDatabase.set(buildTimestamp, newEvent);
    }
    data[OLD_EVENT_KEY] = oldEvent;
    data[NEW_EVENT_KEY] = newEvent;
    response.status(200).send(data);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
