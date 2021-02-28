/**
 * Copyright curl -H "Content-Type: application/json" http://localhost:5000/weather-escape/us-central1/current_weather\?zipCountry\=10011,us\&units\=imperial\&language\=en\&owmApiKey\=88c0c6b7b0375087d95b9dee7cce88d3
. All Rights Reserved.
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

import { AirNowManager } from '../model/AirNowManager';
import { IQAirManager } from '../model/IQAirManager';
import { OpenWeatherMapManager } from '../model/OpenWeatherMapManager';

export const airNowManager = new AirNowManager();
export const iqAirManager = new IQAirManager();
export const owmManager = new OpenWeatherMapManager();
