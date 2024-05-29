/**
   Copyright 2021 Chris Cartland. All Rights Reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

#pragma once

// secrets.h

// WiFi.
#define WIFI_SSID "WIFI_SSID"
#define WIFI_PASSWORD "WIFI_PASSWORD"
#define URL "https://example.com/remoteButton" // Parameters will be added: ?key1=value2&key2=value2

// Exactly one of the following must be true!!!
// * USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER
// Adafruit HUZZAH32 - ESP32 Feather
#define USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER true
// * USE_ADAFRUIT_METRO_M4_EXPRESS_AIRLIFT
// Adafruit Metro M4 Express AirLift
#define USE_ADAFRUIT_METRO_M4_EXPRESS_AIRLIFT false

// Tested with Assembled Feather HUZZAH w/ ESP8266 WiFi With Stacking Headers
#if USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER
#define USE_MULTI_WIFI true
#else
#define USE_MULTI_WIFI false
#endif

#if USE_ADAFRUIT_METRO_M4_EXPRESS_AIRLIFT
// Tested with Adafruit Metro M4 Express AirLift (WiFi) - Lite
#define USE_WIFI_NINA true
#else
#define USE_WIFI_NINA false
#endif
