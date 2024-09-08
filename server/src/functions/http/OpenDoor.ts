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

import { sendFCMForOldData } from '../../controller/fcm/OldDataFCM';
import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

const EVENT_DATABASE = new TimeSeriesDatabase('eventsCurrent', 'eventsAll');

export const httpCheckForOpenDoors = functions.https.onRequest(async (request, response) => {
  const buildTimestamp = 'Sat Mar 13 14:45:00 2021'; // TODO: Use config.
  const eventData = await EVENT_DATABASE.getCurrent(buildTimestamp);
  const result = await sendFCMForOldData(buildTimestamp, eventData);
  response.status(200).send(result);
});
