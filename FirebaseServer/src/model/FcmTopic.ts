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

function sanitizeBuildTimestamp(buildTimestamp: string): string {
  const re = /[^a-zA-Z0-9-_.~%]/g;
  return buildTimestamp.replace(re, '.');
}

export function buildTimestampToFcmTopic(buildTimestamp: string): string {
  return 'door_open-' + sanitizeBuildTimestamp(buildTimestamp);
}

/**
 * Additive "v2" door topic, introduced for the resolved-on-close notification
 * (docs/RESOLVED_NOTIFICATION_PLAN.md). Carries DATA-ONLY messages only — never
 * a notification payload — so the new app build constructs the notification
 * itself (dedicated channel, foreground display, tag-based replace). The legacy
 * `door_open-` topic is left completely untouched, so old app builds are
 * unaffected. The Android side must compute a byte-identical string; pinned by
 * FcmTopicTest on both server and client.
 */
export function buildTimestampToFcmTopicV2(buildTimestamp: string): string {
  return 'door_open_v2-' + sanitizeBuildTimestamp(buildTimestamp);
}
