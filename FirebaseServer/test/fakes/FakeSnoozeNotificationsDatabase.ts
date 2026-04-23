/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { SnoozeNotificationsDatabase } from '../../src/database/SnoozeNotificationsDatabase';

export class FakeSnoozeNotificationsDatabase implements SnoozeNotificationsDatabase {
  private readonly store = new Map<string, any>();

  /**
   * Newest-first history of every set() (and seed()). getRecentN(n)
   * returns the first n entries. Mirrors TimeSeriesDatabase.getLatestN
   * which orders by FIRESTORE_databaseTimestampSeconds desc.
   */
  private readonly history: any[] = [];

  /** Audit log of all set() calls. */
  readonly saved: Array<[string, any]> = [];

  private _nextSetError: Error | null = null;
  private _nextGetError: Error | null = null;
  private _nextGetRecentNError: Error | null = null;

  async set(buildTimestamp: string, data: any): Promise<void> {
    this.saved.push([buildTimestamp, data]);
    if (this._nextSetError) {
      const err = this._nextSetError;
      this._nextSetError = null;
      throw err;
    }
    this.store.set(buildTimestamp, data);
    this.history.unshift(data);
  }

  async get(buildTimestamp: string): Promise<any> {
    if (this._nextGetError) {
      const err = this._nextGetError;
      this._nextGetError = null;
      throw err;
    }
    // Match TimeSeriesDatabase.getCurrent, which routes missing documents
    // through convertFromFirestore() → {}. Returning null would diverge.
    return this.store.get(buildTimestamp) ?? {};
  }

  async getRecentN(n: number): Promise<any> {
    if (this._nextGetRecentNError) {
      const err = this._nextGetRecentNError;
      this._nextGetRecentNError = null;
      throw err;
    }
    return this.history.slice(0, n);
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(buildTimestamp: string, data: any): void {
    this.store.set(buildTimestamp, data);
    this.history.unshift(data);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.history.length = 0;
    this.saved.length = 0;
    this._nextSetError = null;
    this._nextGetError = null;
    this._nextGetRecentNError = null;
  }

  /** Make the next set() reject with the given error. */
  failNextSet(error: Error): void { this._nextSetError = error; }

  /** Make the next get() reject with the given error. */
  failNextGet(error: Error): void { this._nextGetError = error; }

  /** Make the next getRecentN() reject with the given error. */
  failNextGetRecentN(error: Error): void { this._nextGetRecentNError = error; }
}
