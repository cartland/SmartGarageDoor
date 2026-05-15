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
import { HandlerResult, ok, err } from '../HandlerResult';

/**
 * Helper — navigate `functions.config()` to the `serverconfig.<field>`
 * string. Returns null when missing at any level. Keeps the config
 * plumbing out of the pure handler core so tests can pass a plain
 * string (or null) without stubbing the functions.config() global.
 */
export function readServerConfigSecret(
  functionConfig: any,
  field: 'key' | 'updatekey',
): string | null {
  if (!functionConfig || !('serverconfig' in functionConfig)) {
    return null;
  }
  const section = functionConfig['serverconfig'];
  if (!(field in section)) {
    return null;
  }
  return section[field] ?? null;
}

/**
 * Pure core for the GET server-config endpoint. H5 of the handler
 * testing plan.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - No `serverconfig.key` in functions.config() → 500 with the
 *    'Deploy Firebase Functions config with serverconfig.key' message.
 *  - Empty/missing X-ServerConfigKey header → 401.
 *  - Wrong key                              → 403.
 *  - Non-GET method                         → 405.
 *  - Otherwise                              → 200 with current config.
 */
export async function handleServerConfigRead(input: {
  method: string;
  requestKey: string | undefined;
  expectedKey: string | null;
}): Promise<HandlerResult<unknown>> {
  if (input.expectedKey === null || input.expectedKey === undefined) {
    const error = 'Deploy Firebase Functions config with serverconfig.key';
    console.error(error);
    return err(500, { error });
  }
  if (!input.requestKey || input.requestKey.length <= 0) {
    return err(401, { error: 'Unauthorized.' });
  }
  if (input.expectedKey !== input.requestKey) {
    return err(403, { error: 'Forbidden.' });
  }
  if (input.method !== 'GET') {
    return err(405, { error: 'Method Not Allowed.' });
  }
  const config = await ServerConfigDatabase.get();
  return ok(config);
}

/**
 * Pure core for the POST server-config update endpoint.
 *
 * Behavior matches the pre-extraction order: config-check first, then
 * auth, then method, then save. Note: the pre-extraction code also
 * assembled a `data` object from { queryParams, body } — preserved
 * here so stored documents have the same shape as before.
 */
export async function handleServerConfigUpdate(input: {
  method: string;
  requestKey: string | undefined;
  expectedKey: string | null;
  query: any;
  body: any;
}): Promise<HandlerResult<unknown>> {
  if (input.expectedKey === null || input.expectedKey === undefined) {
    const error = 'Deploy Firebase Functions config with serverconfig.updatekey';
    console.error(error);
    return err(500, { error });
  }
  if (!input.requestKey || input.requestKey.length <= 0) {
    return err(401, { error: 'Unauthorized.' });
  }
  if (input.expectedKey !== input.requestKey) {
    return err(403, { error: 'Forbidden.' });
  }
  if (input.method !== 'POST') {
    return err(405, { error: 'Method Not Allowed.' });
  }
  const data = {
    queryParams: input.query,
    body: input.body,
  };
  await ServerConfigDatabase.set(data);
  const config = await ServerConfigDatabase.get();
  return ok(config);
}

export const httpServerConfig = functions.https.onRequest(async (request, response) => {
  const expectedKey = readServerConfigSecret(functions.config(), 'key');
  const result = await handleServerConfigRead({
    method: request.method,
    requestKey: request.get('X-ServerConfigKey'),
    expectedKey,
  });
  if (result.kind === 'error') {
    response.status(result.status).send(result.body);
  } else {
    response.status(200).send(result.data);
  }
});

export const httpServerConfigUpdate = functions.https.onRequest(async (request, response) => {
  const expectedKey = readServerConfigSecret(functions.config(), 'updatekey');
  try {
    const result = await handleServerConfigUpdate({
      method: request.method,
      requestKey: request.get('X-ServerConfigKey'),
      expectedKey,
      query: request.query,
      body: request.body,
    });
    if (result.kind === 'error') {
      response.status(result.status).send(result.body);
    } else {
      response.status(200).send(result.data);
    }
  } catch (error) {
    console.error(error);
    response.status(500).send({ error: 'Internal Server Error' });
  }
});
