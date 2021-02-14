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


/**
 * Current Observation data from AirNowApi.org.
 *
 * https://docs.airnowapi.org/CurrentObservationsByZip/docs
 */
export interface AirNowApiObservation {
  DateObserved: string,   // "2021-02-13 " // API returns trailing space
  HourObserved: number,   // 22
  LocalTimeZone: string,  // "PST"
  ReportingArea: string,  // "San Francisco"
  StateCode: string,      // "CA"
  Latitude: number,       // 37.75
  Longitude: number,      // -122.43
  ParameterName: string,  // "PM2.5", "O3"
  AQI: number,            // 19
  Category: AirNowApiCategory   // {"Number":1,"Name":"Good"}
}

export interface AirNowApiCategory {
  Number: number, // 1
  Name: string,   // "Good"
}

/**
 * Air quality data returned by the server.
 *
 * No nested fields.
 * Data is cleaned up before serving (remove trailing whitespace).
 */
export interface AirQualityObservation {
  DateObserved: string,   // "2021-02-13"
  HourObserved: number,   // 22
  LocalTimeZone: string,  // "PST"
  ReportingArea: string,  // "San Francisco"
  StateCode: string,      // "CA"
  Latitude: number,       // 37.75
  Longitude: number,      // -122.43
  ParameterName: string,  // "PM2.5", "O3"
  AQI: number,            // 19
  CategoryNumber: number, // 1
  CategoryName: string,   // "Good"
}

/**
 * Manage data from AirNow API.
 */
export class AirNowManager {

  /**
   * Convert data from the AirNow API to the data retured by our server.
   *
   * @param data Data from the AirNow API.
   */
  observationFromApi(data: AirNowApiObservation): AirQualityObservation {
    return <AirQualityObservation>{
      DateObserved: data.DateObserved.trim(),
      HourObserved: data.HourObserved,
      LocalTimeZone: data.LocalTimeZone.trim(),
      ReportingArea: data.ReportingArea.trim(),
      StateCode: data.StateCode.trim(),
      Latitude: data.Latitude,
      Longitude: data.Longitude,
      ParameterName: data.ParameterName,
      AQI: data.AQI,
      CategoryNumber: data.Category.Number,
      CategoryName: data.Category.Name
    }
  }

  observationListFromApi(data: AirNowApiObservation[]) {
    const observations = [];
    for (const apiObservation of data) {
      observations.push(this.observationFromApi(apiObservation));
    }
    return observations;
  }

  /**
   * Return first qir quality observation for PM2.5.
   *
   * @param data Unfiltered air quality observation list.
   */
  getPM25Observation(data: AirQualityObservation[]): AirQualityObservation {
    return this.getFirstObservationWithParamaterName(data, "PM2.5");
  }

  /**
   * Return first qir quality observation for Ozone (O3).
   *
   * @param data Unfiltered air quality observation list.
   */
  getOzoneObservation(data: AirQualityObservation[]): AirQualityObservation {
    return this.getFirstObservationWithParamaterName(data, "O3");
  }

  /**
   * Return the first data that matches the ParameterName.
   *
   * The PM2.5 data and Ozone (O3) data appear in the same list.
   * This function allows us to extract one type of observation.
   *
   * @param data Air quality data of multiple types.
   * @param ParameterName The type of data to return ("PM2.5" or "O3").
   */
  getFirstObservationWithParamaterName(data: AirQualityObservation[], ParameterName: string): AirQualityObservation {
    if (data.length === 0) {
      return null;
    }
    for (const observation of data) {
      if (observation.ParameterName === ParameterName) {
        return observation;
      }
    }
    return null;
  }
}
