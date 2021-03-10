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

#include "WiFiGet.h"

bool wifiSetup(String wifiSSID, String wifiPassword) {
#if USE_WIFI_NINA
  return WiFiNINASetup(wifiSSID, wifiPassword);
#endif
#if USE_MULTI_WIFI
  return wifiMultiSetup(wifiSSID, wifiPassword);
#endif
}

/**
   Usage:

  const uint16_t port = 443;
  char buf[4000];
  String urlc = URL;
  wget(urlc, 80, buf);
  Serial.println(buf);
*/
bool wget(String &url, int port, char *buff) {
  int pos1 = url.indexOf("/", 0);
  int pos2 = url.indexOf("/", 8);
  String host = url.substring(pos1 + 2, pos2);
  String path = url.substring(pos2);
  Serial.println("Parsed: wget(" + host + "," + path + "," + port + ")");
#if USE_WIFI_NINA
  return wgetWifiNINA(host, path, port, buff);
#endif
#if USE_MULTI_WIFI
  return wgetWifiMulti(host, path, port, buff);
#endif
}
