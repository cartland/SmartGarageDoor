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

/**
 * Pure accessors over a server-config payload fetched from
 * `ServerConfigDatabase.get()`. These don't touch Firestore — they're
 * stateless reads over the config object's `body` shape. Kept out of
 * the DB interface so the DB module stays narrowly focused on get/set.
 */

export function getRemoteButtonPushKey(config: any): string | null {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('remoteButtonPushKey')) {
    return config.body.remoteButtonPushKey;
  }
  return null;
}

export function getRemoteButtonAuthorizedEmails(config: any): string[] | null {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('remoteButtonAuthorizedEmails')) {
    return config.body.remoteButtonAuthorizedEmails;
  }
  return null;
}

export function isRemoteButtonEnabled(config: any): boolean {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('remoteButtonEnabled')) {
    return config.body.remoteButtonEnabled;
  }
  return false;
}

/**
 * buildTimestamp of the door-sensor ESP32. Reads the production key
 * `body.buildTimestamp` (no prefix — the key has been there since the
 * first serverConfig.json in April 2021). The value is stored plain/
 * decoded, so no transform is applied. Empty strings are treated as
 * missing (returns null) so caller `?? fallback` chains behave
 * correctly.
 */
export function getBuildTimestamp(config: any): string | null {
  if (!config || !config.body) return null;
  const value = config.body.buildTimestamp;
  if (typeof value !== 'string' || value.length === 0) return null;
  return value;
}

/**
 * buildTimestamp of the remote-button device. Reads
 * `body.remoteButtonBuildTimestamp`, which has been stored URL-encoded
 * in production config since April 2021. This accessor normalizes by
 * calling `decodeURIComponent()` so callers always see the decoded
 * form — matching the pre-refactor hardcoded literal used in
 * `pubsub/RemoteButton.ts`.
 *
 * Defensive behavior:
 *  - Empty string value → null (callers' `?? fallback` triggers).
 *  - Already-decoded value → unchanged (decode is idempotent for
 *    strings without `%`).
 *  - Malformed percent-encoding → returns raw value, never crashes.
 */
export function getRemoteButtonBuildTimestamp(config: any): string | null {
  if (!config || !config.body) return null;
  const raw = config.body.remoteButtonBuildTimestamp;
  if (typeof raw !== 'string' || raw.length === 0) return null;
  try {
    return decodeURIComponent(raw);
  } catch (err) {
    console.warn(
      'ConfigAccessors: failed to URL-decode remoteButtonBuildTimestamp, returning raw value.',
      { raw, err: err instanceof Error ? err.message : String(err) },
    );
    return raw;
  }
}

/**
 * Require a non-null buildTimestamp from config. Throws if the value
 * is missing/empty. This replaces the fallback pattern used in
 * server/16 — production config is now the authoritative source of
 * the device ID. See docs/archive/FIREBASE_HARDENING_PLAN.md → Part A / A3
 * for the history and revert path.
 *
 * Why a throw rather than a silent fallback: after A2 verified
 * production config has both `body.buildTimestamp` and
 * `body.remoteButtonBuildTimestamp` populated and the warn-level
 * fallback logs from server/16 confirmed the fallback never fired
 * in 24+ hours, the fallback's only effect was to mask a future
 * config-deletion bug. Throwing surfaces that bug in Cloud Logging
 * as an ERROR, which pages or alerts — fast feedback beats silent
 * continuation with a stale hardcoded value.
 *
 * Callers: use try/catch (HTTP handlers that return 500) or let the
 * throw propagate (pubsub jobs — Firebase marks the run failed, the
 * next scheduled tick retries).
 *
 * Usage:
 *   const config = await ServerConfigDatabase.get();
 *   const buildTimestamp = requireBuildTimestamp(
 *     getBuildTimestamp(config),
 *     'pubsubCheckForOpenDoorsJob',
 *   );
 */
export function requireBuildTimestamp(
  configValue: string | null,
  context: string,
): string {
  if (configValue === null) {
    const msg = `[${context}] buildTimestamp missing from config — cannot proceed. See docs/archive/FIREBASE_HARDENING_PLAN.md Part A / A3.`;
    console.error(msg);
    throw new Error(msg);
  }
  return configValue;
}

export function isDeleteOldDataEnabled(config: any): boolean {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('deleteOldDataEnabled')) {
    return config.body.deleteOldDataEnabled;
  }
  return false;
}

export function isDeleteOldDataEnabledDryRun(config: any): boolean {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('deleteOldDataEnabledDryRun')) {
    return config.body.deleteOldDataEnabledDryRun;
  }
  return false;
}

export function isSnoozeNotificationsEnabled(config: any): boolean {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('snoozeNotificationsEnabled')) {
    return config.body.snoozeNotificationsEnabled;
  }
  return false;
}
