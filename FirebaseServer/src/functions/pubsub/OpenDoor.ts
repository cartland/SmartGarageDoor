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
import { getBuildTimestamp, resolveBuildTimestamp } from '../../controller/config/ConfigAccessors';

// See http/OpenDoor.ts for the fallback rationale. Same door-sensor
// device.
const DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK = 'Sat Mar 13 14:45:00 2021';

export const pubsubCheckForOpenDoorsJob = functions.pubsub.schedule('every 5 minutes').onRun(async (_context) => {
  const config = await ServerConfigDatabase.get();
  const buildTimestamp = resolveBuildTimestamp(
    getBuildTimestamp(config),
    DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK,
    'pubsubCheckForOpenDoorsJob',
  );
  const eventData = await SensorEventDatabase.getCurrent(buildTimestamp);
  await sendFCMForOldData(buildTimestamp, eventData);
  return null;
});
