/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
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

import { DATABASE as NotificationsDatabase } from '../../database/NotificationsDatabase';
import { DATABASE as ServerConfigDatabase } from '../../database/ServerConfigDatabase';
import { isResolvedOnCloseEnabled, isResolvedNotificationPayloadEnabled } from '../config/ConfigAccessors';

import { SensorEvent } from '../../model/SensorEvent';
import { AndroidMessagePriority, TopicMessage, AndroidConfig, Notification, AndroidNotification } from '../../model/FCM';
import { buildTimestampToFcmTopicV2 } from '../../model/FcmTopic';

/**
 * Additive "resolved-on-close" notification (docs/RESOLVED_NOTIFICATION_PLAN.md).
 *
 * When the door CLOSES after an open-door warning was actually sent this
 * episode, send a DATA-ONLY message on the v2 topic so the new app build shows
 * a "Resolved: garage door closed — it was open for X minutes" notification.
 *
 * Purely additive — never touches the legacy `door_open-` topic, the state-sync
 * (primary push-data) path, or old app builds. Gated by the live server-config
 * flag `resolvedOnCloseEnabled`; flag off = does nothing (today's behavior).
 *
 * Shape mirrors EventFCM / the *Database.ts services (interface + default impl +
 * swappable singleton + setImpl/resetImpl) so tests can capture sends with a
 * fake.
 */

const NOTIFICATION_CURRENT_EVENT_KEY = 'notificationCurrentEvent';
const NOTIFICATION_MESSAGE_KEY = 'message';
const TIMESTAMP_SECONDS_KEY = 'timestampSeconds';
// Single-use marker flag: set true once a resolved was sent for the warned
// episode so a later close (a quick un-warned open/close, or an at-least-once
// duplicate Closed report) can't fire a second / bogus resolved.
const RESOLVED_SENT_KEY = 'resolvedSent';

// Stale-marker guard. A warning requires the door to be open >15 min, and a
// real episode resolves in minutes-to-hours. A marker that survived a close
// while the flag was off could anchor a duration of days — treat anything
// beyond this as stale and skip (don't send a bogus "open for 9 days").
const STALE_MARKER_CAP_SECONDS = 7 * 24 * 60 * 60; // 7 days.

export interface ResolvedNotificationFCMService {
  /**
   * Fire the resolved notification for a door-close transition. Returns the
   * sent message, or null when nothing was sent (flag off, no warning this
   * episode, already resolved, stale marker, or send failure). Never throws for
   * an expected condition — the caller wraps it defensively regardless so the
   * primary event/state-sync path can never be broken by this feature.
   */
  sendFCMForResolvedDoor(buildTimestamp: string, closedEvent: SensorEvent): Promise<TopicMessage | null>;
}

class DefaultResolvedNotificationFCMService implements ResolvedNotificationFCMService {
  async sendFCMForResolvedDoor(buildTimestamp: string, closedEvent: SensorEvent): Promise<TopicMessage | null> {
    // 1. Flag gate (live config read). Off → do nothing new.
    const config = await ServerConfigDatabase.get();
    if (!isResolvedOnCloseEnabled(config)) {
      return null;
    }

    // 2. Did we warn this episode? The dedup marker is written only after a
    //    real warning send (which already passed the snooze + 15-min checks),
    //    so its presence (un-consumed) means "this episode was warned".
    const marker = await NotificationsDatabase.getCurrent(buildTimestamp);
    const warnedEvent = marker ? marker[NOTIFICATION_CURRENT_EVENT_KEY] : null;
    if (!warnedEvent || typeof warnedEvent[TIMESTAMP_SECONDS_KEY] !== 'number') {
      // No warning was sent for this episode → nothing to resolve.
      return null;
    }
    if (marker[RESOLVED_SENT_KEY] === true) {
      // Already resolved for this warned episode (consumed). Don't double-fire.
      return null;
    }

    // 3. Duration + stale guard. Anchor on the WARNED event's timestamp (never
    //    previousEvent, which for Open→Closing→Closed is when closing began).
    const openTimestampSeconds = warnedEvent[TIMESTAMP_SECONDS_KEY];
    const closeTimestampSeconds = closedEvent.timestampSeconds;
    const durationSeconds = closeTimestampSeconds - openTimestampSeconds;
    if (durationSeconds <= 0 || durationSeconds > STALE_MARKER_CAP_SECONDS) {
      console.warn(
        'Resolved-on-close: marker looks stale, skipping send.',
        JSON.stringify({ openTimestampSeconds, closeTimestampSeconds, durationSeconds }),
      );
      return null;
    }

    // 4. Build + send the resolved. Send BEFORE consuming the marker (mirror
    //    R5): a failed send leaves the marker un-consumed so a duplicate Closed
    //    report can retry, rather than marking "resolved" with nothing delivered.
    //    `resolvedNotificationPayloadEnabled` (live) decides data-only (default,
    //    today's shape) vs the relaxed-A combined notification+data message.
    const message = getResolvedMessage(
      buildTimestamp,
      openTimestampSeconds,
      closeTimestampSeconds,
      isResolvedNotificationPayloadEnabled(config),
    );
    console.log('Sending resolved-on-close notification', JSON.stringify(message));
    try {
      const response = await firebase.messaging().send(message);
      console.log('Successfully sent resolved message:', JSON.stringify(response));
    } catch (error) {
      console.error('Error sending resolved message:', JSON.stringify(error));
      return null; // Marker left un-consumed → a duplicate Closed can retry.
    }

    // 5. Consume the marker so a later un-warned close can't reuse it. A fresh
    //    warning (next episode) overwrites the whole marker without this flag,
    //    naturally resetting it.
    try {
      const consumed: any = {};
      consumed[NOTIFICATION_CURRENT_EVENT_KEY] = warnedEvent;
      if (marker[NOTIFICATION_MESSAGE_KEY] !== undefined) {
        consumed[NOTIFICATION_MESSAGE_KEY] = marker[NOTIFICATION_MESSAGE_KEY];
      }
      consumed[RESOLVED_SENT_KEY] = true;
      await NotificationsDatabase.save(buildTimestamp, consumed);
    } catch (error) {
      console.error(
        'Sent resolved but failed to consume the marker; a duplicate close may re-send (coalesced by collapse_key):',
        JSON.stringify(error),
      );
    }

    return message;
  }
}

