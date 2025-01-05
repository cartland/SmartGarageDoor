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

#include "Debouncer.h"

bool Debouncer::debounceUpdate(int pin, unsigned long currentTime) {
  bool changed = false;
  int newRead = digitalRead(pin);
  if (newRead != lastRead[pin]) {
    debounceTime[pin] = currentTime;
  }
  if (currentTime - debounceTime[pin] > debounceDuration) {
    if (state[pin] != newRead) {
      Serial->print(currentTime);
      Serial->print(": ");
      Serial->print("Debounced pin: ");
      Serial->print(pin);
      Serial->print(", value: ");
      Serial->println(newRead);
      changed = true;
    }
    state[pin] = newRead;
  }
  lastRead[pin] = newRead;
  return changed;
}

int Debouncer::debounceGet(int pin) {
  return state[pin];
}
