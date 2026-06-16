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
import { isResolvedOnCloseEnabled } from '../config/ConfigAccessors';

import { SensorEvent } from '../../model/SensorEvent';
import { AndroidMessagePriority, TopicMessage, AndroidConfig } from '../../model/FCM';
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

    // 4. Build + send the data-only resolved. Send BEFORE consuming the marker
    //    (mirror R5): a failed send leaves the marker un-consumed so a duplicate
    //    Closed report can retry, rather than marking "resolved" with nothing
    //    delivered.
    const message = getResolvedMessage(buildTimestamp, openTimestampSeconds, closeTimestampSeconds);
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

/**
 * Pure helper — builds the DATA-ONLY resolved payload. No side effects.
 *
 * The client formats the human strings (duration + local start/end times) in the
 * device timezone, so the server sends only raw second timestamps. `kind`
 * discriminates the notification intent (distinct from the door-event `type`
 * key). collapse_key matches the warning's so an offline device coalesces a
 * stale warning with its resolution; HIGH priority so the data wakes the app
 * promptly (there is no `notification` block, so this never renders an OS
 * heads-up — the app owns display).
 */
export function getResolvedMessage(
  buildTimestamp: string,
  openTimestampSeconds: number,
  closeTimestampSeconds: number,
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
  message.android.priority = AndroidMessagePriority.HIGH;
  return message;
}
