/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { TimeSeriesDatabase } from './TimeSeriesDatabase';

// Canonical collection strings for this database. Pinned by
// test/database/SensorEventDatabaseTest.ts. Changing them requires a
// Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'eventsCurrent';
export const COLLECTION_ALL = 'eventsAll';

export interface SensorEventDatabase {
  save(buildTimestamp: string, data: any): Promise<void>;
  getCurrent(buildTimestamp: string): Promise<any>;
  updateCurrentWithMatchingCurrentEventTimestamp(buildTimestamp: string, matchingCurrent: any): Promise<any>;
  deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number>;
  getLatestN(n: number): Promise<any>;
  getRecentForBuildTimestamp(buildTimestamp: string, n: number): Promise<any>;
}

class FirestoreSensorEventDatabase implements SensorEventDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  save(t: string, d: any) { return this.db.save(t, d); }
  getCurrent(t: string) { return this.db.getCurrent(t); }
  updateCurrentWithMatchingCurrentEventTimestamp(t: string, m: any) {
    return this.db.updateCurrentWithMatchingCurrentEventTimestamp(t, m);
  }
  deleteAllBefore(c: number, dry: boolean) { return this.db.deleteAllBefore(c, dry); }
  getLatestN(n: number) { return this.db.getLatestN(n); }
  getRecentForBuildTimestamp(t: string, n: number) { return this.db.getRecentForBuildTimestamp(t, n); }
}

let _instance: SensorEventDatabase = new FirestoreSensorEventDatabase();

export const DATABASE: SensorEventDatabase = {
  save: (t, d) => _instance.save(t, d),
  getCurrent: (t) => _instance.getCurrent(t),
  updateCurrentWithMatchingCurrentEventTimestamp: (t, m) => _instance.updateCurrentWithMatchingCurrentEventTimestamp(t, m),
  deleteAllBefore: (c, dry) => _instance.deleteAllBefore(c, dry),
  getLatestN: (n) => _instance.getLatestN(n),
  getRecentForBuildTimestamp: (t, n) => _instance.getRecentForBuildTimestamp(t, n),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: SensorEventDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreSensorEventDatabase(); }
