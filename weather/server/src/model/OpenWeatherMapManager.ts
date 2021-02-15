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
 * Current weather from Open Weather Map API.
 *
 * https://openweathermap.org/current#current_JSON
 *
 * Example Response:
 {
  "coord": {
    "lon": -122.08,
    "lat": 37.39
  },
  "weather": [
    {
      "id": 800,
      "main": "Clear",
      "description": "clear sky",
      "icon": "01d"
    }
  ],
  "base": "stations",
  "main": {
    "temp": 282.55,
    "feels_like": 281.86,
    "temp_min": 280.37,
    "temp_max": 284.26,
    "pressure": 1023,
    "humidity": 100
  },
  "visibility": 16093,
  "wind": {
    "speed": 1.5,
    "deg": 350
  },
  "clouds": {
    "all": 1
  },
  "dt": 1560350645,
  "sys": {
    "type": 1,
    "id": 5122,
    "message": 0.0139,
    "country": "US",
    "sunrise": 1560343627,
    "sunset": 1560396563
  },
  "timezone": -25200,
  "id": 420006353,
  "name": "Mountain View",
  "cod": 200
}
 */
export interface OWMCurrentWeather {
  coord: Coord,
  weather: Weather[],
  base: string,
  main: Main,
  visibility: number,
  wind: Wind,
  clouds: Clouds,
  dt: number,
  sys: Sys,
  timezone: number,
  id: number,
  name: string,
  cod: number,
}

interface Coord {
  lon: number,
  lat: number,
}

interface Weather {
  id: number,
  main: string,
  description: string,
  icon: string,
}

interface Main {
  temp: number,
  feels_like: number,
  temp_min: number,
  temp_max: number,
  pressure: number,
  humidity: number,
}

interface Wind {
  speed: number,
  deg: number,
}

interface Clouds {
  all: number,
}

interface Sys {
  type: number,
  id: number,
  message: number,
  country: string,
  sunrise: number,
  sunset: number,
}

/**
 * Air quality data returned by the server.
 *
 * No nested fields.
 * Data is cleaned up before serving (remove trailing whitespace).
 */
export interface CurrentWeatherResponse {
  lat: number,
  lon: number,
  main: string,
  description: string,
  icon: string,
  cityName: string,
  visibility: number,
  timezone: number,
  country: string,
  observationTime: number,
  sunrise: number,
  sunset: number,
  temp: number,
  pressure: number,
  humidity: number,
  tempMin: number,
  tempMax: number,
  windSpeed: number,
  windDeg: number,
}

/**
 * Manage data from Open Weather Map API.
 */
export class OpenWeatherMapManager {

  /**
   * Convert data from the Open Weather Map API to the data retured by our server.
   *
   * @param data Data from the Open Weather Map API.
   */
  clientDataFromRemoteData(data: OWMCurrentWeather): CurrentWeatherResponse {
    return <CurrentWeatherResponse>{
      cityName: data.name,
      visibility: data.visibility,
      timezone: data.timezone,
      observationTime: data.dt,

      // Coord.
      lat: data.coord.lat,
      lon: data.coord.lon,

      // Weather.
      main: data.weather[0].main,
      description: data.weather[0].description,
      icon: data.weather[0].icon,

      // Sys.
      country: data.sys.country,
      sunrise: data.sys.sunrise,
      sunset: data.sys.sunset,

      // Main.
      temp: data.main.temp,
      pressure: data.main.pressure,
      humidity: data.main.humidity,
      tempMin: data.main.temp_min,
      tempMax: data.main.temp_max,

      // Wind.
      windSpeed: data.wind.speed,
      windDeg: data.wind.deg,
    }
  }

  clientDataFromList(owmList: OWMCurrentWeather[]): CurrentWeatherResponse[] {
    const result = [];
    for (const owmData of owmList) {
      result.push(this.clientDataFromRemoteData(owmData));
    }
    return result;
  }
}
