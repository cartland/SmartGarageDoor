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

export const httpServerConfig = functions.https.onRequest(async (request, response) => {
  const data = {
    queryParams: request.query,
    body: request.body
  };
  const functionConfig = functions.config();
  if (!functionConfig
    || !('serverconfig' in functionConfig)
    || !('key' in functionConfig['serverconfig'])) {
    const error = 'Deploy Firebase Functions config with serverconfig.key';
    console.error(error);
    response.status(500).send({ error: error });
    return;
  }
  // Set the config key when you deploy the Firebase project.
  // $ export SERVER_CONFIG_KEY="YourKeyHere"
  // $ firebase functions:config:set serverconfig.key="$SERVER_CONFIG_KEY"
  // $ firebase functions:config:get
  // $ firebase deploy
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
  if (request.method !== 'GET') {
    response.status(405).send({ error: 'Method Not Allowed.' });
    return;
  }
  // Fully authorized.
  const config = await Config.get();
  response.status(200).send(config);
  return;
});

export const httpServerConfigUpdate = functions.https.onRequest(async (request, response) => {
  const data = {
    queryParams: request.query,
    body: request.body
  };
  const functionConfig = functions.config();
  if (!functionConfig
    || !('serverconfig' in functionConfig)
    || !('updatekey' in functionConfig['serverconfig'])) {
    const error = 'Deploy Firebase Functions config with serverconfig.updatekey';
    console.error(error);
    response.status(500).send({ error: error });
    return;
  }
  // Set the config key when you deploy the Firebase project.
  // $ export SERVER_CONFIG_UPDATE_KEY="YourKeyHere"
  // $ firebase functions:config:set serverconfig.updatekey="$SERVER_CONFIG_UPDATE_KEY"
  // $ firebase functions:config:get
  // $ firebase deploy
  const configSecretKey = functionConfig['serverconfig']['updatekey'];
  const requestConfigKey = request.get('X-ServerConfigKey');
  if (!requestConfigKey || requestConfigKey.length <= 0) {
    response.status(401).send({ error: 'Unauthorized.' });
    return;
  }
  if (configSecretKey !== requestConfigKey) {
    response.status(403).send({ error: 'Forbidden.' });
    return;
  }
  // Fully authorized.
  if (request.method !== 'POST') {
    response.status(405).send({ error: 'Method Not Allowed.' });
    return;
  }
  try {
    await Config.set(data);
    const config = await Config.get();
    response.status(200).send(config);
    return;
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
    return;
  }
});
