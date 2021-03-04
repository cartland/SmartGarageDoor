#pragma once

#include "secrets.h"
#define ARDUINOJSON_USE_LONG_LONG 1
#include <ArduinoJson.h>          //https://github.com/bblanchon/ArduinoJson

typedef struct ServerResponse {
  String version;
  int code;
  String session;
} ServerResponse;

class ServerApi {
  private:
    Stream *Serial;
    String _error;

  public:
    ServerApi(Stream *serial) {
      Serial = serial;
    };
    String buildUrl(String session, String batteryVoltage);
    bool parseData(ServerResponse &data, String json);
    void setError(String error) {
      _error = error;
    }
    String getError() {
      return _error;
    }
};
