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
import { isAuthorizedToPushRemoteButton } from '../../controller/Auth';

import { RemoteButtonCommand } from '../../model/RemoteButtonCommand';

const REMOTE_BUTTON_COMMAND_DATABASE = new TimeSeriesDatabase('remoteButtonCommandCurrent', 'remoteButtonCommandAll');
const REMOTE_BUTTON_REQUEST_DATABASE = new TimeSeriesDatabase('remoteButtonRequestCurrent', 'remoteButtonRequestAll');

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';
const SESSION_PARAM_KEY = "session";
const BUTTON_ACK_TOKEN_PARAM_KEY = "buttonAckToken";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EMAIL_PARAM_KEY = "email";

const REMOTE_BUTTON_MIN_PERIOD_SECONDS = 10;
const REMOTE_BUTTON_COMMAND_TIMEOUT_SECONDS = 60;

const REMOTE_BUTTON_REQUEST_ERROR_SECONDS = 60 * 10;
const REMOTE_BUTTON_REQUEST_ERROR_DATABASE = new TimeSeriesDatabase('remoteButtonRequestErrorCurrent', 'remoteButtonRequestErrorAll');

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/remoteButton?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const httpRemoteButton = functions.https.onRequest(async (request, response) => {
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
    // Save the request. This is mostly for logging purposes.
    await REMOTE_BUTTON_REQUEST_DATABASE.save(buildTimestamp, data);
    // Get the remote button command from the database. We need to return this value.
    const oldCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    const oldAckToken = oldCommand?.[BUTTON_ACK_TOKEN_PARAM_KEY] ?? '';
    const timeSinceLastRemoteButtonCommandSeconds = oldCommand?.[DATABASE_TIMESTAMP_SECONDS_KEY]
      ? firebase.firestore.Timestamp.now().seconds - oldCommand[DATABASE_TIMESTAMP_SECONDS_KEY]
      : Number.MAX_SAFE_INTEGER;
    // We reset the button command if any of the following are true:
    // Condition 1) The command does not contain an ACK token (invaild state).
    const commandDoesNotContainAckToken = !oldCommand || !(BUTTON_ACK_TOKEN_PARAM_KEY in oldCommand);
    // Condition 2) The client sends a request with the ACK token (successfully acknowledge).
    const buttonAcknowledged = buttonAckToken === oldAckToken;
    // Condition 3) The command is too old.
    const replaceOldCommand = (timeSinceLastRemoteButtonCommandSeconds > REMOTE_BUTTON_COMMAND_TIMEOUT_SECONDS)
      && (oldAckToken !== '');
    const shouldStopSendingRemoteButtonCommand =
      commandDoesNotContainAckToken // Condition 1.
      || buttonAcknowledged // Condition 2.
      || replaceOldCommand; // Condition 3.
    if (shouldStopSendingRemoteButtonCommand) {
      const noopCommand = <RemoteButtonCommand>{
        session: session,
        buildTimestamp: buildTimestamp,
        buttonAckToken: '',
        commandDidNotContainAckToken: commandDoesNotContainAckToken,
        commandAcknowledged: buttonAcknowledged,
        commandTimeout: replaceOldCommand,
        oldAckToken: oldAckToken,
      };
      console.log('Saving noop command:', noopCommand);
      await REMOTE_BUTTON_COMMAND_DATABASE.save(buildTimestamp, noopCommand);
      const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
      response.status(200).send(updatedCommand);
      return;
    } else {
      response.status(200).send(oldCommand);
      return;
    }
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
    return;
  }
});

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/addRemoteButtonCommand?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const httpAddRemoteButtonCommand = functions.https.onRequest(async (request, response) => {
  const config = await Config.get();
  if (!Config.isRemoteButtonEnabled(config)) {
    response.status(400).send({ error: 'Disabled' });
    return;
  }
  const buttonPushKeyHeader = request.get('X-RemoteButtonPushKey');
  if (!buttonPushKeyHeader || buttonPushKeyHeader.length <= 0) {
    const result = { error: 'Unauthorized (key).' };
    console.error(result);
    response.status(401).send(result);
    return;
  }
  if (Config.getRemoteButtonPushKey(config) !== buttonPushKeyHeader) {
    const result = { error: 'Forbidden (key).' };
    console.error(result);
    response.status(403).send(result);
    return;
  }
  const googleIdToken = request.get('X-AuthTokenGoogle');
  console.log('googleIdToken:', googleIdToken);
  if (!googleIdToken || googleIdToken.length <= 0) {
    const result = { error: 'Unauthorized (token).' };
    console.error(result);
    response.status(401).send(result);
    return;
  }
  const decodedToken = await firebase.auth().verifyIdToken(googleIdToken);
  const email = decodedToken.email;
  console.log('email:', email);
  const authorizedEmails = Config.getRemoteButtonAuthorizedEmails(config);
  if (!isAuthorizedToPushRemoteButton(email, authorizedEmails)) {
    const result = { error: 'Forbidden (user).' };
    console.error(result);
    response.status(403).send(result);
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
    // Button ack token needs to be unique for each request (prefer random).
    data[BUTTON_ACK_TOKEN_PARAM_KEY] = request.query[BUTTON_ACK_TOKEN_PARAM_KEY];
  } else {
    // TODO: Determine if we should abort the request at this point.
    // My memory suggests that we should require a button ack token,
    // but this code allows us to submit a request with a non-existent token.
    // However, since this code has been running for 3.5 years,
    // I do not want to change the behavior without better testing.
    console.warn('No button ack token in request');
  }
  // The build timestamp is unique to each device.
  if (BUILD_TIMESTAMP_PARAM_KEY in request.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = request.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // TODO: Determine if we should abort the request at this point.
    // I think a build timestamp should be required.
    console.error('No build timestamp in request');
    const result = { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY };
    response.status(400).send(result);
  }
  data[EMAIL_PARAM_KEY] = email;
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  try {
    const oldCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    const timeSinceLastRemoteButtonCommandSeconds = oldCommand?.[DATABASE_TIMESTAMP_SECONDS_KEY]
      ? firebase.firestore.Timestamp.now().seconds - oldCommand[DATABASE_TIMESTAMP_SECONDS_KEY]
      : Number.MAX_SAFE_INTEGER;
    if (timeSinceLastRemoteButtonCommandSeconds < REMOTE_BUTTON_MIN_PERIOD_SECONDS) {
      console.log('Time since remote button press is less than minimum',
        timeSinceLastRemoteButtonCommandSeconds, '<', REMOTE_BUTTON_MIN_PERIOD_SECONDS);
      const result = { error: 'Conflict (too many recent requests).' };
      console.error(result);
      response.status(409).send(result);
      return;
    }
    // Submit new remote button command.
    await REMOTE_BUTTON_COMMAND_DATABASE.save(buildTimestamp, data);
    const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    response.status(200).send(updatedCommand);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
