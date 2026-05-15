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

import * as functions from 'firebase-functions/v1';

import { sendFCMForOldData } from '../../controller/fcm/OldDataFCM';
import { DATABASE as SensorEventDatabase } from '../../database/SensorEventDatabase';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import {
  getBuildTimestamp,
  requireBuildTimestamp,
  getRemoteButtonPushKey,
  getRemoteButtonAuthorizedEmails,
} from '../../controller/config/ConfigAccessors';
import { isEmailInAllowlist } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

// History: a DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021'
// constant lived here through server/16. Removed in A3 after
// production was verified to have body.buildTimestamp populated with
// that exact value, and server/16's warn-level fallback logs stayed
// empty for 24+ hours. See docs/archive/FIREBASE_HARDENING_PLAN.md → Part A / A3
// for the full rationale + revert path.

/**
 * Pure core — testable via fakes on ServerConfigDatabase,
 * SensorEventDatabase, and AuthService. Extracted in H2 of the
 * handler testing plan; auth chain added in the post-audit pass.
 *
 * Auth chain mirrors handleButtonHealth byte-for-byte:
 *   push-key-header → push-key-match → id-token-header →
 *   verifyIdToken → email-authorized
 *
 * Pre-audit this endpoint had NO auth — anyone could trigger
 * FCM dispatches via this URL (audit finding H2 / H3).
 *
 * Reads buildTimestamp from config (throws if missing), fetches the
 * current sensor event for that device, and delegates to
 * sendFCMForOldData. Returns whatever sendFCMForOldData returns —
 * which the HTTP wrapper passes straight through as the 200 body
 * on the happy path, or as a HandlerResult error otherwise.
 *
 * Same verifyIdToken-no-try/catch quirk as handleButtonHealth:
 * malformed tokens propagate to the wrapper's outer catch.
 */
export async function handleCheckForOpenDoorsRequest(input: {
  pushKeyHeader: string | undefined;
  googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<unknown>> {
  const config = await ServerConfigDatabase.get();
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
  if (!input.googleIdTokenHeader || input.googleIdTokenHeader.length <= 0) {
    const result = { error: 'Unauthorized (token).' };
    console.error(result);
    return err(401, result);
  }
  // Deliberately no try/catch — see doc above.
  const decodedToken = await AuthService.verifyIdToken(input.googleIdTokenHeader);
  const email = decodedToken.email;
  const authorizedEmails = getRemoteButtonAuthorizedEmails(config);
  if (!isEmailInAllowlist(email, authorizedEmails)) {
    const result = { error: 'Forbidden (user).' };
    console.error(result);
    return err(403, result);
  }
  const buildTimestamp = requireBuildTimestamp(
    getBuildTimestamp(config),
    'httpCheckForOpenDoors',
  );
  const eventData = await SensorEventDatabase.getCurrent(buildTimestamp);
  return ok(await sendFCMForOldData(buildTimestamp, eventData));
}

export const httpCheckForOpenDoors = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleCheckForOpenDoorsRequest({
      pushKeyHeader: request.get('X-RemoteButtonPushKey'),
      googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error('httpCheckForOpenDoors failed', error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
