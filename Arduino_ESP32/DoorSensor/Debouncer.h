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

#include "Arduino.h"

#define DEBOUNCER_MAX_PIN_COUNT 32
#define DEBOUNCER_INVALID -1

class Debouncer {
  private:
    Stream *Serial;
    String _error;
    unsigned long debounceDuration;
    int state[DEBOUNCER_MAX_PIN_COUNT];
    int lastRead[DEBOUNCER_MAX_PIN_COUNT];
    unsigned long debounceTime[DEBOUNCER_MAX_PIN_COUNT];

  public:
    Debouncer(Stream *serial, unsigned long duration) {
      Serial = serial;
      debounceDuration = duration;
      for (int i = 0; i < DEBOUNCER_MAX_PIN_COUNT; i++) {
        state[i] = DEBOUNCER_INVALID;
        lastRead[i] = DEBOUNCER_INVALID;
        debounceTime[i] = 0;
      }
    };
    bool debounceUpdate(int SENSOR_PIN, unsigned long currentTime);
    int debounceGet(int SENSOR_PIN);
    void setError(String error) {
      _error = error;
    }
    String getError() {
      return _error;
    }
};
