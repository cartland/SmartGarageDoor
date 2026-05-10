/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { ButtonHealthFCMService, buildTransitionPayload } from '../../src/controller/fcm/ButtonHealthFCM';
import { ButtonHealthRecord } from '../../src/database/ButtonHealthDatabase';
import { TopicMessage } from '../../src/model/FCM';

export class FakeButtonHealthFCMService implements ButtonHealthFCMService {
  /** Audit log of all sendForTransition() calls. */
  readonly sends: Array<{
    buildTimestamp: string;
    record: ButtonHealthRecord;
    lastPollAtSeconds: number | null;
  }> = [];

  async sendForTransition(
    buildTimestamp: string,
    record: ButtonHealthRecord,
    lastPollAtSeconds: number | null,
  ): Promise<TopicMessage> {
    this.sends.push({ buildTimestamp, record, lastPollAtSeconds });
    // Return the same payload the production impl would build, so callers
    // that inspect the return value see realistic data.
    return buildTransitionPayload(buildTimestamp, record, lastPollAtSeconds);
  }

  /** Test-only helper: wipe audit logs. */
  clear(): void {
    this.sends.length = 0;
  }
}
