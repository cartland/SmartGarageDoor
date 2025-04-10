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

export const pubsubDataRetentionPolicy = functions.pubsub
  .schedule('0 0 * * *').timeZone('America/Los_Angeles') // California midnight every day.
  .onRun(async (context) => {
    const config = await Config.get();
    if (!Config.isDeleteOldDataEnabled(config)) {
      console.log('Deleting data is disabled');
      return null;
    }
    const cutoffMillis = new Date().getTime() - 1000 * 60 * 60 * 24 * 14; // 2 weeks.
    const cutoffSeconds = cutoffMillis / 1000;
    const dryRunRequested = false;
    const deleteCount = await deleteOldData(cutoffSeconds, dryRunRequested);
    return null;
  });
