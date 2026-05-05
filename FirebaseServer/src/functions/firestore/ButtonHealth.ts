/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as functions from 'firebase-functions/v1';

import { handleButtonHealthFromPollWrite } from '../../controller/ButtonHealthUpdates';

/**
 * Fires asynchronously after every button-poll write. The handler
 * re-reads RemoteButtonRequestDatabase to get the freshest poll,
 * computes the new health state, persists if different from prior,
 * and sends an FCM on transition.
 *
 * MUST NOT use {failurePolicy: true}. Default no-retry mirrors
 * firestoreUpdateEvents — Cloud Functions retries with the ORIGINAL
 * event payload, which can become stale; instead the handler defends
 * itself with a fresh re-read.
 *
 * Provably cannot affect the device path:
 *  - The trigger fires AFTER the device's HTTP response is on the wire.
 *  - The trigger reads/writes a separate collection (buttonHealthCurrent).
 *  - Trigger crashes / Firestore failures / FCM failures are isolated.
 */
export const firestoreCheckButtonHealth = functions.firestore
  .document('remoteButtonRequestAll/{docId}')
  .onWrite(async (change, _context) => {
    const data = change.after.data();
    const buildTimestamp = data?.queryParams?.buildTimestamp ?? data?.buildTimestamp;
    await handleButtonHealthFromPollWrite(buildTimestamp);
    return null;
  });
