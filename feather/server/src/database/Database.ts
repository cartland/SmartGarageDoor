import * as firebase from 'firebase-admin';

const UPDATE_CURRENT = 'updateCurrent';
const UPDATE_ALL = 'updateAll';

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

export const save = async (session: string, data: any) => {
  const firestoreData = convertToFirestore(data);
  // Set the 'current' data.
  await firebase.app().firestore().collection(UPDATE_CURRENT).doc(session).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(UPDATE_ALL)
    .add(firestoreData);
  console.debug('save:', UPDATE_CURRENT, UPDATE_ALL, allRes.id);
}

export const getCurrent = async (session: string): Promise<any> => {
  const currentRef = await firebase.app().firestore().collection(UPDATE_CURRENT)
    .doc(session).get();
  return convertFromFirestore(currentRef.data());
}
