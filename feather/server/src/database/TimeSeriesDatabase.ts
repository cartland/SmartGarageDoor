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

}