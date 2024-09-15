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

#define MORSE_CODE_DASH_MILLIS 400
#define MORSE_CODE_DOT_MILLIS 100
#define MORSE_CODE_CHAR_PAUSE_MILLIS 200
#define MORSE_CODE_WORD_PAUSE_MILLIS 500

void blinkOn(int pin, long duration);

void blinkDash(int pin);

void blinkDot(int pin);

void blinkMorseCode(int pin, int sequence[], int len);

void blinkOK(int pin);
