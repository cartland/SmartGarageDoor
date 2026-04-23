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
  /** Audit log of every sendFCMForSensorEvent call. */
  readonly sends: Array<{ buildTimestamp: string, event: SensorEvent }> = [];

  async sendFCMForSensorEvent(buildTimestamp: string, sensorEvent: SensorEvent): Promise<TopicMessage> {
    this.sends.push({ buildTimestamp, event: sensorEvent });
    return null;
  }

  /** Test-only helper: wipe audit log. */
  clear(): void {
    this.sends.length = 0;
  }
}
