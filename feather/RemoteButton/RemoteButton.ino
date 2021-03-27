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

ServerApi serverApi(&Serial);
ServerResponse serverdata;
String session = "";
String buttonAckToken = "NO_BUTTON_ACK_TOKEN";
unsigned long HEARTBEAT_INTERVAL = 1000 * 10; // 10 seconds.
unsigned long PUSH_REMOTE_BUTTON_DURATION_MILLIS = 500;

void pushRemoteButton(unsigned long durationMillis) {
  digitalWrite(REMOTE_BUTTON_PIN, HIGH);
  delay(durationMillis);
  digitalWrite(REMOTE_BUTTON_PIN, LOW);
}

bool pingServer(ClientParams params) {
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
  // If the server provided a new buttonAckToken, push the button.
  String newButtonAckToken = serverdata.buttonAckToken;
  if (newButtonAckToken.length() > 0 && newButtonAckToken != buttonAckToken) {
    pushRemoteButton(PUSH_REMOTE_BUTTON_DURATION_MILLIS);
  }
  buttonAckToken = newButtonAckToken;
  digitalWrite(LED_BUILTIN, LOW);
  return true;
}

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
  ClientParams params;
  params.session = session;
  params.buttonAckToken = buttonAckToken;
  pingServer(params);
  delay(HEARTBEAT_INTERVAL);
}
