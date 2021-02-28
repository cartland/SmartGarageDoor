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

import * as functions from 'firebase-functions';
import * as AirNowDatabase from '../../database/AirNowDatabase';
import * as AirNowNetwork from '../../network/AirNowNetwork';
import * as IQAirDatabase from '../../database/IQAirDatabase';
import * as IQAirNetwork from '../../network/IQAirNetwork';
import * as OWMDatabase from '../../database/OWMDatabase';
import * as OWMNetwork from '../../network/OWMNetwork';
import { airNowManager, iqAirManager, owmManager } from '../shared';

// TODO: Merge all data into 1 API request.

/**
 * Get the current air quality observations.
 *
 * Request the data from AirNowApi.org. Reformat the data and return the result.
 *
 * airNowApiKey: Required. API key from airnowapi.org.
 * zipCode: Required.
 * miles: Required. Also known as "distance" in the AirNowApi.org.
 * parameterName: Return observation for P2.5 or Ozone.
 * Deafult: PM2.5
 *
 * curl -H "Content-Type: application/json" http://localhost:5000/weather-escape/us-central1/current_air_quality_observation\?zipCode\=10011\&miles\=5\&airNowApiKey\=AIR_NOW_API_KEY
 */
export const current_air_quality_observation = functions.https.onRequest(async (request, response) => {
  // Air Now API parameters.
  const AIR_NOW_API_KEY = <string>request.query['airNowApiKey'];
  const ZIP_CODE = <string>request.query['zipCode'];
  const MILES = <string>request.query['miles'];

  // IQAir API parameters.
  const IQA_API_KEY = <string>request.query['iqaApiKey'];
  const LAT = <string>request.query['lat'];
  const LON = <string>request.query['lon'];
  try {
    // 1. FETCH from external API.
    const airNowFetchData = await AirNowNetwork.getCurrentAirNowObservation(AIR_NOW_API_KEY, ZIP_CODE, MILES);
    const iqAirFetchData = await IQAirNetwork.getCurrentIQAirObservation(IQA_API_KEY, LAT, LON);
    // 2. SAVE in database.
    await AirNowDatabase.saveAirNowObservations(ZIP_CODE, airNowFetchData);
    await IQAirDatabase.saveIQAirObservation(LAT, LON, iqAirFetchData);
    // 3. RETRIEVE from database.
    let airNowExternalData = await AirNowDatabase.getCurrentAirNowObservation(ZIP_CODE, 'PM2.5');
    if (!airNowExternalData) {
      airNowExternalData = await AirNowDatabase.getCurrentAirNowObservation(ZIP_CODE, 'O3');
    }
    if (!airNowExternalData) {
      airNowExternalData = await AirNowDatabase.getCurrentAirNowObservation(ZIP_CODE, 'PM10');
    }
    const iqAirExternalData = await IQAirDatabase.getCurrentIQAirObservation(LAT, LON);
    // 4. RESPOND with formatted data.
    const airNowResponseData = airNowManager.observationFromApi(airNowExternalData);
    const iqAirResponseData = iqAirManager.observationFromApi(iqAirExternalData);
    if (airNowResponseData.DateObserved.length > 0) {
      console.info('Returning Air Now Data', JSON.stringify(airNowResponseData));
      console.debug('Not returning IQAir data', JSON.stringify(iqAirResponseData));
      response.status(200).send(airNowResponseData);
    } else {
      console.info('Returning IQAir data', JSON.stringify(iqAirResponseData));
      console.debug('Not returning Air Now Data', JSON.stringify(airNowResponseData));
      response.status(200).send(iqAirResponseData);
    }
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});

/**
 * Get the current weather.
 *
 * Request the data from https://openweathermap.org. Reformat the data and return the result.
 *
 * owmApiKey: Required. API key from https://openweathermap.org.
 * zipCountry: Required. "Zip_Code,Country_Code".
 * units: Required. "imperial" or "metric".
 * language: Required.
 *
 * curl -H "Content-Type: application/json" http://localhost:5000/weather-escape/us-central1/current_weather\?zipCountry\=10011,us\&units\=imperial\&language\=en\&owmApiKey\=OWM_API_KEY
 */
export const current_weather = functions.https.onRequest(async (request, response) => {
  const OWM_API_KEY = <string>request.query['owmApiKey'];
  const ZIP_COUNTRY = <string>request.query['zipCountry'];
  const UNITS = <string>request.query['units'];
  const LANGUAGE = <string>request.query['language'];
  try {
    // 1. FETCH from external API.
    const fetchData = await OWMNetwork.fetchCurrentOpenWeatherMapObservation(OWM_API_KEY, ZIP_COUNTRY, UNITS, LANGUAGE);
    // 2. SAVE in database.
    await OWMDatabase.saveOpenWeatherMapObservation(ZIP_COUNTRY, fetchData);
    // 3. RETRIEVE from database.
    const externalData = await OWMDatabase.getCurrentOpenWeatherMapObservation(ZIP_COUNTRY);
    // 4. RESPOND with formatted data.
    const responseData = owmManager.clientDataFromRemoteData(externalData);
    console.info(JSON.stringify(responseData));
    response.status(200).send(responseData);
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
});
