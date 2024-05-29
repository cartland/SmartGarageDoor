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

#include "BlinkMorseCode.h"

void blinkOn(int pin, long duration) {
  digitalWrite(pin, HIGH);
  delay(duration);
  digitalWrite(pin, LOW);
}

void blinkDash(int pin) {
  blinkOn(pin, MORSE_CODE_DASH_MILLIS);
  delay(MORSE_CODE_DASH_MILLIS);
}

void blinkDot(int pin) {
  blinkOn(pin, MORSE_CODE_DOT_MILLIS);
  delay(MORSE_CODE_DASH_MILLIS);
}

void blinkMorseCode(int pin, int sequence[], int len) {
  for (int i = 0; i < len; i++) {
    if (sequence[i]) {
      blinkDash(pin);
    } else {
      blinkDot(pin);
    }
  }
}

void blinkOK(int pin) {
  int LETTER_O[3] = {1, 1, 1};
  blinkMorseCode(pin, LETTER_O, 3); // "O"
  delay(MORSE_CODE_CHAR_PAUSE_MILLIS);
  int LETTER_K[3] = {1, 0, 1};
  blinkMorseCode(pin, LETTER_K, 3); // "K"
  delay(MORSE_CODE_WORD_PAUSE_MILLIS);
}
