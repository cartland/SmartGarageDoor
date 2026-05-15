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

import * as functions from 'firebase-functions/v1';

import { DATABASE as UpdateDatabase } from '../../database/UpdateDatabase';
import { HTTP_RUNTIME_OPTS } from '../HttpRuntime';

const SESSION_PARAM_KEY = "session";
const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";

/**
 * Pure core — testable with plain object args. Handler-body extraction
 * per docs/archive/FIREBASE_HANDLER_TESTING_PLAN.md (Phase H1 pilot).
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 * - Builds the echo `data` payload from query + body.
 * - Uses the provided `session` query param, or generates a v4 UUID.
 * - Passes `buildTimestamp` through if present in the query.
 * - Saves to UpdateDatabase keyed by session, then returns the stored
 *   document read back from `getCurrent(session)`.
 */
export async function handleEchoRequest(input: {
  query: any;
  body: any;
}): Promise<any> {
  const data: any = {
    queryParams: input.query,
    body: input.body,
  };
  // The session ID allows a client to tell the server that multiple requests
  // come from the same session.
  if (input.query && SESSION_PARAM_KEY in input.query) {
    // If the client sends a session ID, respond with the session ID.
    data[SESSION_PARAM_KEY] = input.query[SESSION_PARAM_KEY];
  } else {
    // If the client does not send a session ID, create a session ID.
    data[SESSION_PARAM_KEY] = uuidv4();
  }
  const session = data[SESSION_PARAM_KEY];
  // The build timestamp is unique to each device.
  if (input.query && BUILD_TIMESTAMP_PARAM_KEY in input.query) {
    data[BUILD_TIMESTAMP_PARAM_KEY] = input.query[BUILD_TIMESTAMP_PARAM_KEY];
  } else {
    // Skip.
  }

  await UpdateDatabase.save(session, data);
  return UpdateDatabase.getCurrent(session);
}

/**
 * HTTP endpoint captures request parameters, stores them in the database, and returns the data.
 */
export const httpEcho = functions.runWith(HTTP_RUNTIME_OPTS).https.onRequest(async (request, response) => {
  try {
    const result = await handleEchoRequest({
      query: request.query,
      body: request.body,
    });
    response.status(200).send(result);
  }
  catch (error) {
    console.error(error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
