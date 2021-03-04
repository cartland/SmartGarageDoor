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

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println(""); // First line is usually lost. Print empty line.
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
  loopsCompleted++;
}
