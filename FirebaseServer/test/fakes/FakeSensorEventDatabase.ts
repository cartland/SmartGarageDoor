/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import {
  SensorEventDatabase,
  GetPageOptions,
  EventPage,
  TimestampCursor,
} from '../../src/database/SensorEventDatabase';

/** A seeded event with its ordering cursor, so paging can be exercised without Firestore. */
export interface FakePageEntry {
  cursor: TimestampCursor;
  item: any;
}

function compareCursors(a: TimestampCursor, b: TimestampCursor): number {
  return a.seconds !== b.seconds ? a.seconds - b.seconds : a.nanoseconds - b.nanoseconds;
}

export class FakeSensorEventDatabase implements SensorEventDatabase {
  private readonly store = new Map<string, any>();

  /** Seeded paging entries per buildTimestamp (any order; sorted on read). */
  private readonly pageEntries = new Map<string, FakePageEntry[]>();

  /** Audit log of all getPageForBuildTimestamp() calls. */
  readonly pageCalls: GetPageOptions[] = [];

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

  /**
   * In-memory mirror of the Firestore cursor paging: window filter, startAfter,
   * limit+1 hasMore, the under-fill probe, and the newest-first reverse for the
   * 'newer' direction. Faithful enough that handler tests exercise real paging
   * behavior; returns the seeded `item` payloads (cursors kept separate).
   */
  async getPageForBuildTimestamp(opts: GetPageOptions): Promise<EventPage> {
    this.pageCalls.push(opts);
    const all = (this.pageEntries.get(opts.buildTimestamp) ?? [])
      .slice()
      .sort((x, y) => compareCursors(y.cursor, x.cursor)); // newest-first

    const windowed =
      opts.sinceSeconds !== undefined
        ? all.filter((e) => e.cursor.seconds >= opts.sinceSeconds)
        : all;
    let ordered = opts.direction === 'older' ? windowed : windowed.slice().reverse();
    if (opts.startAfter) {
      const sa = opts.startAfter;
      ordered = ordered.filter((e) =>
        opts.direction === 'older' ? compareCursors(e.cursor, sa) < 0 : compareCursors(e.cursor, sa) > 0,
      );
    }

    let hasMore = ordered.length > opts.limit;
    const pageEntries = ordered.slice(0, opts.limit);

    if (opts.direction === 'older' && !hasMore && opts.sinceSeconds !== undefined) {
      const cutoff: TimestampCursor = { seconds: opts.sinceSeconds, nanoseconds: 0 };
      const boundary = pageEntries.length > 0 ? pageEntries[pageEntries.length - 1].cursor : cutoff;
      if (all.some((e) => compareCursors(e.cursor, boundary) < 0)) {
        hasMore = true;
      }
    }

    let newest: FakePageEntry | null = null;
    let oldest: FakePageEntry | null = null;
    if (pageEntries.length > 0) {
      if (opts.direction === 'older') {
        newest = pageEntries[0];
        oldest = pageEntries[pageEntries.length - 1];
      } else {
        oldest = pageEntries[0];
        newest = pageEntries[pageEntries.length - 1];
      }
    }
    let oldestCursor = oldest ? oldest.cursor : null;
    const newestCursor = newest ? newest.cursor : null;
    if (opts.direction === 'older' && hasMore && !oldestCursor && opts.sinceSeconds !== undefined) {
      oldestCursor = { seconds: opts.sinceSeconds, nanoseconds: 0 };
    }

    const items = pageEntries.map((e) => e.item);
    const orderedItems = opts.direction === 'newer' ? items.slice().reverse() : items;
    return { items: orderedItems, hasMore, oldestCursor, newestCursor };
  }

  /** Test-only helper: seed paging entries (cursor + item) for a buildTimestamp. */
  seedPageEvents(buildTimestamp: string, entries: FakePageEntry[]): void {
    this.pageEntries.set(buildTimestamp, entries);
  }

  /** Test-only helper: pre-populate storage without recording in saved[]. */
  seed(buildTimestamp: string, data: any): void {
    this.store.set(buildTimestamp, data);
  }

  /** Test-only helper: wipe storage and audit logs. */
  clear(): void {
    this.store.clear();
    this.pageEntries.clear();
    this.pageCalls.length = 0;
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
