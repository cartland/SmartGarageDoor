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

export function getRemoteButtonBuildTimestamp(config: any): string | null {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('remoteButtonBuildTimestamp')) {
    return config.body.remoteButtonBuildTimestamp;
  }
  return null;
}

/**
 * buildTimestamp of the door-sensor ESP32. Used by the pubsub jobs
 * that check for stale events and by the HTTP trigger that forces a
 * check. Call sites pair this with a fallback literal so runtime
 * behavior is preserved even when the field is missing from config.
 */
export function getDoorSensorBuildTimestamp(config: any): string | null {
  if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('doorSensorBuildTimestamp')) {
    return config.body.doorSensorBuildTimestamp;
  }
  return null;
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