let _instance: ResolvedNotificationFCMService = new DefaultResolvedNotificationFCMService();

export const SERVICE: ResolvedNotificationFCMService = {
  sendFCMForResolvedDoor: (t, e) => _instance.sendFCMForResolvedDoor(t, e),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: ResolvedNotificationFCMService): void { _instance = impl; }

/** TEST-ONLY: restore the default (Firebase-dispatching) implementation. */
export function resetImpl(): void { _instance = new DefaultResolvedNotificationFCMService(); }

// Drawer replace-key shared with the warning (OldDataFCM WARNING_REPLACE_TAG) and
// the client (DoorNotificationPresenter.TAG = "garage_door"). Only attached in the
// combined-message variant, so an OS-rendered resolved can replace the warning
// in the tray on a never-woken device. See docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md §9.
const RESOLVED_REPLACE_TAG = 'garage_door';

/** Timezone-free open duration for the OS-rendered resolved body (server cannot
 *  know the device timezone, so no wall-clock time — duration only). Mirrors
 *  OldDataFCM's minutes/hours formatting. */
function formatOpenDuration(durationSeconds: number): string {
  const minutes = Math.floor(durationSeconds / 60);
  if (minutes < 60) {
    return minutes.toString() + ' minutes';
  }
  return Math.floor(minutes / 60).toString() + ' hours';
}

/**
 * Pure helper — builds the resolved payload. No side effects.
 *
 * Two shapes, chosen by `includeNotificationPayload`:
 *
 *  - DATA-ONLY (default, today's live shape): only a `data` block + HIGH
 *    priority. The client formats the human strings (duration + local start/end
 *    times) in the device timezone, so the server sends only raw second
 *    timestamps. No `notification` block, so it never renders an OS heads-up —
 *    the app owns display.
 *  - COMBINED (relaxed-A single-card, docs/RESOLVED_NOTIFICATION_NO_COMPROMISE.md
 *    §9): the same `data` block PLUS a `notification` block sharing the warning's
 *    `garage_door` tag. An alive app still builds the rich device-local body from
 *    the data block; a never-woken app OS-renders the notification block, whose
 *    body is a timezone-free duration. HIGH delivery priority (like the data-only
 *    shape) so the message actually reaches a dozing device — that prompt delivery
 *    is what makes the never-woken replacement happen. The all-clear DOES heads-up
 *    / buzz: on Android 8+ the HIGH "Garage door" channel importance overrides any
 *    per-notification `notification_priority`, so there is no server lever to quiet
 *    it (device gate 2026-07-02 confirmed PRIORITY_LOW had no effect). The
 *    maintainer accepted the buzzing all-clear (§9.4); a silent all-clear would
 *    need a dedicated lower-importance channel, deferred.
 *
 * `kind` discriminates the notification intent (distinct from the door-event
 * `type` key). collapse_key matches the warning's so an offline device coalesces
 * a stale warning with its resolution. The `data` block is identical in both
 * shapes (pinned to wire-contracts/openDoorResolved/payload_resolved.json).
 */
export function getResolvedMessage(
  buildTimestamp: string,
  openTimestampSeconds: number,
  closeTimestampSeconds: number,
  includeNotificationPayload = false,
): TopicMessage {
  const message = <TopicMessage>{};
  message.topic = buildTimestampToFcmTopicV2(buildTimestamp);
  message.data = {
    kind: 'open_door_resolved',
    openTimestampSeconds: String(openTimestampSeconds),
    closeTimestampSeconds: String(closeTimestampSeconds),
  };
  message.android = <AndroidConfig>{};
  message.android.collapse_key = 'door_not_closed';
  // HIGH in BOTH shapes: the data-only shape wakes the app; the combined shape
  // needs prompt Doze delivery so the never-woken replacement actually arrives.
  // Lowering it does NOT quiet the all-clear (channel importance wins, gate
  // 2026-07-02) and would only risk deferring it. See the docstring + §9.4.
  message.android.priority = AndroidMessagePriority.HIGH;
  if (includeNotificationPayload) {
    message.notification = <Notification>{};
    message.notification.title = 'Resolved: garage door closed';
    message.notification.body = 'Was open for ' + formatOpenDuration(closeTimestampSeconds - openTimestampSeconds);
    message.android.notification = <AndroidNotification>{};
    message.android.notification.tag = RESOLVED_REPLACE_TAG;
  }
  return message;
}
