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
import * as functions from 'firebase-functions/v1';

import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { isRemoteButtonEnabled, getRemoteButtonPushKey, getRemoteButtonAuthorizedEmails } from '../../controller/config/ConfigAccessors';
import { DATABASE as REMOTE_BUTTON_COMMAND_DATABASE } from '../../database/RemoteButtonCommandDatabase';
import { DATABASE as REMOTE_BUTTON_REQUEST_DATABASE } from '../../database/RemoteButtonRequestDatabase';
import { isAuthorizedToPushRemoteButton } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';

import { RemoteButtonCommand } from '../../model/RemoteButtonCommand';
import { HandlerResult, ok, err } from '../HandlerResult';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';
const SESSION_PARAM_KEY = "session";
const BUTTON_ACK_TOKEN_PARAM_KEY = "buttonAckToken";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const EMAIL_PARAM_KEY = "email";

const REMOTE_BUTTON_MIN_PERIOD_SECONDS = 10;
const REMOTE_BUTTON_COMMAND_TIMEOUT_SECONDS = 60;

/**
 * Pure core for the device-polling endpoint. H3 (pubsub→HTTP
 * continuation) of the handler testing plan.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - Config not enabled                            → 400 Disabled.
 *  - `buildTimestamp` missing from query           → passed through
 *    as `undefined` (the empty `// Skip.` else branch is preserved so
 *    downstream `getCurrent(undefined)` calls produce the same logs
 *    as before).
 *  - Request is ALWAYS saved for logging, regardless of what branch
 *    the command state machine takes.
 *  - Ack-token state machine: saves a noop-command + returns the
 *    freshly re-read version (Firestore timestamps populated) when
 *    `shouldStopSendingRemoteButtonCommand && oldAckToken !== ''`;
 *    otherwise returns `oldCommand` directly (no save, no re-read).
 *    This asymmetry — return fresh on save-path, return pre-save
 *    on else-path — is intentional. Tests pin it.
 *  - Any throw during the save/read sequence                → 500.
 *
 * No auth: the ESP32 polls this endpoint and has no credentials.
 */
export async function handleRemoteButtonPoll(input: {
  query: any;
  body: any;
}): Promise<HandlerResult<any>> {
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    return err(400, { error: 'Disabled' });
  }
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  if (input.query && SESSION_PARAM_KEY in input.query) {
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  const session = data[SESSION_PARAM_KEY];
  if (input.query && BUTTON_ACK_TOKEN_PARAM_KEY in input.query) {
    data[BUTTON_ACK_TOKEN_PARAM_KEY] = input.query[BUTTON_ACK_TOKEN_PARAM_KEY];
  } else {
    // Client sent no ack token. Leave data[...] undefined — preserves the
    // "undefined ack token means buttonAcknowledged stays false" semantics.
  }
  const buttonAckToken = data[BUTTON_ACK_TOKEN_PARAM_KEY];
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip. Preserved from pre-extraction: buildTimestamp flows through as
    // `undefined` and downstream DB reads see that — byte-identical to the
    // prior behavior.
  }
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  // Save the request. This is mostly for logging purposes.
  await REMOTE_BUTTON_REQUEST_DATABASE.save(buildTimestamp, data);
  const oldCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
  const oldAckToken = oldCommand?.[BUTTON_ACK_TOKEN_PARAM_KEY] ?? '';
  const timeSinceLastRemoteButtonCommandSeconds = oldCommand?.[DATABASE_TIMESTAMP_SECONDS_KEY]
    ? firebase.firestore.Timestamp.now().seconds - oldCommand[DATABASE_TIMESTAMP_SECONDS_KEY]
    : Number.MAX_SAFE_INTEGER;
  // Clear the pending command when any of:
  //   1) the stored command has no ack token (invalid state),
  //   2) the client echoed back the matching ack token (acknowledged), or
  //   3) the stored command is older than the timeout AND still has an
  //      ack token (replace stale).
  const commandDoesNotContainAckToken = !oldCommand || !(BUTTON_ACK_TOKEN_PARAM_KEY in oldCommand);
  const buttonAcknowledged = buttonAckToken === oldAckToken;
  const replaceOldCommand = (timeSinceLastRemoteButtonCommandSeconds > REMOTE_BUTTON_COMMAND_TIMEOUT_SECONDS)
    && (oldAckToken !== '');
  const shouldStopSendingRemoteButtonCommand =
    commandDoesNotContainAckToken
    || buttonAcknowledged
    || replaceOldCommand;
  if (shouldStopSendingRemoteButtonCommand &&
    typeof oldAckToken === 'string' &&
    oldAckToken !== ''
  ) {
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
    // Intentional re-read: the fresh document carries Firestore-injected
    // fields (FIRESTORE_databaseTimestampSeconds). The else branch below
    // returns `oldCommand` without a second read — preserve that split.
    const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
    return ok(updatedCommand);
  }
  return ok(oldCommand);
}

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/remoteButton?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const httpRemoteButton = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleRemoteButtonPoll({
      query: request.query,
      body: request.body,
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  }
  catch (error) {
    console.error(error);
    response.status(500).send(error);
  }
});

