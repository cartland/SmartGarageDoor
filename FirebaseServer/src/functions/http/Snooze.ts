/**
 * Copyright 2024 Chris Cartland. All Rights Reserved.
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

import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { isSnoozeNotificationsEnabled, getRemoteButtonPushKey, getRemoteButtonAuthorizedEmails } from '../../controller/config/ConfigAccessors';
import { isAuthorizedToPushRemoteButton } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { getSnoozeStatus, SnoozeLatestParams, SnoozeLatestResponse, SubmitSnoozeParams, submitSnoozeNotificationsRequest, SubmitSnoozeResponse } from '../../controller/SnoozeNotifications';
import { HandlerResult, ok, err } from '../HandlerResult';

const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
const SNOOZE_DURATION_PARAM_KEY = 'snoozeDuration';
const SNOOZE_EVENT_TIMESTAMP_KEY = 'snoozeEventTimestamp';

/**
 * Pure core for the snooze-latest read endpoint. H4 of the handler
 * testing plan.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - Config disabled  → 400 { error: 'Disabled' }
 *  - Method !== GET   → 405 { error: 'Method Not Allowed.' }
 *  - Missing buildTimestamp → 400 { error: 'Missing required parameter: buildTimestamp' }
 *  - Controller error → 500 {snoozeResponse}
 *  - Success          → 200 {snoozeResponse}
 */
export async function handleSnoozeNotificationsLatest(input: {
  method: string;
  query: any;
}): Promise<HandlerResult<SnoozeLatestResponse>> {
  const config = await ServerConfigDatabase.get();
  if (!isSnoozeNotificationsEnabled(config)) {
    return err(400, { error: 'Disabled' });
  }
  if (input.method !== 'GET') {
    return err(405, { error: 'Method Not Allowed.' });
  }
  const params: SnoozeLatestParams = <SnoozeLatestParams>{
    buildTimestamp: input.query?.[BUILD_TIMESTAMP_PARAM_KEY] as string,
  };
  if (!params.buildTimestamp) {
    console.error('No build timestamp in request');
    return err(400, { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY });
  }
  const snoozeResponse: SnoozeLatestResponse = await getSnoozeStatus(params);
  if (snoozeResponse.error) {
    console.error('Returning HTTP 500 error');
    return err(500, snoozeResponse);
  }
  console.info('Returning HTTP 200 success:', snoozeResponse);
  return ok(snoozeResponse);
}

/**
 * Get latest Snooze request.
 *
 * curl -X GET \
 *    -H "Content-Type: application/json" \
 *    -d '{}' \ "http://localhost:5000/PROJECT-ID/us-central1/snoozeNotificationsLatest?buildTimestamp=Sat%20Mar%2013%2014%3A45%3A00%202021"
 */
export const httpSnoozeNotificationsLatest = functions.https.onRequest(async (request, response) => {
  const result = await handleSnoozeNotificationsLatest({
    method: request.method,
    query: request.query,
  });
  if (result.kind === 'error') {
    response.status(result.status).send(result.body);
  } else {
    response.status(200).send(result.data);
  }
});

/**
 * Pure core for the snooze-submit endpoint. H4 (write) of
 * docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md.
 *
 * **Preserved quirks (from 5-reviewer audit):**
 *
 * 1. `let email = null;` declared OUTSIDE the try/catch block. The
 *    catch returns 401 so a null-email path only reaches the
 *    `isAuthorizedToPushRemoteButton(email, ...)` call when the token
 *    was successfully verified. Relocating the declaration inside
 *    the try would subtly change scoping — preserved as-is.
 *
 * 2. The params-parse try/catch at `const params = <SubmitSnoozeParams>{...}`
 *    is structurally unreachable (a type cast cannot throw). Preserved
 *    as dead code because it documents the original author's intent
 *    that param shape is "defensively" captured.
 *
 * 3. `verifyIdToken` IS wrapped in try/catch → returns 401 on throw.
 *    This differs from `handleAddRemoteButtonCommand` which does NOT
 *    wrap → 500. Tests must pin both paths.
 */
export async function handleSnoozeNotificationsRequest(input: {
    method: string;
    query: any;
    pushKeyHeader: string | undefined;
    googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<any>> {
    const config = await ServerConfigDatabase.get();
    if (!isSnoozeNotificationsEnabled(config)) {
        return err(400, { error: 'Disabled' });
    }
    if (input.method !== 'POST') {
        return err(405, { error: 'Method Not Allowed.' });
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
    // Preserved quirk: `email` declared outside the try; the catch
    // returns, so `email` only survives to the authorization step
    // when the token successfully verifies.
    let email = null;
    try {
        const decodedToken = await AuthService.verifyIdToken(input.googleIdTokenHeader);
        email = decodedToken.email;
    } catch (error: any) {
        console.error(error);
        const result = { error: 'Unauthorized (token).' };
        return err(401, result);
    }
    console.log('email:', email);
    const authorizedEmails = getRemoteButtonAuthorizedEmails(config);
    if (!isAuthorizedToPushRemoteButton(email, authorizedEmails)) {
        const result = { error: 'Forbidden (user).' };
        console.error(result);
        return err(403, result);
    }

    // Preserved quirk: structurally unreachable catch. The type cast
    // cannot throw. Kept as-is to match pre-extraction code.
    let params: SubmitSnoozeParams = null;
    try {
        params = <SubmitSnoozeParams>{
            buildTimestamp: input.query?.[BUILD_TIMESTAMP_PARAM_KEY] as string,
            snoozeDuration: input.query?.[SNOOZE_DURATION_PARAM_KEY] as string,
            snoozeEventTimestamp: input.query?.[SNOOZE_EVENT_TIMESTAMP_KEY] as string,
        };
    } catch {
        const result = {
            error: 'Could not parse parameters: '
                + BUILD_TIMESTAMP_PARAM_KEY + ', ' + SNOOZE_DURATION_PARAM_KEY + ', ' + SNOOZE_EVENT_TIMESTAMP_KEY,
        };
        console.error(result.error);
        return err(400, result);
    }
    if (!params.buildTimestamp) {
        console.error('No build timestamp in request');
        return err(400, { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY });
    }
    if (!params.snoozeDuration) {
        console.error('No snooze duration in request');
        return err(400, { error: 'Missing required parameter: ' + SNOOZE_DURATION_PARAM_KEY });
    }
    if (!params.snoozeEventTimestamp) {
        console.error('No snooze event timestamp in request');
        return err(400, { error: 'Missing required parameter: ' + SNOOZE_EVENT_TIMESTAMP_KEY });
    }

    const snoozeResponse: SubmitSnoozeResponse = await submitSnoozeNotificationsRequest(params);

    if (snoozeResponse.error) {
        console.error('Returning HTTP 500 error');
        return err(snoozeResponse.code ?? 500, snoozeResponse);
    }
    const snooze = snoozeResponse.snooze;
    console.info('Returning HTTP 200 success:', snooze);
    return ok(snooze);
}

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/PROJECT-ID/us-central1/snoozeNotificationsLatest?buildTimestamp=buildTimestamp
 */
export const httpSnoozeNotificationsRequest = functions.https.onRequest(async (request, response) => {
    const result = await handleSnoozeNotificationsRequest({
        method: request.method,
        query: request.query,
        pushKeyHeader: request.get('X-RemoteButtonPushKey'),
        googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
    });
    if (result.kind === 'error') {
        response.status(result.status).send(result.body);
    } else {
        response.status(200).send(result.data);
    }
});
