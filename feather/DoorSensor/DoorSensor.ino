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

#define SIGNAL_HIGH 1
#define SIGNAL_LOW 0
#define SWITCH_OPEN SIGNAL_HIGH
#define SWITCH_CLOSED SIGNAL_LOW
#define DEBOUNCE_MILLIS 250

// Analog input to measure battery voltage.
//
// https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/pinouts
// https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/power-management
#define A13 35

#if USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER
// Use A13 on Adafruit HUZZAH32 - ESP32 Feather.
// https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/pinouts
#define VOLTAGE_INPUT_PIN A13
#define SENSOR_PIN_A 14
#define SENSOR_PIN_B 32
#define LED_PIN_A 15
#define LED_PIN_B 33
#define RST_PIN 27 // https://www.instructables.com/two-ways-to-reset-arduino-in-software/
#define WIFI_PORT 80
#endif

#if USE_ADAFRUIT_METRO_M4_EXPRESS_AIRLIFT
// Use A0 on Adafruit Metro M4 Express AirLift.
// https://learn.adafruit.com/adafruit-metro-m4-express-airlift-wifi/pinouts-2
#define VOLTAGE_INPUT_PIN A0
#define SENSOR_PIN_A 2 // Pull down switch with pull-up resitor.
#define SENSOR_PIN_B 3 // Pull down switch with pull-up resitor.
#define LED_PIN_A 6
#define LED_PIN_B 7
#define RST_PIN 8 // https://www.instructables.com/two-ways-to-reset-arduino-in-software/
#define WIFI_PORT 443
#endif

Debouncer debouncer(&Serial, DEBOUNCE_MILLIS);

ServerApi serverApi(&Serial);
ClientParams params;
ServerResponse serverdata;
String session = "";
unsigned long HEARTBEAT_INTERVAL = 1000 * 60 * 10; // 10 minutes.
unsigned long lastNetworkRequestTime = 0;
float batteryVoltage = 0.0;

const unsigned long BLINK_PERIOD_MS = 1000 * 10; // 10 seconds.
const unsigned long BLINK_DURATION_MS = 500; // 500 ms.

// https://www.instructables.com/two-ways-to-reset-arduino-in-software/
void resetDevice() {
  pinMode(RST_PIN, OUTPUT);
  digitalWrite(RST_PIN, LOW);
}

void fail(String msg, int delaySeconds) {
  Serial.print("Failure: ");
  Serial.println(msg);
  Serial.print("Restarting device after ");
  Serial.print(delaySeconds);
  Serial.println(" seconds...");
  for (int i = 0; i < delaySeconds; i++) {
    delay(1000);
    Serial.print('.');
  }
  Serial.println();
  Serial.println("Resetting now!");
  resetDevice();
}

float readBatteryVoltage() {
  float ADAFRUIT_MULTIPLIER = 2.0;
  float MAX_ANALOG_READ_VOLTAGE_INPUT = 4095.0;
  float MAX_VOLTAGE = 3.3;
  // https://cuddletech.com/?p=1030
  // https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/peripherals/adc.html#overview
  float ADC_REFERENCE_VOLTAGE = 1.1; // 1100mV ADC Reference Voltage
  return (analogRead(VOLTAGE_INPUT_PIN) / MAX_ANALOG_READ_VOLTAGE_INPUT) * MAX_VOLTAGE * ADC_REFERENCE_VOLTAGE * ADAFRUIT_MULTIPLIER;
}

bool updateServerSensorData(ClientParams params) {
  digitalWrite(LED_BUILTIN, HIGH); // Blink a little while contacting the server.
  String url = serverApi.buildUrl(params);
  Serial.print("Request URL: ");
  Serial.println(url);
  const uint16_t port = WIFI_PORT;
  char buf[4000];
  wget(url, port, buf);
  String json = buf;
  Serial.println(json);
  bool success = serverApi.parseData(serverdata, json);
  if (!success) {
    digitalWrite(LED_BUILTIN, LOW);
    return false;
  }
  session = serverdata.session;
  if (session.length() <= 0) {
    Serial.println("No session ID.");
  } else {
    Serial.print("Session ID: ");
    Serial.println(serverdata.session);
  }
  digitalWrite(LED_BUILTIN, LOW);
  return true;
}

