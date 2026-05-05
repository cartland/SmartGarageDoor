/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { ButtonHealthRecord } from '../database/ButtonHealthDatabase';

export const ONLINE_THRESHOLD_SEC = 60;

/**
 * Pure function. Computes the desired state given the most recent poll
 * timestamp. Returns null when there is no poll record at all (treated
 * by callers as "device is OFFLINE since unknown time").
 *
 * Clock-skew clamp: if lastPollSec is in the future relative to nowSec,
 * treat as ONLINE (the device legitimately just polled; the skew is
 * almost certainly clock drift, not a stale poll).
 */
export function computeHealthFromLastPoll(
  lastPollSec: number | null,
  nowSec: number,
): 'ONLINE' | 'OFFLINE' {
  if (lastPollSec === null || lastPollSec === undefined) return 'OFFLINE';
  if (lastPollSec > nowSec) return 'ONLINE';
  if ((nowSec - lastPollSec) <= ONLINE_THRESHOLD_SEC) return 'ONLINE';
  return 'OFFLINE';
}

export interface TransitionResult {
  didTransition: boolean;
  newRecord: ButtonHealthRecord;
}

/**
 * Pure function. Decides whether a state transition occurred and
 * returns the record to persist. When prior state matches computed,
 * preserves prior.stateChangedAtSeconds (no-op writes MUST NOT bump
 * the timestamp — the UI's "Offline since X" label depends on it).
 *
 * Prior null (no doc yet) is treated as a transition into the
 * computed state. The persisted state is always 'ONLINE' or 'OFFLINE';
 * 'UNKNOWN' is a wire/Android concern only.
 */
export function detectTransition(
  prior: ButtonHealthRecord | null,
  computed: 'ONLINE' | 'OFFLINE',
  nowSec: number,
): TransitionResult {
  if (prior === null || prior.state !== computed) {
    return {
      didTransition: true,
      newRecord: {
        state: computed,
        stateChangedAtSeconds: nowSec,
      },
    };
  }
  // No-op: state unchanged. Return prior record verbatim — preserves
  // the original stateChangedAtSeconds.
  return {
    didTransition: false,
    newRecord: prior,
  };
}
