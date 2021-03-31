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

import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

const UPDATE_DATABASE = new TimeSeriesDatabase('updateCurrent', 'updateAll');
const EVENT_DATABASE = new TimeSeriesDatabase('eventsCurrent', 'eventsAll');
const REMOTE_REQUEST_DATABASE = new TimeSeriesDatabase('remoteButtonRequestCurrent', 'remoteButtonRequestAll');
const REMOTE_COMMAND_DATABASE = new TimeSeriesDatabase('remoteButtonCommandCurrent', 'remoteButtonCommandAll');

export const dataRetentionPolicy = functions.pubsub
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

export const deleteData = functions.https.onRequest(async (request, response) => {
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

async function deleteOldData(cutoffTimestampSeconds: number, dryRunRequested: boolean): Promise<object> {
  const config = await Config.get();
  if (!Config.isDeleteOldDataEnabled(config)) {
    console.log('deleteOldData: Deleting data is disabled');
    return {};
  }
  if (dryRunRequested) {
    console.log('deleteOldData: Dry run requested');
  }
  const dryRunConfig = Config.isDeleteOldDataEnabledDryRun(config);
  if (dryRunConfig) {
    console.log('deleteOldData: Dry run is configured');
  }
  const dryRun = dryRunRequested || dryRunConfig;
  if (dryRun) {
    console.log('deleteOldData: Doing dry run');
  } else {
    console.log('deleteOldData: Deleting data!');
  }
  const updateCount = await UPDATE_DATABASE.deleteAllBefore(cutoffTimestampSeconds, dryRun);
  const eventCount = await EVENT_DATABASE.deleteAllBefore(cutoffTimestampSeconds, dryRun);
  const requestCount = await REMOTE_REQUEST_DATABASE.deleteAllBefore(cutoffTimestampSeconds, dryRun);
  const commandCount = await REMOTE_COMMAND_DATABASE.deleteAllBefore(cutoffTimestampSeconds, dryRun);
  const summary = {
    updatesDeleted: updateCount,
    eventsDeleted: eventCount,
    requestsDeleted: requestCount,
    commandsDeleted: commandCount
  };
  return summary
}
