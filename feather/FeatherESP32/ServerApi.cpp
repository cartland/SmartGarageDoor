#include "ServerApi.h"

String ServerApi::buildUrl(String session, String batteryVoltage) {
  String baseUrl = URL;
  String buildTimestamp = __TIMESTAMP__;
  // Convert "Wed Mar  3 00:28:41 2021" to "Wed%20Mar%20%203%2000%3A28%3A41%202021"
  buildTimestamp.replace(" ", "%20");
  long deviceTimestamp = millis();
  if (session.length() > 0) {
    return baseUrl + "?buildTimestamp=" + buildTimestamp + "&deviceTimestamp=" + deviceTimestamp + "&batteryVoltage=" + batteryVoltage + "&session=" + session;
  }
  return baseUrl + "?buildTimestamp=" + buildTimestamp + "&deviceTimestamp=" + deviceTimestamp + "&batteryVoltage=" + batteryVoltage;
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
  data.session = (const char*) doc["session"];
  return true;
}