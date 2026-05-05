/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as firebase from 'firebase-admin';

// Canonical collection string. Pinned by
// test/database/ButtonHealthDatabaseTest.ts. Changing it requires a
// Firestore data migration in production — see
// docs/FIREBASE_DATABASE_REFACTOR.md.
export const COLLECTION_CURRENT = 'buttonHealthCurrent';

export interface ButtonHealthRecord {
  state: 'ONLINE' | 'OFFLINE';
  stateChangedAtSeconds: number;   // when this state was ENTERED (not bumped on no-op)
}

export interface ButtonHealthDatabase {
  save(buildTimestamp: string, record: ButtonHealthRecord): Promise<void>;
  getCurrent(buildTimestamp: string): Promise<ButtonHealthRecord | null>;
}

class FirestoreButtonHealthDatabase implements ButtonHealthDatabase {
  async save(buildTimestamp: string, record: ButtonHealthRecord): Promise<void> {
    await firebase.app().firestore()
      .collection(COLLECTION_CURRENT)
      .doc(buildTimestamp)
      .set(record);
  }

  async getCurrent(buildTimestamp: string): Promise<ButtonHealthRecord | null> {
    const ref = await firebase.app().firestore()
      .collection(COLLECTION_CURRENT)
      .doc(buildTimestamp)
      .get();
    const data = ref.data();
    if (!data) return null;
    return {
      state: data.state,
      stateChangedAtSeconds: data.stateChangedAtSeconds,
    };
  }
}

let _instance: ButtonHealthDatabase = new FirestoreButtonHealthDatabase();

export const DATABASE: ButtonHealthDatabase = {
  save: (t, r) => _instance.save(t, r),
  getCurrent: (t) => _instance.getCurrent(t),
};

/** TEST-ONLY: swap in a fake implementation. */
export function setImpl(impl: ButtonHealthDatabase): void { _instance = impl; }

/** TEST-ONLY: restore the Firestore implementation. */
export function resetImpl(): void { _instance = new FirestoreButtonHealthDatabase(); }
