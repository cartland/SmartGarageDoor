import * as firebase from 'firebase-admin';
import { AirNowApiObservation } from '../model/AirNowManager';

const AIR_NOW_CURRENT_OBSERVATIONS = 'airNowObservationsCurrent';
const AIR_NOW_ALL_OBSERVATIONS = 'airNowObservationsAll';
const PARAMETER_NAME_COLLECTION_KEY = 'parameterName';

const ZIP_CODE_KEY = 'FIRESTORE_zipCode';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const convertToFirestore = (externalData: AirNowApiObservation, zipCode: string, seconds: number): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  firestoreData[ZIP_CODE_KEY] = zipCode;
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): AirNowApiObservation => {
  const result = {} as AirNowApiObservation;
  Object.assign(result, firestoreData);
  return result;
}

/**
 * Save each item in a list from the Air Now API.
 *
 * @param zipCode zipCode used with the Air Now API.
 * @param externalData External data from the Air Now API.
 */
export const saveAirNowObservations = async (zipCode: string, externalData: AirNowApiObservation[]) => {
  const seconds = firebase.firestore.Timestamp.now().seconds;
  for (const apiObservation of externalData) {
    await saveAirNowObservation(seconds, zipCode, apiObservation.ParameterName, apiObservation);
  }
}

export const saveAirNowObservation = async (
  seconds: number, zipCode: string, parameterName: string, externalData: AirNowApiObservation) => {
  const firestoreData = convertToFirestore(externalData, zipCode, seconds);
  // Set the 'current' observation.
  await firebase.app().firestore().collection(AIR_NOW_CURRENT_OBSERVATIONS)
    .doc(zipCode).collection(PARAMETER_NAME_COLLECTION_KEY).doc(parameterName).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(AIR_NOW_ALL_OBSERVATIONS)
    .doc(zipCode).collection(PARAMETER_NAME_COLLECTION_KEY)
    .add(firestoreData);
  console.debug('saveAirNowObservation:',
    AIR_NOW_CURRENT_OBSERVATIONS, zipCode, parameterName, AIR_NOW_ALL_OBSERVATIONS, allRes.id);
}

export const getCurrentAirNowObservation = async (zipCode: string, parameterName: string): Promise<AirNowApiObservation> => {
  const currentRef = await firebase.app().firestore().collection(AIR_NOW_CURRENT_OBSERVATIONS)
    .doc(zipCode).collection(PARAMETER_NAME_COLLECTION_KEY).doc(parameterName).get();
  return convertFromFirestore(currentRef.data());
}
