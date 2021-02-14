#pragma once
#include "secrets.h"
#define ARDUINOJSON_USE_LONG_LONG 1
#include <ArduinoJson.h>          //https://github.com/bblanchon/ArduinoJson

typedef struct AirQualityObservation {
    String DateObserved;    // "2021-02-13"
    int HourObserved;       // 22
    String LocalTimeZone;   // "PST"
    String ReportingArea;   // "San Francisco"
    String StateCode;       // "CA"
    float Latitude;         // 37.75
    float Longitude;        // -122.43
    String ParameterName;   // "PM2.5", "O3"
    float AQI;              // 19
    int CategoryNumber;     // 1
    String CategoryName;    // "Good"
} AirQualityObservation;

class AirQualityApi {
  private:
    Stream *Serial;
    String currentKey;
    String currentParent;
    //OpenWeatherMapCurrentData *data;
    uint8_t weatherItemCounter = 0;
    bool metric = true;
    String language;
    String _error;

  public:
    AirQualityApi(Stream *serial){Serial = serial;};
    String buildUrlCurrent(bool ozone);
    bool updateCurrent(AirQualityObservation &data,String json);
    void setError(String error){_error = error;}
    String getError(){return _error;}
};
