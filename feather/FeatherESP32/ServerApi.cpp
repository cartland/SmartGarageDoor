#include "ServerApi.h"

String ServerApi::buildUrl() {
  String baseUrl = URL;
  String buildTimestamp = __TIMESTAMP__;
  // Convert "Wed Mar  3 00:28:41 2021" to "Wed%20Mar%20%203%2000%3A28%3A41%202021"
  buildTimestamp.replace(" ", "%20");
  long deviceTimestamp = millis();
  return baseUrl + "?buildTimestamp=" + buildTimestamp + "&deviceTimestamp=" + deviceTimestamp;
}

bool ServerApi::parseData(ServerResponse &data, String json) {
  Serial->println("ServerApi::parseData");
  DynamicJsonDocument doc(2000);
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
  return true;
}
