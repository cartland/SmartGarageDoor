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

import * as Database from '../../database/Database';

/**
 * Get the current air quality observations.
 *
 * curl -H "Content-Type: application/json" http://localhost:5000/escape-echo/us-central1/echo?key1=value1&key2=value2 --data '{"key3":"value3","key4":"value4"}'
 */
export const echo = functions.https.onRequest(async (request, response) => {
  // Echo query parameters and body.
  const data = {
    queryParams: request.query,
    body: request.body
  };
  try {
    await Database.save(data);
    const retrievedData = await Database.getCurrent();
    // RESPOND with formatted data.
    response.status(200).send(retrievedData);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
