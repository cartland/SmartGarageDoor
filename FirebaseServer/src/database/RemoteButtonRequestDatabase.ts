/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { TimeSeriesDatabase } from './TimeSeriesDatabase';

// Canonical collection strings for this database. Pinned by
// test/database/RemoteButtonRequestDatabaseTest.ts. Changing them
// requires a Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'remoteButtonRequestCurrent';
export const COLLECTION_ALL = 'remoteButtonRequestAll';

export interface RemoteButtonRequestDatabase {
  save(buildTimestamp: string, data: any): Promise<void>;
  getCurrent(buildTimestamp: string): Promise<any>;
  deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number>;
}

class FirestoreRemoteButtonRequestDatabase implements RemoteButtonRequestDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  save(t: string, d: any) { return this.db.save(t, d); }
  getCurrent(t: string) { return this.db.getCurrent(t); }
  deleteAllBefore(c: number, dry: boolean) { return this.db.deleteAllBefore(c, dry); }
}

let _instance: RemoteButtonRequestDatabase = new FirestoreRemoteButtonRequestDatabase();

export const DATABASE: RemoteButtonRequestDatabase = {
  save: (t, d) => _instance.save(t, d),
  getCurrent: (t) => _instance.getCurrent(t),
  deleteAllBefore: (c, dry) => _instance.deleteAllBefore(c, dry),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: RemoteButtonRequestDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreRemoteButtonRequestDatabase(); }
