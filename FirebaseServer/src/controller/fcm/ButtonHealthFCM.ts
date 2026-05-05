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

import { ButtonHealthRecord } from '../../database/ButtonHealthDatabase';
import { AndroidMessagePriority, TopicMessage, AndroidConfig } from '../../model/FCM';
import { buildTimestampToButtonHealthFcmTopic } from '../../model/ButtonHealthFcmTopic';

/**
 * Side-effecting FCM dispatch for button health transitions.
 *
 * Shape matches src/controller/fcm/EventFCM.ts (interface + default
 * impl + swappable singleton + setImpl/resetImpl). Tests use a fake
 * to capture calls without touching Firebase messaging.
 *
 * Data-only payloads — never a system-tray notification. UNKNOWN is
 * never sent over FCM (it's a wire/Android concern only).
 */
export interface ButtonHealthFCMService {
  sendForTransition(buildTimestamp: string, record: ButtonHealthRecord): Promise<TopicMessage>;
}

class DefaultButtonHealthFCMService implements ButtonHealthFCMService {
  async sendForTransition(buildTimestamp: string, record: ButtonHealthRecord): Promise<TopicMessage> {
    const message = buildTransitionPayload(buildTimestamp, record);
    console.log('Sending button health FCM', JSON.stringify(message));
    await firebase.messaging().send(message)
      .then((response) => {
        console.log('Successfully sent button health FCM:', JSON.stringify(response));
      })
      .catch((error) => {
        console.log('Error sending button health FCM:', JSON.stringify(error));
      });
    return message;
  }
}

let _instance: ButtonHealthFCMService = new DefaultButtonHealthFCMService();

export const SERVICE: ButtonHealthFCMService = {
  sendForTransition: (t, r) => _instance.sendForTransition(t, r),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: ButtonHealthFCMService): void { _instance = impl; }

/** TEST-ONLY: restore the default (Firebase-dispatching) implementation. */
export function resetImpl(): void { _instance = new DefaultButtonHealthFCMService(); }

/**
 * Pure helper — builds the FCM payload for a button health transition.
 * No side effects. Covered by ButtonHealthFCMTest.ts and reused by
 * DefaultButtonHealthFCMService.
 */
export function buildTransitionPayload(buildTimestamp: string, record: ButtonHealthRecord): TopicMessage {
  const message = <TopicMessage>{};
  message.topic = buildTimestampToButtonHealthFcmTopic(buildTimestamp);
  message.data = {
    buttonState: record.state,
    stateChangedAtSeconds: String(record.stateChangedAtSeconds),
    buildTimestamp: buildTimestamp,
  };
  message.android = <AndroidConfig>{};
  message.android.collapse_key = 'button_health_update';
  message.android.priority = AndroidMessagePriority.HIGH;
  // Intentionally NO `notification` block — data-only by design.
  return message;
}
