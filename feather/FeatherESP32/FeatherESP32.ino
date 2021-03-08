/**
   Feather ESP32 WiFi GET request to a URL.
*/

#include "secrets.h"
#include "WiFiGet.h"
#include "ServerApi.h"

// Analog input to measure battery voltage.
// https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/power-management
#define A13 35

const uint32_t MAX_LOOPS = 0;

ServerApi serverApi(&Serial);
ServerResponse serverdata;
String session = "";

const long morseCodeLong = 400;
const long morseCodeShort = 100;
const long morseCodeChar = 200;
const long morseCodeEnd = 500;

void blinkOn(long duration) {
  digitalWrite(LED_BUILTIN, HIGH);
  delay(duration);
  digitalWrite(LED_BUILTIN, LOW);
}

void blinkDash() {
  blinkOn(morseCodeLong);
  delay(morseCodeLong);
}

void blinkDot() {
  blinkOn(morseCodeShort);
  delay(morseCodeLong);
}

void blinkMorseCode(int sequence[], int len) {
  for (int i = 0; i < len; i++) {
    if (sequence[i]) {
      blinkDash();
    } else {
      blinkDot();
    }
  }
}

void blinkOK() {
  int LETTER_O[3] = {1, 1, 1};
  blinkMorseCode(LETTER_O, 3); // "O"
  delay(morseCodeChar);
  int LETTER_K[3] = {1, 0, 1};
  blinkMorseCode(LETTER_K, 3); // "K"
  delay(morseCodeEnd);
}

void blinkingDelay(long ms) {
  long block = 10 * 1000; // Blink once per block.
  long duration = 100; // Blink duration.
  while (ms > 0) {
    if (ms < block) { // Delay is smaller than the block.
      if (ms < duration) { // Delay is smaller than the blink duration.
        blinkOn(ms); // Blink for the remainder of the delay, then return.
        ms = 0;
        return;
      }
      // Delay is smaller than the block, but larger than the blink duration.
      blinkOn(duration); // Delay for the blink duration.
      ms = ms - duration;
      delay(ms); // Delay for the remaining time, then return.
      return;
    }
    blinkOn(duration); // Blink for the normal blink duration.
    ms = ms - duration;
    delay(block); // Delay for the block size.
    ms = ms - block;
  }
}

float readBatteryVoltage() {
  float ADAFRUIT_MULTIPLIER = 2.0;
  float MAX_INPUT = 4095.0;
  float MAX_VOLTAGE = 3.3;
  // https://cuddletech.com/?p=1030 
  // https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/peripherals/adc.html#overview
  float ADC_REFERENCE_VOLTAGE = 1.1; // 1100mV ADC Reference Voltage
  return (analogRead(A13) / MAX_INPUT) * MAX_VOLTAGE * ADC_REFERENCE_VOLTAGE * ADAFRUIT_MULTIPLIER;
}

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println(""); // First line is usually lost. Print empty line.

  pinMode(LED_BUILTIN, OUTPUT);
  blinkOK();

  Serial.println("==========");
  Serial.println(String(__TIMESTAMP__));
  Serial.println("Connecting to WiFi access point...");
  bool success = wifiSetup(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("WiFi connected!");
}

void loop() {
  static uint32_t loopsCompleted = 0;
  static boolean firsttime = true;
  static boolean finished = false;
  static long delaySeconds = 60 * 10; // 10 minutes.
  const long MAX_DELAY_SECONDS = 60 * 60 * 24; // 1 day.
  const bool DOUBLE_DELAY_EACH_TIME = false;

  // If MAX_LOOPS == 0, never finish.
  if ((MAX_LOOPS > 0) && (loopsCompleted >= MAX_LOOPS)) {
    if (!finished) {
      Serial.println("Finished!");
    }
    finished = true;
    delay(delaySeconds * 1000);
    return;
  }
  if (!firsttime) {
    Serial.print("Waiting ");
    Serial.print(String(delaySeconds));
    Serial.println(" seconds...");
    if (delaySeconds > MAX_DELAY_SECONDS) {
      delaySeconds = MAX_DELAY_SECONDS;
    }
    blinkingDelay(delaySeconds * 1000);
    if (DOUBLE_DELAY_EACH_TIME) {
      delaySeconds = delaySeconds * 2;
    }
  }
  firsttime = false;
  digitalWrite(LED_BUILTIN, HIGH); // LED on when network request starts.
  Serial.print("-> Making URL request: ");
  String batteryVoltage = String(readBatteryVoltage());
  String url = serverApi.buildUrl(session, batteryVoltage);
  Serial.println(url);
  const uint16_t port = 443;
  char buf[4000];
  wget(url, 80, buf);
  String json = buf;
  Serial.println(json);
  bool success = serverApi.parseData(serverdata, json);
  if (!success) {
    Serial.println("Failed to parse server data. No session provided.");
  }
  session = serverdata.session;
  if (session.length() <= 0) {
    Serial.println("No session ID.");
  } else {
    Serial.print("Session ID: ");
    Serial.println(serverdata.session);
  }
  digitalWrite(LED_BUILTIN, LOW); // LED off when network request completes.
  loopsCompleted++;
}
