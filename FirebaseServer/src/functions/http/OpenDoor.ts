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

import { sendFCMForOldData } from '../../controller/fcm/OldDataFCM';
import { DATABASE as SensorEventDatabase } from '../../database/SensorEventDatabase';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { getBuildTimestamp, requireBuildTimestamp } from '../../controller/config/ConfigAccessors';

// History: a DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021'
// constant lived here through server/16. Removed in A3 after
// production was verified to have body.buildTimestamp populated with
// that exact value, and server/16's warn-level fallback logs stayed
// empty for 24+ hours. See docs/FIREBASE_HARDENING_PLAN.md → Part A / A3
// for the full rationale + revert path.

/**
 * Pure core — testable via fakes on ServerConfigDatabase and
 * SensorEventDatabase. Extracted in H2 of the handler testing plan.
 *
 * Reads buildTimestamp from config (throws if missing), fetches the
 * current sensor event for that device, and delegates to
 * sendFCMForOldData. Returns whatever sendFCMForOldData returns —
 * which the HTTP wrapper passes straight through as the 200 body.
 */
export async function handleCheckForOpenDoorsRequest(): Promise<unknown> {
  const config = await ServerConfigDatabase.get();
  const buildTimestamp = requireBuildTimestamp(
    getBuildTimestamp(config),
    'httpCheckForOpenDoors',
  );
  const eventData = await SensorEventDatabase.getCurrent(buildTimestamp);
  return sendFCMForOldData(buildTimestamp, eventData);
}

export const httpCheckForOpenDoors = functions.https.onRequest(async (_request, response) => {
  try {
    const result = await handleCheckForOpenDoorsRequest();
    response.status(200).send(result);
  } catch (error) {
    console.error('httpCheckForOpenDoors failed', error);
    response.status(500).send({ error: error instanceof Error ? error.message : String(error) });
  }
});
