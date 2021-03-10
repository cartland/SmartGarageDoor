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

#include "WiFiGetMulti.h"

#if USE_MULTI_WIFI
WiFiMulti WiFiMulti;

bool wifiMultiSetup(String wifiSSID, String wifiPassword) {
  Serial.println("Using WiFiMulti");
  WiFiMulti.addAP(wifiSSID.c_str(), wifiPassword.c_str());
  unsigned int retryCount = 0;
  Serial.print("Looking for WiFi SSID: ");
  Serial.println(wifiSSID.c_str());
  while (WiFiMulti.run() != WL_CONNECTED) {
    if (retryCount > WIFI_CONNECT_RETRY_MAX) {
      Serial.println();
      Serial.print("Tried to connect to WiFi ");
      Serial.print(retryCount);
      Serial.println(" times.");
      return false;
    }
    Serial.print(".");
    delay(500);
    retryCount++;
  }
  Serial.println("");
  return true;
}

bool wgetWifiMulti(String &host, String &path, int port, char *buff) {
  Serial.println("Preparing GET request...");
  Serial.print("Connecting to host: ");
  Serial.print(host);
  Serial.print(", path: ");
  Serial.print(path);
  Serial.print(", port: ");
  Serial.print(port);
  Serial.println("...");
  WiFiClient client;
  client.stop();
  if (client.connect(host.c_str(), port)) {
    Serial.println("Making GET request...");
    client.println(String("GET ") + path + String(" HTTP/1.0"));
    client.println("Host: " + host);
    client.println("Connection: close");
    client.println();
    uint32_t bytes = 0;
    int capturepos = 0;
    bool capture = false;
    int linelength = 0;
    char lastc = '\0';
    unsigned long loopCount = 0;
    while (true) {
      while (client.available()) {
        char c = client.read();
        if ((c == '\n') && (lastc == '\r')) {
          if (linelength == 0) {
            capture = true;
          }
          linelength = 0;
        } else if (capture) {
          buff[capturepos++] = c;
        } else {
          if ((c != '\n') && (c != '\r')) {
            linelength++;
          }
        }
        lastc = c;
        bytes++;
      }
      // If the server is disconnected, stop the client.
      if (!client.connected()) {
        Serial.println("Done with GET request. Disconnecting from the server.");
        Serial.print("loopCount: ");
        Serial.println(loopCount);
        client.stop();
        buff[capturepos] = '\0';
        Serial.println("Captured " + String(capturepos) + " bytes.");
        return true;
      }
      // Also, stop after 100000 loops. Sometimes the server doesn't disconnect.
      // 10,000 loops is common. Make sure the timeout is higher.
      if (loopCount > 100000) {
        Serial.println("Client abandoning the GET request. Disconnecting from the server.");
        client.stop();
        buff[capturepos] = '\0';
        Serial.println("Captured " + String(capturepos) + " bytes.");
        return false;
      }
      if (loopCount % 10000 == 0) {
        Serial.println("...captured " + String(capturepos) + " bytes so far...");
      }
      loopCount++;
    }
  } else {
    Serial.println("Problem connecting to " + host + ":" + String(port));
    buff[0] = '\0';
    return true;
  }
}
#endif
