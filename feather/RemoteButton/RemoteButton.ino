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

#include "secrets.h"
#include "BlinkMorseCode.h"
#include "WiFiGet.h"
#include "ServerApi.h"

#if USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER
// Use A13 on Adafruit HUZZAH32 - ESP32 Feather.
// https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/pinouts
#define REMOTE_BUTTON_PIN A1
#define RST_PIN 27 // https://www.instructables.com/two-ways-to-reset-arduino-in-software/
#define WIFI_PORT 80
#endif

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println(""); // First line is usually lost. Print empty line.
  pinMode(REMOTE_BUTTON_PIN, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  blinkOK(LED_BUILTIN);
  Serial.println("==========");
  Serial.println(String(__TIMESTAMP__));
}

void loop() {
  unsigned long currentTime = millis();

  // Press remote button for 500 ms every 10 seconds.
  if (currentTime % 10000 < 500) {
    digitalWrite(REMOTE_BUTTON_PIN, HIGH);
    digitalWrite(LED_BUILTIN, HIGH);
  } else {
    digitalWrite(REMOTE_BUTTON_PIN, LOW);
    digitalWrite(LED_BUILTIN, LOW);
  }
}
