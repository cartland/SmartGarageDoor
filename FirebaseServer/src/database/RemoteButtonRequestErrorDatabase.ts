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
// test/database/RemoteButtonRequestErrorDatabaseTest.ts. Changing them
// requires a Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
//
// This database stores health-monitoring entries written by the
// pubsub cron (`pubsubCheckForRemoteButtonErrors`) when it detects
// that the request pipeline has gone stale (device offline, missing
// config, etc.). It is NOT a per-request error log for the HTTP
// handler — those failures are logged to console and returned to the
// client.
export const COLLECTION_CURRENT = 'remoteButtonRequestErrorCurrent';
export const COLLECTION_ALL = 'remoteButtonRequestErrorAll';

export interface RemoteButtonRequestErrorDatabase {
  save(buildTimestamp: string, data: any): Promise<void>;
}

class FirestoreRemoteButtonRequestErrorDatabase implements RemoteButtonRequestErrorDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  save(t: string, d: any) { return this.db.save(t, d); }
}

let _instance: RemoteButtonRequestErrorDatabase = new FirestoreRemoteButtonRequestErrorDatabase();

export const DATABASE: RemoteButtonRequestErrorDatabase = {
  save: (t, d) => _instance.save(t, d),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: RemoteButtonRequestErrorDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreRemoteButtonRequestErrorDatabase(); }
