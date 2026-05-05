/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { ButtonHealthDatabase, ButtonHealthRecord } from '../../src/database/ButtonHealthDatabase';

export class FakeButtonHealthDatabase implements ButtonHealthDatabase {
  private readonly store = new Map<string, ButtonHealthRecord>();

  /** Audit log of all save() calls. */
  readonly saved: Array<[string, ButtonHealthRecord]> = [];

  async save(buildTimestamp: string, record: ButtonHealthRecord): Promise<void> {
    this.store.set(buildTimestamp, record);
    this.saved.push([buildTimestamp, record]);
  }

  async getCurrent(buildTimestamp: string): Promise<ButtonHealthRecord | null> {
    return this.store.get(buildTimestamp) ?? null;
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(buildTimestamp: string, record: ButtonHealthRecord): void {
    this.store.set(buildTimestamp, record);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.saved.length = 0;
  }
}
