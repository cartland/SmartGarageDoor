import * as firebase from 'firebase-admin';

const UPDATE_CURRENT = 'updateCurrent';
const UPDATE_ALL = 'updateAll';

const CURRENT_KEY = "current";

const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const convertToFirestore = (externalData: any, seconds: number): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): any => {
  const result = {} as any;
  Object.assign(result, firestoreData);
  return result;
}

export const save = async (data: any) => {
  const seconds = firebase.firestore.Timestamp.now().seconds;
  const firestoreData = convertToFirestore(data, seconds);
  // Set the 'current' data.
  await firebase.app().firestore().collection(UPDATE_CURRENT).doc(CURRENT_KEY).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(UPDATE_ALL)
    .add(firestoreData);
  console.debug('save:', UPDATE_CURRENT, UPDATE_ALL, allRes.id);
}

export const getCurrent = async (): Promise<any> => {
  const currentRef = await firebase.app().firestore().collection(UPDATE_CURRENT)
    .doc(CURRENT_KEY).get();
  return convertFromFirestore(currentRef.data());
}
