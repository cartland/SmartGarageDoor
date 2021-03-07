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

#define SENSOR_PIN_A 2
#define SENSOR_PIN_B 3
#define LED_PIN_A 6
#define LED_PIN_B 7
#define DEBOUNCE_MILLIS 500

void onStateAChanged(int value) {
  Serial.print("State A Changed: ");
  Serial.println(value);
}

void onStateBChanged(int value) {
  Serial.print("State B Changed: ");
  Serial.println(value);
}

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println(""); // First line is usually lost. Print empty line.
  pinMode(SENSOR_PIN_A, INPUT);
  pinMode(SENSOR_PIN_B, INPUT);
  pinMode(LED_PIN_A, OUTPUT);
  pinMode(LED_PIN_B, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  blinkOK(LED_BUILTIN);
  Serial.println("==========");
  Serial.println(String(__TIMESTAMP__));
}

void loop() {
  static int stateA = 0;
  static int stateB = 0;
  static int lastSensorA = 0;
  static int lastSensorB = 0;
  static unsigned long debounceTimeA = 0;
  static unsigned long debounceTimeB = 0;

  unsigned long currentTime = millis();

  int newSensorA = digitalRead(SENSOR_PIN_A);
  int newSensorB = digitalRead(SENSOR_PIN_B);

  if (newSensorA != lastSensorA) {
    debounceTimeA = currentTime;
  }
  if (newSensorB != lastSensorB) {
    debounceTimeB = currentTime;
  }

  if (currentTime - debounceTimeA > DEBOUNCE_MILLIS) {
    if (stateA != newSensorA) {
      onStateAChanged(newSensorA);
    }
    stateA = newSensorA;
  }
  if (stateA == 1) {
    digitalWrite(LED_PIN_A, HIGH);
  } else {
    digitalWrite(LED_PIN_A, LOW);
  }
  if (currentTime - debounceTimeB > DEBOUNCE_MILLIS) {
    if (stateB != newSensorB) {
      onStateBChanged(newSensorB);
    }
    stateB = newSensorB;
  }
  if (stateB == 1) {
    digitalWrite(LED_PIN_B, LOW);
  } else {
    digitalWrite(LED_PIN_B, HIGH);
  }

  lastSensorA = newSensorA;
  lastSensorB = newSensorB;
}
