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
import { deleteOldData } from '../../controller/DatabaseCleaner';

export const httpDeleteOldData = functions.https.onRequest(async (request, response) => {
  const config = await Config.get();
  if (!Config.isDeleteOldDataEnabled(config)) {
    console.log('Deleting data is disabled');
    response.status(400).send({ error: 'Disabled' });
    return;
  }
  let cutoffTimestampSeconds: number = null;
  let dryRun = false;
  if ('dryRun' in request.query) {
    dryRun = true;
  }
  if ('cutoffTimestampSeconds' in request.query) {
    const s: string = request.query['cutoffTimestampSeconds'].toString();
    cutoffTimestampSeconds = parseInt(s);
  }
  const deleteCount = await deleteOldData(cutoffTimestampSeconds, dryRun);
  const result = {
    dryRun: dryRun,
    summary: deleteCount
  };
  await response.status(200).send(result);
});
