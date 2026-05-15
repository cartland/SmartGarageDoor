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

import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import {
  isDeleteOldDataEnabled,
  getRemoteButtonPushKey,
  getRemoteButtonAuthorizedEmails,
} from '../../controller/config/ConfigAccessors';
import { deleteOldData } from '../../controller/DatabaseCleaner';
import { isEmailInAllowlist } from '../../controller/Auth';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

export interface DeleteOldDataResult {
  dryRun: boolean;
  summary: object;
}

/**
 * Pure core — testable via FakeServerConfigDatabase + FakeAuthService +
 * sinon stub on deleteOldData. H5 of the handler testing plan.
 *
 * Auth chain mirrors handleButtonHealth byte-for-byte:
 *   config-enabled → push-key-header → push-key-match →
 *   id-token-header → verifyIdToken → email-authorized
 *
 * Pre-audit this endpoint had NO auth — anyone on the internet who
 * knew the URL and the `deleteOldDataEnabled=true` config flag could
 * trigger bulk Firestore deletes. Audit finding H2.
 *
 * Same verifyIdToken-no-try/catch quirk as handleButtonHealth: a
 * malformed token propagates to the wrapper's outer catch and yields
 * 500 (not 401). Snooze wraps and returns 401 — inconsistency preserved.
 */
export async function handleDeleteOldData(input: {
  query: any;
  pushKeyHeader: string | undefined;
  googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<DeleteOldDataResult>> {
  const config = await ServerConfigDatabase.get();
  if (!isDeleteOldDataEnabled(config)) {
    console.log('Deleting data is disabled');
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
  let cutoffTimestampSeconds: number = null;
  let dryRun = false;
  if (input.query && 'dryRun' in input.query) {
    dryRun = true;
  }
  if (input.query && 'cutoffTimestampSeconds' in input.query) {
    const s: string = input.query['cutoffTimestampSeconds'].toString();
    cutoffTimestampSeconds = parseInt(s);
  }
  const deleteCount = await deleteOldData(cutoffTimestampSeconds, dryRun);
  return ok({
    dryRun: dryRun,
    summary: deleteCount,
  });
}

export const httpDeleteOldData = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleDeleteOldData({
      query: request.query,
      pushKeyHeader: request.get('X-RemoteButtonPushKey'),
      googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      await response.status(200).send(result.data);
    }
  } catch (error) {
    // Catches a propagated AuthService.verifyIdToken throw — yields 500,
    // matching handleButtonHealth / handleAddRemoteButtonCommand.
    console.error(error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
