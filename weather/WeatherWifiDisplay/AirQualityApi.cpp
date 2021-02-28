#include "AirQualityApi.h"

String AirQualityApi::buildUrlCurrent(bool ozone) {
  String baseUrl = AIR_QUALITY_CURRENT_URL;
  String parameterName = ozone ? "O3" : "PM2.5";
  return baseUrl + "?zipCode=" + ZIP_CODE + "&miles=" + String(DISTANCE_MILES) + "&airNowApiKey=" + AIR_NOW_API_KEY + "&parameterName=" + parameterName;
}

bool AirQualityApi::updateCurrent(AirQualityObservation &data, String json) {
  Serial->println("AirQualityApi updateCurrent()");
  DynamicJsonDocument doc(2000);

  Serial->println(json);
  DeserializationError error = deserializeJson(doc, json);
  if (error) {
    Serial->println(String("deserializeJson() failed: ") + (const char *)error.c_str());
    Serial->println(json);
    setError(String("deserializeJson() failed: ") + error.c_str());
    return false;
  }

  data.DateObserved = (const char*) doc["DateObserved"];    // "2021-02-13"
  data.HourObserved = (int) doc["HourObserved"];            // 22
  data.LocalTimeZone = (const char*) doc["LocalTimeZone"];  // "PST"
  data.ReportingArea = (const char*) doc["ReportingArea"];  // "San Francisco"
  data.StateCode = (const char*) doc["StateCode"];          // "CA"
  data.Latitude = (float) doc["Latitude"];                  // 37.75
  data.Longitude = (float) doc["Longitude"];                // -122.43
  data.ParameterName = (const char*) doc["ParameterName"];  // "PM2.5", "O3"
  data.AQI = (float) doc["AQI"];                            // 19
  data.CategoryNumber = (int) doc["CategoryNumber"];        // 1
  data.CategoryName = (const char*) doc["CategoryName"];    // "Good"
  Serial->println("Finished Air Quality Data");
  return true;
}
