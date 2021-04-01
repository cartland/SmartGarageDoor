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

import * as firebase from 'firebase-admin';
import * as functions from 'firebase-functions';

import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

import { Config } from '../../database/ServerConfigDatabase';

import { RemoteButtonCommand } from '../../model/RemoteButtonCommand';

const REMOTE_BUTTON_COMMAND_DATABASE = new TimeSeriesDatabase('remoteButtonCommandCurrent', 'remoteButtonCommandAll');
const REMOTE_BUTTON_REQUEST_DATABASE = new TimeSeriesDatabase('remoteButtonRequestCurrent', 'remoteButtonRequestAll');

const SESSION_PARAM_KEY = "session";
const BUTTON_ACK_TOKEN_PARAM_KEY = "buttonAckToken";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EMAIL_PARAM_KEY = "email"

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/remoteButton?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const remoteButton = functions.https.onRequest(async (request, response) => {
  const config = await Config.get();
  if (!Config.isRemoteButtonEnabled(config)) {
    response.status(400).send({ error: 'Disabled' });
    return;
  }
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
  if (BUTTON_ACK_TOKEN_PARAM_KEY in request.query) {
    // If the client sends a session ID, respond with the session ID.
    data[BUTTON_ACK_TOKEN_PARAM_KEY] = request.query[BUTTON_ACK_TOKEN_PARAM_KEY];
  } else {
    // TODO.
  }
  const buttonAckToken = data[BUTTON_ACK_TOKEN_PARAM_KEY];
  // The build timestamp is unique to each device.
  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  try {
    await REMOTE_BUTTON_REQUEST_DATABASE.save(buildTimestamp, data);
    const oldCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    if (!(BUTTON_ACK_TOKEN_PARAM_KEY in oldCommand) || buttonAckToken === oldCommand[BUTTON_ACK_TOKEN_PARAM_KEY]) {
      const noopCommand = <RemoteButtonCommand>{
        session: session,
        buildTimestamp: buildTimestamp,
        buttonAckToken: '',
      }
      await REMOTE_BUTTON_COMMAND_DATABASE.save(buildTimestamp, noopCommand);
      const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
      response.status(200).send(updatedCommand);
    } else {
      response.status(200).send(oldCommand);
    }
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/addRemoteButtonCommand?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const addRemoteButtonCommand = functions.https.onRequest(async (request, response) => {
  const config = await Config.get();
  if (!Config.isRemoteButtonEnabled(config)) {
    response.status(400).send({ error: 'Disabled' });
    return;
  }
  const googleIdToken = request.get('X-AuthTokenGoogle');
  console.log('googleIdToken:', googleIdToken);
  // if (!googleIdToken || googleIdToken.length <= 0) {
  //   response.status(401).send({ error: 'Unauthorized token.' });
  //   return;
  // }
  let email = "";
  if (googleIdToken && googleIdToken.length > 0) {
    const decodedToken = await firebase.auth().verifyIdToken(googleIdToken);
    email = decodedToken.email;
    console.log('email:', email);
  }
  const buttonPushKeyHeader = request.get('X-RemoteButtonPushKey');
  console.log('buttonPushKeyHeader', buttonPushKeyHeader);
  if (!buttonPushKeyHeader || buttonPushKeyHeader.length <= 0) {
    response.status(401).send({ error: 'Unauthorized key.' });
    return;
  }
  if (Config.getRemoteButtonPushKey(config) !== buttonPushKeyHeader) {
    response.status(403).send({ error: 'Forbidden.' });
    return;
  }
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
  if (BUTTON_ACK_TOKEN_PARAM_KEY in request.query) {
    // If the client sends a session ID, respond with the session ID.
    data[BUTTON_ACK_TOKEN_PARAM_KEY] = request.query[BUTTON_ACK_TOKEN_PARAM_KEY];
  } else {
    // TODO.
  }
  // The build timestamp is unique to each device.
  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }
  data[EMAIL_PARAM_KEY] = email;
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  try {
    await REMOTE_BUTTON_COMMAND_DATABASE.save(buildTimestamp, data);
    const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    response.status(200).send(updatedCommand);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
