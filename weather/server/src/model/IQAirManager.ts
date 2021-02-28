import { AirQualityObservation } from './AirQualityObservation';

/**
 * Current observation data from iqair.com.
 *
 * https://api-docs.iqair.com/?version=latest
 *
 * Example response:
 {
  "status": "success",
  "data": {
    "city": "Mountain View",
    "state": "California",
    "country": "USA",
    "location": {
      "type": "Point",
      "coordinates": [
        -122.088255,
        37.373795
      ]
    },
    "current": {
      "weather": {
        "ts": "2021-02-28T07:00:00.000Z",
        "tp": 9,
        "pr": 1022,
        "hu": 62,
        "ws": 2.06,
        "wd": 270,
        "ic": "01n"
      },
      "pollution": {
        "ts": "2021-02-28T07:00:00.000Z",
        "aqius": 31,
        "mainus": "p2",
        "aqicn": 11,
        "maincn": "p2"
      }
    }
  }
}
 */
export interface IQAirObservation {
  status: string,
  data: Data
}

interface Data {
  city: string,
  state: string,
  country: string,
  location: Location,
  current: Current
}

interface Location {
  type: string,
  coordinates: number[]
}

interface Current {
  weather: Weather,
  pollution: Pollution
}

interface Weather {
  ts: string,
  tp: number,
  pr: number,
  hu: number,
  ws: number,
  wd: number,
  ic: string
}

interface Pollution {
  ts: string,
  aqius: number,
  mainus: string,
  aqicn: number,
  maincn: string
}

/**
 * Manage data from IQAir API.
 */
export class IQAirManager {

  /**
   * Convert data from the IQAir API to the data retured by our server.
   *
   * @param data Data from the IQAir API.
   */
  observationFromApi(data: IQAirObservation): AirQualityObservation {
    const date = Date.parse(data.data.current.pollution.ts);
    return <AirQualityObservation>{
      DateObserved: data.data.current.pollution.ts,
      HourObserved: 0,
      LocalTimeZone: '',
      ReportingArea: data.data.city,
      StateCode: data.data.state,
      Latitude: data.data.location.coordinates[0],
      Longitude: data.data.location.coordinates[1],
      ParameterName: 'Unknown',
      AQI: data.data.current.pollution.aqius,
      CategoryNumber: 0,
      CategoryName: ''
    }
  }
}