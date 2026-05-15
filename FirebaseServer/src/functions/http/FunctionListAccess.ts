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
import { getFunctionListAuthorizedEmails } from '../../controller/config/ConfigAccessors';
import { SERVICE as AuthService } from '../../controller/AuthService';
import { isEmailInAllowlist } from '../../controller/Auth';
import { HandlerResult, ok, err } from '../HandlerResult';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

export interface FunctionListAccessResponse {
  enabled: boolean;
}

/**
 * Pure core for the GET function-list access endpoint.
 *
 *  - Method !== GET                  → 405 { error: 'Method Not Allowed.' }
 *  - Missing X-AuthTokenGoogle       → 401 { error: 'Unauthorized (token).' }
 *  - verifyIdToken throws            → 401 { error: 'Unauthorized (token).' }
 *  - Token verifies but no email     → 200 { enabled: false }
 *  - No allowlist configured (null)  → 200 { enabled: false }
 *  - Email NOT in allowlist          → 200 { enabled: true|false based on membership }
 *  - Otherwise                       → 200 { enabled: true }
 *
 * Why a 200 with `{enabled: false}` instead of a 403 for unauthorized
 * users: this endpoint is a UI hint — clients use it to decide whether
 * to render the Function List entry point. The actual security boundary
 * is the per-action handlers (`pushButton`, `snoozeNotifications`),
 * which already gate by allowlist. Returning 403 here would force the
 * client to special-case "user is signed in but feature-denied," which
 * is the same affirmative answer as `{enabled: false}` — keep one path.
 */
export async function handleFunctionListAccess(input: {
  method: string;
  googleIdTokenHeader: string | undefined;
}): Promise<HandlerResult<FunctionListAccessResponse>> {
  if (input.method !== 'GET') {
    return err(405, { error: 'Method Not Allowed.' });
  }
  if (!input.googleIdTokenHeader || input.googleIdTokenHeader.length <= 0) {
    return err(401, { error: 'Unauthorized (token).' });
  }
  let email: string;
  try {
    const decodedToken = await AuthService.verifyIdToken(input.googleIdTokenHeader);
    if (!decodedToken.email) {
      // Token verified but carried no email claim — treat as deny. Returned
      // here (inside the try) so `email` does not need to escape the block.
      return ok({ enabled: false });
    }
    email = decodedToken.email;
  } catch (error: any) {
    console.error(error);
    return err(401, { error: 'Unauthorized (token).' });
  }
  const config = await ServerConfigDatabase.get();
  const allowed = getFunctionListAuthorizedEmails(config);
  // `isEmailInAllowlist` is null-tolerant — a missing config field is
  // deny-all so a fresh deploy starts closed.
  return ok({ enabled: isEmailInAllowlist(email, allowed) });
}

/**
 * curl -H "X-AuthTokenGoogle: <id_token>" \
 *      "http://localhost:5000/PROJECT-ID/us-central1/functionListAccess"
 */
export const httpFunctionListAccess = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  const result = await handleFunctionListAccess({
    method: request.method,
    googleIdTokenHeader: request.get('X-AuthTokenGoogle'),
  });
  if (result.kind === 'error') {
    response.status(result.status).send(result.body);
  } else {
    response.status(200).send(result.data);
  }
});
