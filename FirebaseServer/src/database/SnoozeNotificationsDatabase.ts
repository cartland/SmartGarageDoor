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
// test/database/SnoozeNotificationsDatabaseTest.ts. Changing them
// requires a Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'snoozeNotificationsCurrent';
export const COLLECTION_ALL = 'snoozeNotificationsAll';

export interface SnoozeNotificationsDatabase {
  set(buildTimestamp: string, data: any): Promise<void>;
  get(buildTimestamp: string): Promise<any>;
  getRecentN(n: number): Promise<any>;
}

class FirestoreSnoozeNotificationsDatabase implements SnoozeNotificationsDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  set(b: string, d: any) { return this.db.save(b, d); }
  get(b: string) { return this.db.getCurrent(b); }
  getRecentN(n: number) { return this.db.getLatestN(n); }
}

let _instance: SnoozeNotificationsDatabase = new FirestoreSnoozeNotificationsDatabase();

export const DATABASE: SnoozeNotificationsDatabase = {
  set: (b, d) => _instance.set(b, d),
  get: (b) => _instance.get(b),
  getRecentN: (n) => _instance.getRecentN(n),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: SnoozeNotificationsDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreSnoozeNotificationsDatabase(); }
