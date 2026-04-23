/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { EventFCMService } from '../../src/controller/fcm/EventFCM';
import { SensorEvent } from '../../src/model/SensorEvent';
import { TopicMessage } from '../../src/model/FCM';

export class FakeEventFCMService implements EventFCMService {
  /** Audit log of every sendFCMForSensorEvent call (succeeded OR threw). */
  readonly sends: Array<{ buildTimestamp: string, event: SensorEvent }> = [];

  // Failure-injection slot. Single-shot; cleared on use.
  private _nextSendError: Error | null = null;

  async sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage> {
    this.sends.push({ buildTimestamp, event: sensorEvent });
    if (this._nextSendError) {
      const e = this._nextSendError;
      this._nextSendError = null;
      throw e;
    }
    return null;
  }

  /** Test-only helper: wipe audit log and any armed failure. */
  clear(): void {
    this.sends.length = 0;
    this._nextSendError = null;
  }

  /** Test-only helper: arm the NEXT sendFCMForSensorEvent() call to reject with `error`. */
  failNextSend(error: Error): void { this._nextSendError = error; }
}
