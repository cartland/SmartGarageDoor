/**
 * Copyright 2018 Google LLC. All Rights Reserved.
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
import * as https from 'https';
import * as url from 'url';
import { AirNowApiObservation } from '../../model/AirNowManager';
import { airNowManager } from '../shared'

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
  const AIR_NOW_API_KEY = <string>request.query['airNowApiKey']
  const ZIP_CODE = <string>request.query['zipCode']
  const MILES = <string>request.query['miles']
  const PARAMETER_NAME = <string>request.query['parameterName']
  try {
    const apiObservations = await getCurrentAirNowObservation(AIR_NOW_API_KEY, ZIP_CODE, MILES);
    const observations = airNowManager.observationListFromApi(apiObservations);
    switch (PARAMETER_NAME) {
      case "O3":
        response.status(200).send(airNowManager.getOzoneObservation(observations));
        break;
      default:
      // Fall through. Default is PM2.5.
      case "PM2.5":
        response.status(200).send(airNowManager.getPM25Observation(observations));
        break;
    }
  }
  catch (error) {
    console.error(error)
    response.status(500).send(error)
  }
})

/**
 * Request data from AirNowApi.gov.
 *
 * @param airNowApiKey API key from airnowapi.org.
 * @param zipCode US ZIP Code.
 * @param miles Maximum distance from Zip Code.
 */
const getCurrentAirNowObservation = async (airNowApiKey: string, zipCode: string, miles: string) => {
  return new Promise<AirNowApiObservation[]>(function (resolve, reject) {
    const airNowUrl = new url.URL("https://www.airnowapi.org/aq/observation/zipCode/current/");
    airNowUrl.searchParams.append('format', 'application/json');
    airNowUrl.searchParams.append('zipCode', zipCode);
    airNowUrl.searchParams.append('distance', miles);
    airNowUrl.searchParams.append('API_KEY', airNowApiKey);
    https.get(airNowUrl.href, (res) => {
      const body = [];
      res.on('data', function (chunk) {
        body.push(chunk);
      });
      res.on('end', function () {
        try {
          const fullBody = Buffer.concat(body).toString();
          if (res.statusCode >= 400) {
            reject(fullBody);
            return;
          }
          const observations: AirNowApiObservation[] = JSON.parse(fullBody);
          resolve(observations);
        } catch (e) {
          reject(e);
        }
      });
    });
  });
}
