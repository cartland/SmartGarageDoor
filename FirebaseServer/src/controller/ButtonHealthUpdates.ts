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

import { DATABASE as BUTTON_HEALTH_DATABASE } from '../database/ButtonHealthDatabase';
import { DATABASE as REMOTE_BUTTON_REQUEST_DATABASE } from '../database/RemoteButtonRequestDatabase';
import { DATABASE as ServerConfigDatabase } from '../database/ServerConfigDatabase';
import { computeHealthFromLastPoll, detectTransition } from './ButtonHealthInterpreter';
import { SERVICE as ButtonHealthFCMService } from './fcm/ButtonHealthFCM';
import { isRemoteButtonEnabled, getRemoteButtonBuildTimestamp, requireBuildTimestamp } from './config/ConfigAccessors';

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

/**
 * Pure handler core called by the Firestore trigger on every poll write.
 *
 * Critical: re-reads `RemoteButtonRequestDatabase.getCurrent()` rather
 * than trusting the trigger's event payload. Cloud Functions retries
 * triggers with the ORIGINAL event payload, which can become stale
 * by the time the retry runs. The fresh re-read prevents a stale-event
 * retry from incorrectly computing OFFLINE.
 *
 * Kill switch: short-circuits when `isRemoteButtonEnabled(config) ==
 * false`. Reuses the existing button-feature kill switch — one toggle
 * disables everything button-related.
 *
 * @param buildTimestamp Identifies which device's poll arrived. Stable
 * across retries (it's the device identity, not a state value).
 */
export async function handleButtonHealthFromPollWrite(buildTimestamp: string): Promise<void> {
  if (!buildTimestamp) {
    console.warn('handleButtonHealthFromPollWrite: missing buildTimestamp; no-op');
    return;
  }
  const config = await ServerConfigDatabase.get();
  if (!isRemoteButtonEnabled(config)) {
    console.log('handleButtonHealthFromPollWrite: button feature disabled; no-op');
    return;
  }
  const expectedBuildTimestamp = requireBuildTimestamp(
    getRemoteButtonBuildTimestamp(config),
    'firestoreCheckButtonHealth',
  );
  if (buildTimestamp !== expectedBuildTimestamp) {
    // Trigger fired for a doc whose buildTimestamp doesn't match server config.
    // Could happen during a device-rotation transition; safer to no-op than
    // to flap state for the OLD device.
    console.log(
      'handleButtonHealthFromPollWrite: buildTimestamp does not match config',
      { eventBuildTimestamp: buildTimestamp, configBuildTimestamp: expectedBuildTimestamp },
    );
    return;
  }
  // Re-read for the freshest poll (defends against retry with stale event).
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
