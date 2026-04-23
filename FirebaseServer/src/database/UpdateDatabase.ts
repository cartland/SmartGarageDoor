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
// test/database/UpdateDatabaseTest.ts. Changing them requires a
// Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'updateCurrent';
export const COLLECTION_ALL = 'updateAll';

export interface UpdateDatabase {
  save(session: string, data: any): Promise<void>;
  getCurrent(session: string): Promise<any>;
  deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number>;
}

class FirestoreUpdateDatabase implements UpdateDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  save(s: string, d: any) { return this.db.save(s, d); }
  getCurrent(s: string) { return this.db.getCurrent(s); }
  deleteAllBefore(c: number, dry: boolean) { return this.db.deleteAllBefore(c, dry); }
}

let _instance: UpdateDatabase = new FirestoreUpdateDatabase();

export const DATABASE: UpdateDatabase = {
  save: (s, d) => _instance.save(s, d),
  getCurrent: (s) => _instance.getCurrent(s),
  deleteAllBefore: (c, dry) => _instance.deleteAllBefore(c, dry),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: UpdateDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreUpdateDatabase(); }
