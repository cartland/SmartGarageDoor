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

import { updateEvent } from '../../controller/EventUpdates';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { getBuildTimestamp, requireBuildTimestamp } from '../../controller/config/ConfigAccessors';

// History: see http/OpenDoor.ts for the fallback removal rationale.
// Same door-sensor device; same A3 story.

export const pubsubCheckForDoorErrors = functions.pubsub.schedule('every 1 minutes').onRun(async (_context) => {
  const BUILD_TIMESTAMP_PARAM_KEY = "buildTimestamp";
  const config = await ServerConfigDatabase.get();
  const buildTimestampString = requireBuildTimestamp(
    getBuildTimestamp(config),
    'pubsubCheckForDoorErrors',
  );
  const scheduledJob = true;
  const data = {};
  data[BUILD_TIMESTAMP_PARAM_KEY] = buildTimestampString;
  await updateEvent(data, scheduledJob);
  return null;
});
