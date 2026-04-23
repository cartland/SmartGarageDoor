/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { NotificationsDatabase } from '../../src/database/NotificationsDatabase';

export class FakeNotificationsDatabase implements NotificationsDatabase {
  private readonly store = new Map<string, any>();

  /** Audit log of all save() calls. */
  readonly saved: Array<[string, any]> = [];

  private _nextSaveError: Error | null = null;
  private _nextGetCurrentError: Error | null = null;

  async save(buildTimestamp: string, data: any): Promise<void> {
    this.saved.push([buildTimestamp, data]);
    if (this._nextSaveError) {
      const err = this._nextSaveError;
      this._nextSaveError = null;
      throw err;
    }
    this.store.set(buildTimestamp, data);
  }

  async getCurrent(buildTimestamp: string): Promise<any> {
    if (this._nextGetCurrentError) {
      const err = this._nextGetCurrentError;
      this._nextGetCurrentError = null;
      throw err;
    }
    // Match TimeSeriesDatabase.getCurrent, which routes missing documents
    // through convertFromFirestore() → {}. Returning null would diverge.
    return this.store.get(buildTimestamp) ?? {};
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(buildTimestamp: string, data: any): void {
    this.store.set(buildTimestamp, data);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.saved.length = 0;
    this._nextSaveError = null;
    this._nextGetCurrentError = null;
  }

  /** Make the next save() reject with the given error. */
  failNextSave(error: Error): void { this._nextSaveError = error; }

  /** Make the next getCurrent() reject with the given error. */
  failNextGetCurrent(error: Error): void { this._nextGetCurrentError = error; }
}
