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
#include "Debouncer.h"
#include "WiFiGet.h"
#include "ServerApi.h"

#define SENSOR_PIN_A 2
#define SENSOR_PIN_B 3
#define SWITCH_OPEN 1
#define SWITCH_CLOSED 0
#define SIGNAL_HIGH 1
#define SIGNAL_LOW 0
#define LED_PIN_A 6
#define LED_PIN_B 7
#define DEBOUNCE_MILLIS 500

Debouncer debouncer(&Serial, DEBOUNCE_MILLIS);

ServerApi serverApi(&Serial);
ClientParams params;
ServerResponse serverdata;
String session = "";
float batteryVoltage = 0.0f;

void onStateAChanged(int value) {
  Serial.print("State A Changed: ");
  Serial.println(value);
  ClientParams params;
  params.session = session;
  params.batteryVoltage = "";
  params.sensorA = String(value);
  params.sensorB = "";
  String url = serverApi.buildUrl(params);
  Serial.print("Request URL: ");
  Serial.println(url);
  const uint16_t port = 443;
  char buf[4000];
  wget(url, port, buf);
  String json = buf;
  Serial.println(json);
  bool success = serverApi.parseData(serverdata, json);
  if (!success) {
    Serial.println("Failed to parse server data.");
    return;
  }
  session = serverdata.session;
  if (session.length() <= 0) {
    Serial.println("No session ID.");
  } else {
    Serial.print("Session ID: ");
    Serial.println(serverdata.session);
  }
}

void onStateBChanged(int value) {
  Serial.print("State B Changed: ");
  Serial.println(value);
  ClientParams params;
  params.session = session;
  params.batteryVoltage = "";
  params.sensorA = "";
  params.sensorB = String(value);
  String url = serverApi.buildUrl(params);
  Serial.print("Request URL: ");
  Serial.println(url);
  const uint16_t port = 443;
  char buf[4000];
  wget(url, port, buf);
  String json = buf;
  Serial.println(json);
  bool success = serverApi.parseData(serverdata, json);
  if (!success) {
    Serial.println("Failed to parse server data.");
    return;
  }
  session = serverdata.session;
  if (session.length() <= 0) {
    Serial.println("No session ID.");
  } else {
    Serial.print("Session ID: ");
    Serial.println(serverdata.session);
  }
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

  bool success = wifiSetup(WIFI_SSID, WIFI_PASSWORD);
  if (success) {
    Serial.println("Successfully connected to WiFi.");
  } else {
    Serial.println("Failed to connect to WiFi.");
  }
}

void loop() {
  unsigned long currentTime = millis();

  bool changedA = debouncer.debounceUpdate(SENSOR_PIN_A, currentTime);
  int debouncedA = debouncer.debounceGet(SENSOR_PIN_A);
  if (changedA) {
    onStateAChanged(debouncedA);
  }
  if (debouncedA == SWITCH_CLOSED) {
    digitalWrite(LED_PIN_A, HIGH);
  } else {
    digitalWrite(LED_PIN_A, LOW);
  }

  bool changedB = debouncer.debounceUpdate(SENSOR_PIN_B, currentTime);
  int debouncedB = debouncer.debounceGet(SENSOR_PIN_B);
  if (changedB) {
    onStateBChanged(debouncedB);
  }
  if (debouncedB == SIGNAL_HIGH) {
    digitalWrite(LED_PIN_B, LOW);
  } else {
    digitalWrite(LED_PIN_B, HIGH);
  }
}
