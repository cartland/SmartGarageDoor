/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as functions from 'firebase-functions/v1';

import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { DATABASE as BUTTON_HEALTH_DATABASE } from '../../database/ButtonHealthDatabase';
import { DATABASE as REMOTE_BUTTON_REQUEST_DATABASE } from '../../database/RemoteButtonRequestDatabase';
import {
  isRemoteButtonEnabled,
  getRemoteButtonPushKey,
  getRemoteButtonAuthorizedEmails,
} from '../../controller/config/ConfigAccessors';
import { isEmailInAllowlist } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

const BUILD_TIMESTAMP_PARAM_KEY = 'buildTimestamp';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

/**
 * Cold-start endpoint for mobile clients. Returns the current health
 * snapshot for the button device's buildTimestamp, or
 * `{ buttonState: "UNKNOWN", stateChangedAtSeconds: null, ... }`
 * when no buttonHealthCurrent doc exists yet.
 *
 * Auth chain mirrors handleAddRemoteButtonCommand byte-for-byte:
 *   config-enabled → push-key-header → push-key-match →
 *   id-token-header → verifyIdToken → email-authorized
 *
 * Same caveat as handleAddRemoteButtonCommand: verifyIdToken is NOT
 * wrapped in try/catch — malformed tokens propagate to the wrapper's
 * outer catch and yield 500 (not 401). Consistency with the existing
 * button auth handler is more important than the asymmetry with
 * Snooze (which wraps and returns 401).
 */
export async function handleButtonHealth(input: {
  query: any;
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

  const buildTimestamp: string | undefined = input.query?.[BUILD_TIMESTAMP_PARAM_KEY];
  if (!buildTimestamp || typeof buildTimestamp !== 'string' || buildTimestamp.length === 0) {
    return err(400, { error: 'Missing required parameter: ' + BUILD_TIMESTAMP_PARAM_KEY });
  }

  const record = await BUTTON_HEALTH_DATABASE.getCurrent(buildTimestamp);
  // `lastPollAtSeconds` is computed fresh from the polling history rather
  // than persisted in `buttonHealthCurrent` — this avoids ~17K Firestore
  // writes/day to one doc just to keep a freshness counter, at the cost of
  // one extra Firestore read per cold-start fetch (low frequency).
  const latestRequest = await REMOTE_BUTTON_REQUEST_DATABASE.getCurrent(buildTimestamp);
  const lastPollAtSeconds: number | null =
    latestRequest?.[DATABASE_TIMESTAMP_SECONDS_KEY] ?? null;
  if (!record) {
    // No doc yet — wire returns UNKNOWN per the design's bootstrap behavior.
    return ok({
      buildTimestamp,
      buttonState: 'UNKNOWN',
      stateChangedAtSeconds: null,
      lastPollAtSeconds,
    });
  }
  return ok({
    buildTimestamp,
    buttonState: record.state,
    stateChangedAtSeconds: record.stateChangedAtSeconds,
    lastPollAtSeconds,
  });
}

/**
 * curl -H "X-RemoteButtonPushKey: <key>" -H "X-AuthTokenGoogle: <id-token>" \
 *   "https://.../buttonHealth?buildTimestamp=<bt>"
 */
export const httpButtonHealth = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleButtonHealth({
      query: request.query,
      pushKeyHeader: request.get('X-RemoteButtonPushKey'),
      googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    // Catches a propagated AuthService.verifyIdToken throw — yields 500,
    // matching handleAddRemoteButtonCommand's behavior.
    console.error(error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
