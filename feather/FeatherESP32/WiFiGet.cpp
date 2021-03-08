/**
   GET requests.
*/

#include "WiFiGet.h"

#ifdef USE_MULTI_WIFI
WiFiMulti WiFiMulti;

String wifiMultiSetup(String wifiSSID, String wifiPassword) {
  WiFiMulti.addAP(wifiSSID.c_str(), wifiPassword.c_str());
  Serial.println("Looking for WiFi...");
  while (WiFiMulti.run() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("");
  return WiFi.localIP().toString();
}

void wgetWifiMulti(String &host, String &path, int port, char *buff) {
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
#endif USE_MULTI_WIFI


#ifdef USE_WIFI_NINA // Check WiFiGet.h
WiFiSSLClient client;
int status = WL_IDLE_STATUS;

bool WiFiNINASetup(String wifiSSID, String wifiPassword) {
  // Check for WiFi module.
  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Error: Communication with WiFi module failed!");
    return false;
  }
  String fv = WiFi.firmwareVersion();
  if (fv < WIFI_FIRMWARE_LATEST_VERSION) {
    Serial.println("Warning: Please upgrade the firmware");
  }

  Serial.print("Attempting to connect to WiFi SSID: ");
  Serial.println(wifiSSID.c_str());
  while (status != WL_CONNECTED) {
    // Connect to WPA/WPA2 network.
    status = WiFi.begin(wifiSSID.c_str(), wifiPassword.c_str());
    Serial.print(".");
    delay(500);
  }
  Serial.println("");

  Serial.print("Connected to SSID: ");
  Serial.println(WiFi.SSID());

  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);

  long rssi = WiFi.RSSI();
  Serial.print("WiFi signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");

  return true;
}

void wgetWifiNINA(String &host, String &path, int port, char *buff) {
  Serial.print("wget host: ");
  Serial.print(host);
  Serial.print(", path: ");
  Serial.print(path);
  Serial.print(", port: ");
  Serial.print(port);
  Serial.println("...");
  client.stop();
  Serial.println("Connecting to host...");
  if (client.connect(host.c_str(), port)) {
    Serial.print("Connected to host: ");
    Serial.println(host);
    Serial.println("Making GET request...");
    client.println(String("GET ") + path + String(" HTTP/1.1"));
    client.println("Host: " + host);
    client.println("Connection: close");
    client.println();
    uint32_t bytes = 0;
    int capturepos = 0;
    bool capture = false;
    int linelength = 0;
    char lastc = '\0';
    unsigned long loopCount = 0;
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
      // Also, stop after 1000 loops. Sometimes the server doesn't disconnect.
      if (!client.connected() || loopCount > 1000) {
        Serial.println("Done with GET request. Disconnecting from the server.");
        client.stop();
        buff[capturepos] = '\0';
        Serial.println("Captured " + String(capturepos) + " bytes.");
        return;
      }
      loopCount++;
    }
  } else {
    Serial.println("Problem connecting to " + host + ":" + String(port));
    buff[0] = '\0';
  }
  Serial.println("Done with GET request.");
}
#endif // #ifdef USE_WIFI_NINA




String wifiSetup(String wifiSSID, String wifiPassword) {
  // WiFiMulti version.
  return wifiMultiSetup(wifiSSID, wifiPassword);
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
  wgetWifiMulti(host, path, port, buff);
}
