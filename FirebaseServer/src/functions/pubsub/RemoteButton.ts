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

import * as firebase from 'firebase-admin';
import * as functions from 'firebase-functions';

import { TimeSeriesDatabase } from '../../database/TimeSeriesDatabase';

const REMOTE_BUTTON_REQUEST_DATABASE = new TimeSeriesDatabase('remoteButtonRequestCurrent', 'remoteButtonRequestAll');

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const REMOTE_BUTTON_REQUEST_ERROR_SECONDS = 60 * 10;
const REMOTE_BUTTON_REQUEST_ERROR_DATABASE = new TimeSeriesDatabase('remoteButtonRequestErrorCurrent', 'remoteButtonRequestErrorAll');

export const pubsubCheckForRemoteButtonErrors = functions.pubsub
  .schedule('every 10 minutes').timeZone('America/Los_Angeles') // California after midnight every day.
  .onRun(async (context) => {
    // TODO: Use config.
    // const config = await Config.get();
    // const buildTimestamp = Config.getRemoteButtonBuildTimestamp(config);
    const buildTimestamp = 'Sat Apr 10 23:57:32 2021';
    if (!buildTimestamp) {
      const result = { error: 'No remote button build timestamp in config: ' + buildTimestamp };
      console.error(result.error);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    const remoteButtonRequest = await REMOTE_BUTTON_REQUEST_DATABASE.getCurrent(buildTimestamp);
    if (!remoteButtonRequest) {
      const result = { error: 'No remote button requests found for build timestamp: ' + buildTimestamp };
      console.error(result.error);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    const secondsSinceDatabaseUpdate =
      firebase.firestore.Timestamp.now().seconds - remoteButtonRequest[DATABASE_TIMESTAMP_SECONDS_KEY];
    if (isNaN(secondsSinceDatabaseUpdate)) {
      const result = {
        error: 'Could not compute seconds since last database update'
      };
      console.error(result.error);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    if (secondsSinceDatabaseUpdate > REMOTE_BUTTON_REQUEST_ERROR_SECONDS) {
      const result = {
        error: 'Seconds since remote button status was checked is greater than expected: ' +
          secondsSinceDatabaseUpdate + ' > ' + REMOTE_BUTTON_REQUEST_ERROR_SECONDS
      };
      console.error(result.error);
      Object.assign(result, remoteButtonRequest);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    console.log('checkForRemoteButtonErrors did not find any errors');
    return null;
  });
