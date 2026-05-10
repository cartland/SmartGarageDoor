/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as firebase from 'firebase-admin';
import * as functions from 'firebase-functions/v1';

import { DATABASE as BUTTON_HEALTH_DATABASE } from '../../database/ButtonHealthDatabase';
import { DATABASE as REMOTE_BUTTON_REQUEST_DATABASE } from '../../database/RemoteButtonRequestDatabase';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { computeHealthFromLastPoll, detectTransition } from '../../controller/ButtonHealthInterpreter';
import { SERVICE as ButtonHealthFCMService } from '../../controller/fcm/ButtonHealthFCM';
import { isRemoteButtonEnabled, getRemoteButtonBuildTimestamp, requireBuildTimestamp } from '../../controller/config/ConfigAccessors';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

/**
 * Pure handler core for the every-minute sweep.
 *
 * Reads the latest poll record + the prior persisted health state,
 * computes the new state via the same pure function the trigger uses,
 * and persists + sends FCM only on transitions. No-op writes (state
 * unchanged) preserve stateChangedAtSeconds.
 *
 * Drives the OFFLINE-side detection (the trigger handles ONLINE
 * recovery sub-second on every poll).
 *
 * Kill switch: short-circuits when isRemoteButtonEnabled(config) ==
 * false.
 */
export async function handleCheckButtonHealth(): Promise<void> {
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    console.log('handleCheckButtonHealth: button feature disabled; no-op');
    return;
  }
  const buildTimestamp = requireBuildTimestamp(
    getRemoteButtonBuildTimestamp(config),
    'pubsubCheckButtonHealth',
  );
  const latestRequest = await REMOTE_BUTTON_REQUEST_DATABASE.getCurrent(buildTimestamp);
  const lastPollSeconds: number | null = latestRequest?.[DATABASE_TIMESTAMP_SECONDS_KEY] ?? null;
  const nowSeconds = firebase.firestore.Timestamp.now().seconds;
  const computed = computeHealthFromLastPoll(lastPollSeconds, nowSeconds);
  const prior = await BUTTON_HEALTH_DATABASE.getCurrent(buildTimestamp);
  const { didTransition, newRecord } = detectTransition(prior, computed, nowSeconds);
  if (didTransition) {
    await BUTTON_HEALTH_DATABASE.save(buildTimestamp, newRecord);
    await ButtonHealthFCMService.sendForTransition(buildTimestamp, newRecord, lastPollSeconds);
  }
}

// Schedule cadence is the OFFLINE-detection ceiling. Tightened from
// 10 min to 1 min on 2026-05-10 to bound worst-case OFFLINE-detection
// latency to ~1 min. Cost: ~1.4K invocations/day (well under free
// tier). Cannot affect device path. See BUTTON_HEALTH_ARCHITECTURE.md.
export const pubsubCheckButtonHealth = functions.pubsub
  .schedule('every 1 minutes').timeZone('America/Los_Angeles')
  .onRun(async (_context) => {
    await handleCheckButtonHealth();
    return null;
  });
