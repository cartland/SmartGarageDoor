/**
   GET requests.
*/

#include <WiFiMulti.h>

#include "WiFiGet.h"

WiFiMulti WiFiMulti;

String wifiSetup(String wifiSSID, String wifiPassword) {
  WiFiMulti.addAP(wifiSSID.c_str(), wifiPassword.c_str());
  Serial.println("Looking for WiFi...");
  while (WiFiMulti.run() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("");
  return WiFi.localIP().toString();
}

/**
   Usage:

  const uint16_t port = 443;
  char buf[4000];
  String urlc = URL;
  wget(urlc, 80, buf);
  Serial.println(buf);
*/
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
