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

#include "ServerApi.h"

String ServerApi::buildUrl(ClientParams params) {
  String buildTimestamp = __TIMESTAMP__;
  // Convert "Wed Mar  3 00:28:41 2021" to "Wed%20Mar%20%203%2000%3A28%3A41%202021"
  buildTimestamp.replace(" ", "%20");
  long deviceTimestamp = millis();
  String url = URL + String("?buildTimestamp=") + buildTimestamp + String("&deviceTimestamp=") + String(deviceTimestamp);
  if (params.session.length() > 0) {
    url = url + String("&session=") + params.session;
  }
  if (params.buttonAckToken.length() > 0) {
    url = url + String("&buttonAckToken=") + params.buttonAckToken;
  }
  if (params.error.length() > 0) {
    params.error.replace(" ", "%20");
    url = url + String("&error=") + params.error;
  }
  return url;
}

bool ServerApi::parseData(ServerResponse &data, String json) {
  Serial->println("ServerApi::parseData");
  DynamicJsonDocument doc(4000);
  Serial->println(json);
  DeserializationError error = deserializeJson(doc, json);
  if (error) {
    Serial->println(String("deserializeJson() failed: ") + (const char *)error.c_str());
    Serial->println(json);
    setError(String("deserializeJson() failed: ") + error.c_str());
    return false;
  }
  data.version = (const char*) doc["version"];
  data.code = (int) doc["version"];
  data.session = (const char*) doc["session"];
  data.buttonAckToken = (const char*) doc["buttonAckToken"];
  return true;
}
