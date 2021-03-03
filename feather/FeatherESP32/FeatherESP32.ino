/**
   Feather ESP32 WiFi GET request to a URL.
*/

#include "secrets.h"
#include "WiFiGet.h"
#include "ServerApi.h"

const uint32_t MAX_LOOPS = 2;

ServerApi serverApi(&Serial);

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

  if (loopsCompleted >= MAX_LOOPS) {
    if (!finished) {
      Serial.println("Finished!");
    }
    finished = true;
    delay(10 * 1000);
    return;
  }
  if (!firsttime) {
    Serial.println("Waiting 10 seconds...");
    delay(10 * 1000);
  }
  firsttime = false;
  Serial.print("-> Making URL request: ");
  String url = serverApi.buildUrl();
  Serial.println(url);
  const uint16_t port = 443;
  char buf[4000];
  wget(url, 80, buf);
  Serial.println(buf);
  loopsCompleted++;
}
