import * as firebase from 'firebase-admin';
import { OWMCurrentWeather } from '../model/OpenWeatherMapManager';

const OWM_CURRENT_OBSERVATIONS = 'openWeatherMapObservationsCurrent';
const OWM_ALL_OBSERVATIONS = 'openWeatherMapObservationsAll';

const ZIP_COUNTRY_KEY = 'FIRESTORE_zipCountry';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const convertToFirestore = (externalData: OWMCurrentWeather, zipCountry: string, seconds: number): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  firestoreData[ZIP_COUNTRY_KEY] = zipCountry;
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): OWMCurrentWeather => {
  const result = {} as OWMCurrentWeather;
  Object.assign(result, firestoreData);
  return result;
}

export const saveOpenWeatherMapObservation = async (zipCountry: string, externalData: OWMCurrentWeather) => {
  const seconds = firebase.firestore.Timestamp.now().seconds;
  const firestoreData = convertToFirestore(externalData, zipCountry, seconds);
  // Set observation for zipCountry. This is the "current" value for the location.
  await firebase.app().firestore().collection(OWM_CURRENT_OBSERVATIONS)
    .doc(zipCountry).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore().collection(OWM_ALL_OBSERVATIONS)
    .add(firestoreData);
  console.debug('saveOpenWeatherMapObservation:',
    OWM_CURRENT_OBSERVATIONS, zipCountry, OWM_ALL_OBSERVATIONS, allRes.id);
}

export const getCurrentOpenWeatherMapObservation = async (zipCountry: string): Promise<OWMCurrentWeather> => {
  const currentRef = await firebase.app().firestore().collection(OWM_CURRENT_OBSERVATIONS)
    .doc(zipCountry).get();
  return convertFromFirestore(currentRef.data());
}
