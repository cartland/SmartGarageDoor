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
 * Resolve a nullable config-read value to a non-null string, falling
 * back to a hardcoded literal and emitting a Cloud Logging warning if
 * the fallback is used. Callers get a guaranteed string; operators
 * get visibility when config drift happens (missing field, empty
 * value, etc.).
 *
 * Usage:
 *   const buildTimestamp = resolveBuildTimestamp(
 *     getBuildTimestamp(config),
 *     DOOR_SENSOR_BUILD_TIMESTAMP_FALLBACK,
 *     'pubsubCheckForOpenDoorsJob',
 *   );
 *
 * The `context` string identifies the call site in logs so filters
 * like `logName:"cloudfunctions" severity:"WARNING" textPayload:"buildTimestamp"`
 * can distinguish fallback hits across functions.
 */
export function resolveBuildTimestamp(
  configValue: string | null,
  fallback: string,
  context: string,
): string {
  if (configValue === null) {
    console.warn(
      `[${context}] buildTimestamp not in config; using hardcoded fallback.`,
      { fallback },
    );
    return fallback;
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
