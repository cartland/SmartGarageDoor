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
import { isDeleteOldDataEnabled } from '../../controller/config/ConfigAccessors';
import { deleteOldData } from '../../controller/DatabaseCleaner';

const TWO_WEEKS_SECONDS = 60 * 60 * 24 * 14;

/**
 * Pure core — testable via a fake ServerConfigDatabase + sinon stub
 * on deleteOldData. H5 of the handler testing plan.
 *
 * The 2-week window is computed from `nowMillis` (defaulting to
 * `Date.now()`) so tests can pin it without stubbing the global.
 *
 * Behavior is byte-identical to the pre-extraction inline code:
 *  - disabled → logs the existing message, does not call deleteOldData.
 *  - enabled  → calls deleteOldData(cutoffSeconds, dryRun=false) where
 *    cutoffSeconds = (nowMillis - 2w) / 1000 — same arithmetic as
 *    before, just with `nowMillis` injected for testability.
 */
export async function handleDataRetentionPolicy(
  nowMillis: number = Date.now(),
): Promise<void> {
  const config = await ServerConfigDatabase.get();
  if (!isDeleteOldDataEnabled(config)) {
    console.log('Deleting data is disabled');
    return;
  }
  const cutoffMillis = nowMillis - 1000 * TWO_WEEKS_SECONDS;
  const cutoffSeconds = cutoffMillis / 1000;
  const dryRunRequested = false;
  await deleteOldData(cutoffSeconds, dryRunRequested);
}

export const pubsubDataRetentionPolicy = functions.pubsub
  .schedule('0 0 * * *').timeZone('America/Los_Angeles') // California midnight every day.
  .onRun(async (_context) => {
    await handleDataRetentionPolicy();
    return null;
  });
