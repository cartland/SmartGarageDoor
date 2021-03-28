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

import * as functions from 'firebase-functions';

import { Config } from '../../database/ServerConfigDatabase';

/**
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/remoteButton?buildTimestamp=buildTimestamp&buttonAckToken=buttonAckToken
 */
export const serverConfig = functions.https.onRequest(async (request, response) => {
  const data = {
    queryParams: request.query,
    body: request.body
  };
  if (request.method === 'GET') {
    const config = await Config.get();
    response.status(200).send(config);
    return;
  }
  if (request.method !== 'POST') {
    response.status(400).send({ error: 'Invalid request.' });
    return;
  }
  const functionConfig = functions.config();
  if (!functionConfig
    || !('serverconfig' in functionConfig)
    || !('key' in functionConfig['serverconfig'])) {
    const error = 'Deploy Firebase Functions config with serverconfig.key';
    console.error(error);
    response.status(500).send({ error: error });
    return;
  }
  const configSecretKey = functionConfig['serverconfig']['key'];
  const requestConfigKey = request.get('X-ServerConfigKey');
  if (!requestConfigKey || requestConfigKey.length <= 0) {
    response.status(401).send({ error: 'Unauthorized.' });
    return;
  }
  if (configSecretKey !== requestConfigKey) {
    response.status(403).send({ error: 'Forbidden.' });
    return;
  }
  try {
    await Config.set(data);
    const config = await Config.get();
    response.status(200).send(config);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
