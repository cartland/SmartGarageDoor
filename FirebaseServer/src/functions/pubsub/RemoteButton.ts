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
import * as functions from 'firebase-functions/v1';

import { DATABASE as REMOTE_BUTTON_REQUEST_DATABASE } from '../../database/RemoteButtonRequestDatabase';
import { DATABASE as REMOTE_BUTTON_REQUEST_ERROR_DATABASE } from '../../database/RemoteButtonRequestErrorDatabase';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { getRemoteButtonBuildTimestamp, requireBuildTimestamp } from '../../controller/config/ConfigAccessors';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const REMOTE_BUTTON_REQUEST_ERROR_SECONDS = 60 * 10;

// History: a REMOTE_BUTTON_BUILD_TIMESTAMP_FALLBACK = 'Sat Apr 10 23:57:32 2021'
// constant lived here through server/16. Different device from the
// door sensor, so a different fallback value. Removed in A3 after
// production was verified to have body.remoteButtonBuildTimestamp
// populated (URL-encoded; the accessor decodes it to the same string).
// See docs/FIREBASE_HARDENING_PLAN.md → Part A / A3 for full history
// + revert path.

export const pubsubCheckForRemoteButtonErrors = functions.pubsub
  .schedule('every 10 minutes').timeZone('America/Los_Angeles') // California after midnight every day.
  .onRun(async (_context) => {
    const config = await ServerConfigDatabase.get();
    const buildTimestamp = requireBuildTimestamp(
      getRemoteButtonBuildTimestamp(config),
      'pubsubCheckForRemoteButtonErrors',
    );
    // NOTE: the previous `if (!buildTimestamp)` branch that wrote an
    // error entry to `remoteButtonRequestErrorAll` was removed with
    // the A3 fallback cleanup — `requireBuildTimestamp` throws on
    // missing config, which surfaces the same condition as an ERROR
    // in Cloud Logging without writing a noise entry to Firestore.
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
        error: 'Could not compute seconds since last database update',
      };
      console.error(result.error);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    if (secondsSinceDatabaseUpdate > REMOTE_BUTTON_REQUEST_ERROR_SECONDS) {
      const result = {
        error: 'Seconds since remote button status was checked is greater than expected: ' +
          secondsSinceDatabaseUpdate + ' > ' + REMOTE_BUTTON_REQUEST_ERROR_SECONDS,
      };
      console.error(result.error);
      Object.assign(result, remoteButtonRequest);
      await REMOTE_BUTTON_REQUEST_ERROR_DATABASE.save(buildTimestamp, result);
      return null;
    }
    console.log('checkForRemoteButtonErrors did not find any errors');
    return null;
  });
