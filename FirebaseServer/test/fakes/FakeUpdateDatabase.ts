/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { UpdateDatabase } from '../../src/database/UpdateDatabase';

export class FakeUpdateDatabase implements UpdateDatabase {
  private readonly store = new Map<string, any>();

  /** Audit log of all save() calls. */
  readonly saved: Array<[string, any]> = [];

  /** Audit log of all deleteAllBefore() calls. */
  readonly deleteCalls: Array<{ cutoff: number, dryRun: boolean }> = [];

  async save(session: string, data: any): Promise<void> {
    this.store.set(session, data);
    this.saved.push([session, data]);
  }

  async getCurrent(session: string): Promise<any> {
    // Match TimeSeriesDatabase.getCurrent, which routes missing documents
    // through convertFromFirestore() → {}. Returning null would diverge.
    return this.store.get(session) ?? {};
  }

  async deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number> {
    this.deleteCalls.push({ cutoff: cutoffTimestampSeconds, dryRun });
    return 0;
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(session: string, data: any): void {
    this.store.set(session, data);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.saved.length = 0;
    this.deleteCalls.length = 0;
  }
}
