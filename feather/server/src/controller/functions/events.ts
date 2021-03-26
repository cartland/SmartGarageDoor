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

import * as SensorEventDatabase from '../../database/SensorEventDatabase';

import { SensorEvent, Unknown } from '../../model/SensorEvent';
import { SensorSnapshot } from '../../model/SensorSnapshot';

import { getNewEventOrNull } from '../../controller/EventInterpreter';
import { SSL_OP_SSLEAY_080_CLIENT_DH_BUG } from 'node:constants';

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";

const SENSOR_A_PARAM_KEY = "sensorA";
const SENSOR_B_PARAM_KEY = "sensorB";
const TIMESTAMP_SECONDS_PARAM_KEY = "timestampSeconds";

const NEW_EVENT_KEY = "newEvent";
const OLD_EVENT_KEY = "oldEvent";

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/event?session=ABC
 */
export const nextEvent = functions.https.onRequest(async (request, response) => {
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
    const oldEvent = await SensorEventDatabase.getCurrent(buildTimestamp);
    const newEvent = getNewEventOrNull(oldEvent, sensorSnapshot, timestampSeconds);
    if (newEvent !== null) {
      await SensorEventDatabase.save(buildTimestamp, newEvent);
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
