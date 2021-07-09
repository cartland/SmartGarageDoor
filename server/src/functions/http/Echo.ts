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

import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

const UPDATE_DATABASE = new TimeSeriesDatabase('updateCurrent', 'updateAll');

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";

/**
 * HTTP endpoint captures request parameters, stores them in the database, and returns the data.
 */
export const httpEcho = functions.https.onRequest(async (request, response) => {
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
    await UPDATE_DATABASE.save(session, data);
    const retrievedData = await UPDATE_DATABASE.getCurrent(session);
    response.status(200).send(retrievedData);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
