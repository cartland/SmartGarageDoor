import * as firebase from 'firebase-admin';
import { IQAirObservation } from '../model/IQAirManager';

const IQ_AIR_CURRENT_OBSERVATIONS = 'iqAirObservationsCurrent';
const IQ_AIR_ALL_OBSERVATIONS = 'iqAirObservationsAll';

const LAT_LON_KEY = 'FIRESTORE_latLon';
const DATABASE_TIMESTAMP_SECONDS_KEY = 'FIRESTORE_databaseTimestampSeconds';

const latLonKeyFromLatLon = (lat: string, lon: string): string => {
  return "LAT" + lat + "&LON" + lon;
}

const convertToFirestore = (externalData: IQAirObservation, lat: string, lon: string, seconds: number): any => {
  const firestoreData = {};
  Object.assign(firestoreData, externalData);
  firestoreData[LAT_LON_KEY] = latLonKeyFromLatLon(lat, lon);
  firestoreData[DATABASE_TIMESTAMP_SECONDS_KEY] = seconds;
  return firestoreData;
}

const convertFromFirestore = (firestoreData: any): IQAirObservation => {
  const result = {} as IQAirObservation;
  Object.assign(result, firestoreData);
  return result;
}

/**
 * @param lat Latitude.
 * @param lon Longitude.
 * @param externalData External data from IQAir API.
 */
export const saveIQAirObservation = async (lat: string, lon: string, externalData: IQAirObservation) => {
  const seconds = firebase.firestore.Timestamp.now().seconds;
  const latLonKey = latLonKeyFromLatLon(lat, lon);

  const firestoreData = convertToFirestore(externalData, lat, lon, seconds);
  // Set the 'current' observation.
  await firebase.app().firestore().collection(IQ_AIR_CURRENT_OBSERVATIONS)
    .doc(latLonKey).set(firestoreData);
  // Add historical observation to database.
  const allRes = await firebase.app().firestore()
    .collection(IQ_AIR_ALL_OBSERVATIONS)
    .add(firestoreData);
  console.debug('saveIQAirObservation:',
    IQ_AIR_CURRENT_OBSERVATIONS, latLonKey, IQ_AIR_ALL_OBSERVATIONS, allRes.id);
}

export const getCurrentIQAirObservation = async (lat: string, lon: string): Promise<IQAirObservation> => {
  const latLonKey = latLonKeyFromLatLon(lat, lon);
  const currentRef = await firebase.app().firestore().collection(IQ_AIR_CURRENT_OBSERVATIONS)
    .doc(latLonKey).get();
  return convertFromFirestore(currentRef.data());
}
