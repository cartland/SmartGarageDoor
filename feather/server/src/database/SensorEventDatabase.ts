import * as firebase from 'firebase-admin';

import { SensorEvent } from '../model/SensorEvent';

const EVENTS_ALL = 'eventsAll';
const EVENTS_CURRENT = 'eventsCurrent'

const DATABASE_TIMESTAMP_KEY = 'FIRESTORE_databaseTimestamp';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const convertToFirestore = (externalData: SensorEvent): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  const now = firebase.firestore.Timestamp.now();
  firestoreData[DATABASE_TIMESTAMP_KEY] = now;
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = now.seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): SensorEvent => {
  const result = {} as any;
  Object.assign(result, firestoreData);
  return result;
}

export const save = async (deviceBuildTimestamp: string, data: SensorEvent) => {
  const firestoreData = convertToFirestore(data);
  // Set the 'current' data.
  await firebase.app().firestore().collection(EVENTS_CURRENT).doc(deviceBuildTimestamp).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(EVENTS_ALL)
    .add(firestoreData);
  console.debug('save:', EVENTS_CURRENT, EVENTS_ALL, allRes.id);
}

export const getCurrent = async (deviceBuildTimestamp: string): Promise<SensorEvent> => {
  const currentRef = await firebase.app().firestore().collection(EVENTS_CURRENT)
    .doc(deviceBuildTimestamp).get();
  return convertFromFirestore(currentRef.data());
}
