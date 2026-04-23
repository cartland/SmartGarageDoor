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

  /** Audit log of all save() calls, even ones that throw. */
  readonly saved: Array<[string, any]> = [];

  /** Audit log of all updateCurrentWithMatchingCurrentEventTimestamp() calls, even ones that throw. */
  readonly updates: Array<[string, any]> = [];

  /** Audit log of all deleteAllBefore() calls. */
  readonly deleteCalls: Array<{ cutoff: number, dryRun: boolean }> = [];

  // Failure-injection slots. Each `failNextX(error)` arms the NEXT call to X
  // to reject with `error`. Single-shot; cleared on use. Tests that need
  // multi-call failure patterns should call failNextX again in the handler.
  private _nextSaveError: Error | null = null;
  private _nextGetCurrentError: Error | null = null;
  private _nextUpdateError: Error | null = null;
  private _nextDeleteError: Error | null = null;

  async save(buildTimestamp: string, data: any): Promise<void> {
    this.saved.push([buildTimestamp, data]);
    if (this._nextSaveError) {
      const e = this._nextSaveError;
      this._nextSaveError = null;
      throw e;
    }
    this.store.set(buildTimestamp, data);
  }

  async getCurrent(buildTimestamp: string): Promise<any> {
    if (this._nextGetCurrentError) {
      const e = this._nextGetCurrentError;
      this._nextGetCurrentError = null;
      throw e;
    }
    // Match TimeSeriesDatabase.getCurrent, which routes through
    // convertFromFirestore() and always returns {} for missing documents.
    // Returning null here would diverge from production and break callers
    // that use `KEY in oldData` guards without null-checks.
    return this.store.get(buildTimestamp) ?? {};
  }

  async updateCurrentWithMatchingCurrentEventTimestamp(buildTimestamp: string, matchingCurrent: any): Promise<any> {
    this.updates.push([buildTimestamp, matchingCurrent]);
    if (this._nextUpdateError) {
      const e = this._nextUpdateError;
      this._nextUpdateError = null;
      throw e;
    }
    this.store.set(buildTimestamp, matchingCurrent);
    return null;
  }

  async deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number> {
    this.deleteCalls.push({ cutoff: cutoffTimestampSeconds, dryRun });
    if (this._nextDeleteError) {
      const e = this._nextDeleteError;
      this._nextDeleteError = null;
      throw e;
    }
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
    this._nextSaveError = null;
    this._nextGetCurrentError = null;
    this._nextUpdateError = null;
    this._nextDeleteError = null;
  }

  /** Test-only helper: arm the NEXT save() call to reject with `error`. */
  failNextSave(error: Error): void { this._nextSaveError = error; }

  /** Test-only helper: arm the NEXT getCurrent() call to reject with `error`. */
  failNextGetCurrent(error: Error): void { this._nextGetCurrentError = error; }

  /** Test-only helper: arm the NEXT updateCurrent...() call to reject with `error`. */
  failNextUpdate(error: Error): void { this._nextUpdateError = error; }

  /** Test-only helper: arm the NEXT deleteAllBefore() call to reject with `error`. */
  failNextDelete(error: Error): void { this._nextDeleteError = error; }
}