/**
 * Pure core for the push-button (add-command) endpoint. H3 (HTTP
 * add-command portion) of docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * **Preserved quirks — these look like bugs; do NOT "fix" them here:**
 *
 * 1. Missing-buildTimestamp path DOES NOT early-return. The 400
 *    response is generated, but execution continues through the DB
 *    section, resulting in `save(undefined, data)` to
 *    RemoteButtonCommandDatabase. Express treats the first send as
 *    the client-facing response; subsequent sends throw internally
 *    and are swallowed. Pinned in tests via the `saved[0][0] ===
 *    undefined` assertion.
 *
 * 2. `verifyIdToken` is NOT wrapped in try/catch — throws propagate
 *    to the wrapper's outer catch and become 500 Internal Server
 *    Error. This differs from `handleSnoozeNotificationsRequest`
 *    which wraps and returns 401. The asymmetry is real;
 *    `FakeAuthService.failNextVerify()` exercises both paths.
 *
 * 3. `console.error(result)` logs the whole error object (not just
 *    `.error`). Extraction preserves both styles — some auth failures
 *    log the object, some log the error string.
 *
 * Auth order (pinned byte-for-byte against Snooze):
 *   config-enabled → push-key-header → push-key-match → id-token-header
 *   → verifyIdToken → email-authorized
 */
export async function handleAddRemoteButtonCommand(input: {
  query: any;
  body: any;
  pushKeyHeader: string | undefined;
  googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<any>> {
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    return err(400, { error: 'Disabled' });
  }
  if (!input.pushKeyHeader || input.pushKeyHeader.length <= 0) {
    const result = { error: 'Unauthorized (key).' };
    console.error(result);
    return err(401, result);
  }
  if (getRemoteButtonPushKey(config) !== input.pushKeyHeader) {
    const result = { error: 'Forbidden (key).' };
    console.error(result);
    return err(403, result);
  }
  console.log('googleIdToken:', input.googleIdTokenHeader);
  if (!input.googleIdTokenHeader || input.googleIdTokenHeader.length <= 0) {
    const result = { error: 'Unauthorized (token).' };
    console.error(result);
    return err(401, result);
  }
  // NOTE the deliberate absence of try/catch — preserves the
  // pre-extraction behavior where a malformed token propagates out of
  // the handler to the wrapper's outer catch, yielding 500 (not 401).
  // Snooze's handler wraps this call in try/catch and returns 401 instead.
  const decodedToken = await AuthService.verifyIdToken(input.googleIdTokenHeader);
  const email = decodedToken.email;
  console.log('email:', email);
  const authorizedEmails = getRemoteButtonAuthorizedEmails(config);
  if (!isAuthorizedToPushRemoteButton(email, authorizedEmails)) {
    const result = { error: 'Forbidden (user).' };
    console.error(result);
    return err(403, result);
  }

  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  if (input.query && SESSION_PARAM_KEY in input.query) {
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  if (input.query && BUTTON_ACK_TOKEN_PARAM_KEY in input.query) {
    data[BUTTON_ACK_TOKEN_PARAM_KEY] = input.query[BUTTON_ACK_TOKEN_PARAM_KEY];
  } else {
    // TODO (historical): requiring an ack token was considered but
    // intentionally never enforced — 3.5 years of production traffic
    // before this extraction relied on the warn-only path. Preserved.
    console.warn('No button ack token in request');
  }

  // Preserved quirk (1): the missing-buildTimestamp path generates a
  // 400 response but does NOT short-circuit. Execution continues and
  // the eventual `save(undefined, data)` fires (unless rate-limited).
  // `pendingErrorResponse` captures the 400 so that the eventual
  // return value mirrors what Express delivered to the client first.
  let pendingErrorResponse: HandlerResult<any> | null = null;
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    console.error('No build timestamp in request');
    pendingErrorResponse = err(400, {
      error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY,
    });
  }
  data[EMAIL_PARAM_KEY] = email;
  const buildTimestamp = data[BUILD_TIMESTAMP_PARAM_KEY];
  const oldCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
  const timeSinceLastRemoteButtonCommandSeconds = oldCommand?.[DATABASE_TIMESTAMP_SECONDS_KEY]
    ? firebase.firestore.Timestamp.now().seconds - oldCommand[DATABASE_TIMESTAMP_SECONDS_KEY]
    : Number.MAX_SAFE_INTEGER;
  if (timeSinceLastRemoteButtonCommandSeconds < REMOTE_BUTTON_MIN_PERIOD_SECONDS) {
    console.log('Time since remote button press is less than minimum',
      timeSinceLastRemoteButtonCommandSeconds, '<', REMOTE_BUTTON_MIN_PERIOD_SECONDS);
    const result = { error: 'Conflict (too many recent requests).' };
    console.error(result);
    // If a pending 400 was queued (missing buildTimestamp), Express's
    // "first send wins" rule means the client saw 400, not 409.
    // Preserve: the pending error takes priority.
    return pendingErrorResponse ?? err(409, result);
  }
  // Fires even when pendingErrorResponse is set — preserves the
  // pre-extraction `save(undefined, data)` side effect.
  await REMOTE_BUTTON_COMMAND_DATABASE.save(buildTimestamp, data);
  const updatedCommand = await REMOTE_BUTTON_COMMAND_DATABASE.getCurrent(buildTimestamp);
  return pendingErrorResponse ?? ok(updatedCommand);
}

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/addRemoteButtonCommand?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const httpAddRemoteButtonCommand = functions.https.onRequest(async (request, response) => {
  try {
    const result = await handleAddRemoteButtonCommand({
      query: request.query,
      body: request.body,
      pushKeyHeader: request.get('X-RemoteButtonPushKey'),
      googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  }
  catch (error) {
    // Catches a propagated AuthService.verifyIdToken throw — yields 500,
    // matching the pre-extraction behavior where the uncaught exception
    // escaped the function and Firebase runtime's own error handler
    // sent 500.
    console.error(error);
    response.status(500).send(error);
  }
});
