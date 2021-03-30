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

import * as firebase from 'firebase-admin';

export class TimeSeriesDatabase {

  collectionCurrent: string;
  collectionAll: string;

  static DATABASE_TIMESTAMP_KEY = 'FIRESTORE_databaseTimestamp';
  static DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

  constructor(current: string, all: string) {
    this.collectionCurrent = current;
    this.collectionAll = all;
  }

  static convertToFirestore(externalData: any): any {
    const firestoreData = {};
    Object.assign(firestoreData, externalData);
    const now = firebase.firestore.Timestamp.now();
    firestoreData[TimeSeriesDatabase.DATABASE_TIMESTAMP_KEY] = now;
    firestoreData[TimeSeriesDatabase.DATABASE_TIMESTAMP_SECONDS_KEY] = now.seconds;
    return firestoreData;
  }

  static convertFromFirestore(firestoreData: any): any {
    const result = {} as any;
    Object.assign(result, firestoreData);
    return result;
  }

  async save(session: string, data: any) {
    const firestoreData = TimeSeriesDatabase.convertToFirestore(data);
    // Set the 'current' data.
    await firebase.app().firestore().collection(this.collectionCurrent).doc(session).set(firestoreData);
    // Add historical observation to database.
    const allRes = await firebase.app().firestore()
      .collection(this.collectionAll)
      .add(firestoreData);
    console.debug('save:', this.collectionCurrent, this.collectionAll, allRes.id);
  }

  async getCurrent(session: string): Promise<any> {
    const currentRef = await firebase.app().firestore().collection(this.collectionCurrent)
      .doc(session).get();
    return TimeSeriesDatabase.convertFromFirestore(currentRef.data());
  }

  async deleteAllBefore(cutoffTimestampSeconds: number, dryRun: boolean): Promise<number> {
    const query = firebase.app().firestore().collection(this.collectionAll)
      .where(TimeSeriesDatabase.DATABASE_TIMESTAMP_SECONDS_KEY, '<', cutoffTimestampSeconds);
    let deleteCount = 0;
    if (dryRun) {
      // Do nothing.
      const snapshot = await query.get();
      deleteCount = snapshot.docs.length;
    } else {
      deleteCount = await new Promise((resolve, reject) => {
        const batchSize = 500
        const limitedQuery = query.limit(batchSize);
        deleteQueryBatch(firebase.app().firestore(), limitedQuery, 0, resolve).catch(reject);
      });
    }
    console.info('DB Delete. Dry run:', dryRun, ', collection:', this.collectionAll, ', count:', deleteCount)
    return deleteCount;
  }
}

async function deleteQueryBatch(db, query, count, resolve) {
  const snapshot = await query.get();

  const batchSize = snapshot.size;
  if (batchSize === 0) {
    // When there are no documents left, we are done
    resolve(count);
    return;
  }

  // Delete documents in a batch
  const batch = db.batch();
  snapshot.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });
  await batch.commit();

  // Recurse on the next process tick, to avoid
  // exploding the stack.
  process.nextTick(async () => {
    await deleteQueryBatch(db, query, count + batchSize, resolve);
  });
}