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

#pragma once

#include "secrets.h"
#define ARDUINOJSON_USE_LONG_LONG 1
#include <ArduinoJson.h>          //https://github.com/bblanchon/ArduinoJson

typedef struct ClientParams {
  String session;
  String batteryVoltage;
  String sensorA;
  String sensorB;
} ClientParams;

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
    String buildUrl(ClientParams params);
    bool parseData(ServerResponse &data, String json);
    void setError(String error) {
      _error = error;
    }
    String getError() {
      return _error;
    }
};
