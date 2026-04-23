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
// test/database/NotificationsDatabaseTest.ts. Changing them requires a
// Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'notificationsCurrent';
export const COLLECTION_ALL = 'notificationsAll';

export interface NotificationsDatabase {
  save(buildTimestamp: string, data: any): Promise<void>;
  getCurrent(buildTimestamp: string): Promise<any>;
}

class FirestoreNotificationsDatabase implements NotificationsDatabase {
  private readonly db = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);
  save(b: string, d: any) { return this.db.save(b, d); }
  getCurrent(b: string) { return this.db.getCurrent(b); }
}

let _instance: NotificationsDatabase = new FirestoreNotificationsDatabase();

export const DATABASE: NotificationsDatabase = {
  save: (b, d) => _instance.save(b, d),
  getCurrent: (b) => _instance.getCurrent(b),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: NotificationsDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreNotificationsDatabase(); }
