/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { SensorEventDatabase } from '../../src/database/SensorEventDatabase';

export class FakeSensorEventDatabase implements SensorEventDatabase {
  private readonly store = new Map<string, any>();

  /** Audit log of all save() calls. */
  readonly saved: Array<[string, any]> = [];

  /** Audit log of all updateCurrentWithMatchingCurrentEventTimestamp() calls. */
  readonly updates: Array<[string, any]> = [];

  /** Audit log of all deleteAllBefore() calls. */
  readonly deleteCalls: Array<{ cutoff: number, dryRun: boolean }> = [];

  async save(buildTimestamp: string, data: any): Promise<void> {
    this.store.set(buildTimestamp, data);
    this.saved.push([buildTimestamp, data]);
  }

  async getCurrent(buildTimestamp: string): Promise<any> {
    // Match TimeSeriesDatabase.getCurrent, which routes through
    // convertFromFirestore() and always returns {} for missing documents.
    // Returning null here would diverge from production and break callers
    // that use `KEY in oldData` guards without null-checks.
    return this.store.get(buildTimestamp) ?? {};
  }

  async updateCurrentWithMatchingCurrentEventTimestamp(buildTimestamp: string, matchingCurrent: any): Promise<any> {
    this.updates.push([buildTimestamp, matchingCurrent]);
    this.store.set(buildTimestamp, matchingCurrent);
    return null;
  }

  async deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number> {
    this.deleteCalls.push({ cutoff: cutoffTimestampSeconds, dryRun });
    return 0;
  }

  async getLatestN(_n: number): Promise<any> {
    // Tests that need richer history semantics should override this.
    return [];
  }

  async getRecentForBuildTimestamp(_buildTimestamp: string, _n: number): Promise<any> {
    return [];
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(buildTimestamp: string, data: any): void {
    this.store.set(buildTimestamp, data);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.saved.length = 0;
    this.updates.length = 0;
    this.deleteCalls.length = 0;
  }
}