void setup() {
  // https://www.instructables.com/two-ways-to-reset-arduino-in-software/
  // Write PIN high immediately in order to avoid resetting the device.
  digitalWrite(RST_PIN, HIGH);
  delay(200);
  pinMode(RST_PIN, OUTPUT);

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
    fail("Failed to connect to WiFi.", 5);
  }
}

void loop() {
  unsigned long currentTime = millis();

  bool changedA = debouncer.debounceUpdate(SENSOR_PIN_A, currentTime);
  int debouncedA = debouncer.debounceGet(SENSOR_PIN_A);
  String debouncedAString = String(debouncedA);
#if USE_SENSOR_A
#else
  changedA = false;
  debouncedAString = "";
#endif
  if (debouncedA == SWITCH_CLOSED) {
    digitalWrite(LED_PIN_A, HIGH);
  } else {
    digitalWrite(LED_PIN_A, LOW);
  }

  bool changedB = debouncer.debounceUpdate(SENSOR_PIN_B, currentTime);
  int debouncedB = debouncer.debounceGet(SENSOR_PIN_B);
  String debouncedBString = String(debouncedB);
#if USE_SENSOR_B
#else
  changedB = false;
  debouncedBString = "";
#endif
  if (debouncedB == SWITCH_CLOSED) {
    digitalWrite(LED_PIN_B, HIGH);
  } else {
    digitalWrite(LED_PIN_B, LOW);
  }

  batteryVoltage = readBatteryVoltage();
  bool networkSuccess = false;
  if (changedA) {
    Serial.print("Sensor A Changed: ");
    Serial.print(debouncedAString);
    Serial.print(" - Battery voltage: ");
    Serial.println(batteryVoltage);
    ClientParams params;
    params.session = session;
    params.batteryVoltage = String(batteryVoltage);
    params.sensorA = debouncedAString;
    params.sensorB = "";
    networkSuccess = updateServerSensorData(params);
    if (!networkSuccess) {
      fail("Server update failed", 60);
    }
    lastNetworkRequestTime = currentTime;
    Serial.println();
  }
  if (changedB) {
    Serial.print("Sensor B Changed: ");
    Serial.print(debouncedBString);
    Serial.print(" - Battery voltage: ");
    Serial.println(batteryVoltage);
    ClientParams params;
    params.session = session;
    params.batteryVoltage = String(batteryVoltage);
    params.sensorA = "";
    params.sensorB = debouncedBString;
    networkSuccess = updateServerSensorData(params);
    if (!networkSuccess) {
      fail("Server update failed", 60);
    }
    lastNetworkRequestTime = currentTime;
    Serial.println();
  }
  if (currentTime - lastNetworkRequestTime > HEARTBEAT_INTERVAL || lastNetworkRequestTime == 0) {
    Serial.print("Heartbeat - Battery voltage: ");
    Serial.println(batteryVoltage);
    digitalWrite(LED_BUILTIN, HIGH);
    ClientParams params;
    params.session = session;
    params.batteryVoltage = String(batteryVoltage);
    params.sensorA = debouncedAString;
    params.sensorB = debouncedBString;
    networkSuccess = updateServerSensorData(params);
    if (!networkSuccess) {
      fail("Server update failed", 60);
    }
    lastNetworkRequestTime = currentTime;
    digitalWrite(LED_BUILTIN, LOW);
    Serial.println();
  }

  unsigned long blinkTime = currentTime % BLINK_PERIOD_MS;
  if (blinkTime < BLINK_DURATION_MS) {
    digitalWrite(LED_BUILTIN, HIGH);
  } else {
    digitalWrite(LED_BUILTIN, LOW);
  }
}
