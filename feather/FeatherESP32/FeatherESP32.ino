/**
   Feather ESP32 WiFi GET request to a URL.
*/

#include "secrets.h"
#include "WiFiGet.h"
#include "ServerApi.h"

const uint32_t MAX_LOOPS = 0;

ServerApi serverApi(&Serial);
ServerResponse serverdata;
String session = "";

const long morseCodeLong = 400;
const long morseCodeShort = 100;
const long morseCodeChar = 200;
const long morseCodeEnd = 500;

void blinkDash() {
  digitalWrite(LED_BUILTIN, HIGH);
  delay(morseCodeLong);
  digitalWrite(LED_BUILTIN, LOW);
  delay(morseCodeLong);
}

void blinkDot() {
  digitalWrite(LED_BUILTIN, HIGH);
  delay(morseCodeShort);
  digitalWrite(LED_BUILTIN, LOW);
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

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println(""); // First line is usually lost. Print empty line.

  pinMode(LED_BUILTIN, OUTPUT);
  blinkOK();

  Serial.println("==========");
  Serial.println(String(__TIMESTAMP__));
  Serial.println("Connecting to WiFi access point...");
  String ipAddress = wifiSetup(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("WiFi connected to IP address ");
  Serial.println(ipAddress);
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
    delay(delaySeconds * 1000);
    if (DOUBLE_DELAY_EACH_TIME) {
      delaySeconds = delaySeconds * 2;
    }
  }
  firsttime = false;
  digitalWrite(LED_BUILTIN, HIGH); // LED on when network request starts.
  Serial.print("-> Making URL request: ");
  String url = serverApi.buildUrl(session);
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
