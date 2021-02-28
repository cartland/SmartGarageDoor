import * as firebase from 'firebase-admin';
import { OWMCurrentWeather } from '../model/OpenWeatherMapManager';

const AIR_NOW_CURRENT_OBSERVATIONS = 'airNowObservationsCurrent';
const AIR_NOW_ALL_OBSERVATIONS = 'airNowObservationsAll';

const convertToFirestore = (externalData: OWMCurrentWeather, zipCountry: string, seconds: number): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  firestoreData["zipCountry"] = zipCountry;
  firestoreData["databaseTimestampSeconds"] = seconds;
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
  await firebase.app().firestore().collection(AIR_NOW_CURRENT_OBSERVATIONS)
    .doc(zipCountry).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore().collection(AIR_NOW_ALL_OBSERVATIONS)
    .add(firestoreData);
  console.debug('saveOpenWeatherMapObservation:',
    AIR_NOW_CURRENT_OBSERVATIONS, zipCountry, AIR_NOW_ALL_OBSERVATIONS, allRes.id);
}

export const getCurrentOpenWeatherMapObservation = async (zipCountry: string): Promise<OWMCurrentWeather> => {
  const currentRef = await firebase.app().firestore().collection(AIR_NOW_CURRENT_OBSERVATIONS)
    .doc(zipCountry).get();
  return convertFromFirestore(currentRef.data());
}
