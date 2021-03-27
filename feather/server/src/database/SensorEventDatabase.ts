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

import { SensorEvent } from '../model/SensorEvent';

const EVENTS_ALL = 'eventsAll';
const EVENTS_CURRENT = 'eventsCurrent'

const DATABASE_TIMESTAMP_KEY = 'FIRESTORE_databaseTimestamp';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const convertToFirestore = (externalData: any): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  const now = firebase.firestore.Timestamp.now();
  firestoreData[DATABASE_TIMESTAMP_KEY] = now;
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = now.seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): any => {
  const result = {} as any;
  Object.assign(result, firestoreData);
  return result;
}

export const save = async (deviceBuildTimestamp: string, data: any) => {
  const firestoreData = convertToFirestore(data);
  // Set the 'current' data.
  await firebase.app().firestore().collection(EVENTS_CURRENT).doc(deviceBuildTimestamp).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(EVENTS_ALL)
    .add(firestoreData);
  console.debug('save:', EVENTS_CURRENT, EVENTS_ALL, allRes.id);
}

export const getCurrent = async (deviceBuildTimestamp: string): Promise<any> => {
  const currentRef = await firebase.app().firestore().collection(EVENTS_CURRENT)
    .doc(deviceBuildTimestamp).get();
  return convertFromFirestore(currentRef.data());
}
