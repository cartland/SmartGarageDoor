/**
 * Feather ESP32 WiFi GET request to a URL.
 */

#include <WiFi.h>
#include <WiFiMulti.h>

#include "secrets.h"

WiFiMulti WiFiMulti;

void wget(String &url, int port, char *buff) {
  int pos1 = url.indexOf("/", 0);
  int pos2 = url.indexOf("/", 8);
  String host = url.substring(pos1 + 2, pos2);
  String path = url.substring(pos2);
  Serial.println("Parsed: wget(" + host + "," + path + "," + port + ")");
  wget(host, path, port, buff);
}

void wget(String &host, String &path, int port, char *buff) {
  Serial.println("Preparing GET request...");
  Serial.print("Connecting to host: ");
  Serial.print(host);
  Serial.print(", path: ");
  Serial.print(path);
  Serial.print(", port: ");
  Serial.print(port);
  Serial.println("...");
  WiFiClient client;
  client.stop();
  if (client.connect(host.c_str(), port)) {
    Serial.println("Making GET request...");
    client.println(String("GET ") + path + String(" HTTP/1.0"));
    client.println("Host: " + host);
    client.println("Connection: close");
    client.println();
    uint32_t bytes = 0;
    int capturepos = 0;
    bool capture = false;
    int linelength = 0;
    char lastc = '\0';
    while (true) {
      while (client.available()) {
        char c = client.read();
        if ((c == '\n') && (lastc == '\r')) {
          if (linelength == 0) {
            capture = true;
          }
          linelength = 0;
        } else if (capture) {
          buff[capturepos++] = c;
        } else {
          if ((c != '\n') && (c != '\r')) {
            linelength++;
          }
        }
        lastc = c;
        bytes++;
      }
      // If the server is disconnected, stop the client.
      if (!client.connected()) {
        Serial.println("Done with GET request. Disconnecting from the server.");
        client.stop();
        buff[capturepos] = '\0';
        Serial.println("Captured " + String(capturepos) + " bytes.");
        break;
      }
    }
  } else {
    Serial.println("Problem connecting to " + host + ":" + String(port));
    buff[0] = '\0';
  }
  Serial.println("Done with GET request.");
}

void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println("");
  Serial.println("==========");
  Serial.println(String(__TIMESTAMP__));
  Serial.println("Connecting to WiFi access point...");
  WiFiMulti.addAP(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("Waiting for WiFi...");
  while (WiFiMulti.run() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("");
  Serial.println("WiFi connected!");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void loop() {
  static uint32_t loopsCompleted = 0;
  static boolean firsttime = true;
  static boolean finished = false;
  const uint32_t MAX_LOOPS = 3;

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
  Serial.println(URL);
  const uint16_t port = 443;
  char buf[4000];
  String urlc = URL;
  wget(urlc, 80, buf);
  Serial.println(buf);
  loopsCompleted++;
}
