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

// Canonical collection strings + document key for this database.
// Pinned by test/database/ServerConfigDatabaseTest.ts. Changing them
// requires a Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'configCurrent';
export const COLLECTION_ALL = 'configAll';
export const CURRENT_KEY = 'current';

export interface ServerConfigDatabase {
  get(): Promise<any>;
  set(data: any): Promise<void>;
}

class FirestoreServerConfigDatabase implements ServerConfigDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  get() { return this.db.getCurrent(CURRENT_KEY); }
  set(d: any) { return this.db.save(CURRENT_KEY, d); }
}

let _instance: ServerConfigDatabase = new FirestoreServerConfigDatabase();

export const DATABASE: ServerConfigDatabase = {
  get: () => _instance.get(),
  set: (d) => _instance.set(d),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: ServerConfigDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreServerConfigDatabase(); }
