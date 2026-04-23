/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { ServerConfigDatabase } from '../../src/database/ServerConfigDatabase';

export class FakeServerConfigDatabase implements ServerConfigDatabase {
  private current: any = null;

  /** Audit log of all set() calls. */
  readonly saved: any[] = [];

  private _nextSetError: Error | null = null;
  private _nextGetError: Error | null = null;

  async set(data: any): Promise<void> {
    this.saved.push(data);
    if (this._nextSetError) {
      const err = this._nextSetError;
      this._nextSetError = null;
      throw err;
    }
    this.current = data;
  }

  async get(): Promise<any> {
    if (this._nextGetError) {
      const err = this._nextGetError;
      this._nextGetError = null;
      throw err;
    }
    // Match TimeSeriesDatabase.getCurrent, which routes missing documents
    // through convertFromFirestore() → {}. Returning null would diverge.
    return this.current ?? {};
  }

  /** Test-only helper: pre-populate without recording in saved[]. */
  seed(data: any): void {
    this.current = data;
  }

  /** Test-only helper: wipe state and audit logs. */
  clear(): void {
    this.current = null;
    this.saved.length = 0;
    this._nextSetError = null;
    this._nextGetError = null;
  }

  /** Make the next set() reject with the given error. */
  failNextSet(error: Error): void { this._nextSetError = error; }

  /** Make the next get() reject with the given error. */
  failNextGet(error: Error): void { this._nextGetError = error; }
}
