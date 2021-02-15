#include <time.h>
#include <Adafruit_GFX.h>    // Core graphics library
#include <Adafruit_EPD.h>
#include <Adafruit_NeoPixel.h>
#define ARDUINOJSON_USE_LONG_LONG 1
#include <ArduinoJson.h>        //https://github.com/bblanchon/ArduinoJson
#include <SPI.h>
#include <WiFiNINA.h>

#include "secrets.h"
#include "OpenWeatherMap.h"
#include "AirQualityApi.h"

#include "Fonts/meteocons48pt7b.h"
#include "Fonts/meteocons24pt7b.h"
#include "Fonts/meteocons20pt7b.h"
#include "Fonts/meteocons16pt7b.h"

#include <Fonts/FreeSans9pt7b.h>
#include <Fonts/FreeSans12pt7b.h>
#include <Fonts/FreeSans18pt7b.h>
#include <Fonts/FreeSansBold12pt7b.h>
#include <Fonts/FreeSansBold24pt7b.h>

#define SRAM_CS     8
#define EPD_CS      10
#define EPD_DC      9
#define EPD_RESET -1
#define EPD_BUSY -1

#define NEOPIXELPIN   40

// This is for the 2.7" tricolor EPD
Adafruit_IL91874 gfx(264, 176 ,EPD_DC, EPD_RESET, EPD_CS, SRAM_CS, EPD_BUSY);

AirliftOpenWeatherMap owclient(&Serial);
OpenWeatherMapCurrentData owcdata;
OpenWeatherMapForecastData owfdata[3];

AirQualityApi airQualityApi(&Serial);
AirQualityObservation airQualityData;

Adafruit_NeoPixel neopixel = Adafruit_NeoPixel(1, NEOPIXELPIN, NEO_GRB + NEO_KHZ800);

int8_t readButtons(void) {
  uint16_t reading = analogRead(A3);
  //Serial.println(reading);

  if (reading > 600) {
    return 0; // no buttons pressed
  }
  if (reading > 400) {
    return 4; // button D pressed
  }
  if (reading > 250) {
    return 3; // button C pressed
  }
  if (reading > 125) {
    return 2; // button B pressed
  }
  return 1; // Button A pressed
}

bool wifi_connect() {
  Serial.print("Connecting to WiFi... ");
  WiFi.setPins(SPIWIFI_SS, SPIWIFI_ACK, ESP32_RESETN, ESP32_GPIO0, &SPIWIFI);
  // Check for the WiFi module.
  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Communication with WiFi module failed!");
    displayError("Communication with WiFi module failed!");
    return false;
  }
  String fv = WiFi.firmwareVersion();
  if (fv < "1.0.0") {
    Serial.println("Please upgrade the firmware");
  }
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 255));
  neopixel.show();
  if (WiFi.begin(WIFI_SSID, WIFI_PASSWORD) == WL_CONNECT_FAILED) {
    Serial.println("WiFi connection failed!");
    displayError("WiFi connection failed!");
    return false;
  }
  int wifitimeout = 15;
  int wifistatus;
  while ((wifistatus = WiFi.status()) != WL_CONNECTED && wifitimeout > 0) {
    delay(1000);
    Serial.print(".");
    wifitimeout--;
  }
  if (wifitimeout == 0) {
    Serial.println("WiFi connection timeout with error " + String(wifistatus));
    displayError("WiFi connection timeout with error " + String(wifistatus));
    neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
    neopixel.show();
    return false;
  }
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
  Serial.println("Connected");
  return true;
}

void wget(String &url, int port, char *buff) {
  int pos1 = url.indexOf("/",0);
  int pos2 = url.indexOf("/",8);
  String host = url.substring(pos1+2,pos2);
  String path = url.substring(pos2);
  Serial.println("to wget(" + host + "," + path + "," + port + ")");
  wget(host, path, port, buff);
}

void wget(String &host, String &path, int port, char *buff) {
  // Blue LED while doing a network request.
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 255));
  neopixel.show();
  // Make WiFi request.
  WiFiClient client;
  client.stop();
  if (client.connect(host.c_str(), port)) {
    Serial.println("connected to server");
    // Make HTTP request.
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
          if((c != '\n') && (c != '\r')) {
            linelength++;
          }
        }
        lastc = c;
        bytes++;
      }
      // If the server is disconnected, stop the client.
      if (!client.connected()) {
        Serial.println("disconnecting from server.");
        client.stop();
        buff[capturepos] = '\0';
        Serial.println("captured " + String(capturepos) + " bytes");
        break;
      }
    }
  } else {
    Serial.println("problem connecting to " + host + ":" + String(port));
    buff[0] = '\0';
  }
  // Turn off LED.
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
}

/**
 * Get string length based on string and current gfx settings.
 */
int getStringLength(String s) {
  int16_t  x = 0, y = 0;
  uint16_t w, h;
  gfx.getTextBounds(s, 0, 0, &x, &y, &w, &h);
  return w + x;
}

/**
 * Show error on the display.
 */
void displayError(String str) {
  // Turn LED red when displaying an error.
  neopixel.setPixelColor(0, neopixel.Color(255, 0, 0));
  neopixel.show();
  // Display error.
  Serial.println(str);
  gfx.setTextColor(EPD_BLACK);
  gfx.powerUp();
  gfx.clearBuffer();
  gfx.setTextWrap(true);
  gfx.setCursor(10,60);
  gfx.setFont(&FreeSans12pt7b);
  gfx.print(str);
  gfx.display();
  // Turn off LED.
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
}

/**
 * Display date and city heading.
 */
void displayHeading(OpenWeatherMapCurrentData &owcdata) {
  String text;
  int x, y;
  int middleColumn = gfx.width() / 2;
  int dateHeight = 30;
  int cityHeight = 60;
  // Date.
  time_t local = owcdata.observationTime + owcdata.timezone;
  struct tm *timeinfo = gmtime(&local);
  char datestr[80];
  strftime(datestr,80,"%a, %b %d",timeinfo);
  text = String(datestr);
  gfx.setFont(&FreeSans18pt7b);
  x = middleColumn - (getStringLength(text) / 2);
  y = dateHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
  // City.
  text = owcdata.cityName;
  gfx.setFont(&FreeSansBold12pt7b);
  x = middleColumn - (getStringLength(text) / 2);
  y = cityHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
}

void displayTempIconAqi(AirQualityObservation &airQualityData, OpenWeatherMapCurrentData &owcdata) {
  // Set LED to Green while updating display.
  neopixel.setPixelColor(0, neopixel.Color(0, 255, 0));
  neopixel.show();
  // Power up the display.
  gfx.powerUp();
  gfx.clearBuffer();
  gfx.setTextColor(EPD_BLACK);
  // Display header.
  displayHeading(owcdata);

  // Prepare graphics variables.
  String text = "";
  int x, y;
  int dataPositionHeight = 120;
  int topLabelOffset = 40;
  int bottomLabelOffset = 16;
  int leftColumn = gfx.width() / 5;
  int middleColumn = gfx.width() / 2;
  int rightColumn = gfx.width() * 4 / 5;
  int sunriseSunsetTextHeight = 166;
  int sunriseSunsetIconHeight = 174;
  
  // Temperature Label.
  text = "Temp";
  gfx.setFont(&FreeSans9pt7b);
  x = leftColumn - getStringLength(text)/2;
  y = dataPositionHeight - topLabelOffset;
  gfx.setCursor(x, y);
  gfx.print(text);
  // Temperature.
  text = String((int)(owcdata.temp + .5));
  gfx.setFont(&FreeSansBold24pt7b);
  x = leftColumn - getStringLength(text)/2;
  y = dataPositionHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
  // Temperature degree symbol (not available as a font).
  text = String((int)(owcdata.temp + .5));
  x = leftColumn + getStringLength(text)/2 + 8;
  y = dataPositionHeight - 30;
  gfx.drawCircle(x, y, 4, EPD_BLACK);
  gfx.drawCircle(x, y, 3, EPD_BLACK);
  // Temperature Type.
  String tempUnits = "";
  if (OWM_UNITS == "imperial") {
    tempUnits = "F";
  } else if (OWM_UNITS == "metric") {
    tempUnits = "C";
  } else {
    tempUnits = "K";
  }
  text = tempUnits;
  gfx.setFont(&FreeSans9pt7b);
  x = leftColumn - getStringLength(text)/2;
  y = dataPositionHeight + bottomLabelOffset;
  gfx.setCursor(x, y);
  gfx.print(text);

  // Weather icon.
  text = owclient.getMeteoconIcon(owcdata.icon);
  gfx.setFont(&meteocons24pt7b);
  x = middleColumn - getStringLength(text)/2;
  y = dataPositionHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
  // Weather description.
  text = owcdata.main;
  gfx.setFont(&FreeSans9pt7b);
  x = middleColumn - getStringLength(text)/2;
  y = dataPositionHeight + bottomLabelOffset;
  gfx.setCursor(x, y);
  gfx.print(text);

  // AQI Label.
  text = "AQI";
  gfx.setFont(&FreeSans9pt7b);
  x = rightColumn - getStringLength(text)/2;
  y = dataPositionHeight - topLabelOffset;
  gfx.setCursor(x, y);
  gfx.print(text);
  // AQI Value.
  text = String((int)(airQualityData.AQI + .5));
  gfx.setFont(&FreeSansBold24pt7b);
  x = rightColumn - getStringLength(text)/2;
  y = dataPositionHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
  // AQI Type.
  text = airQualityData.ParameterName;
  gfx.setFont(&FreeSans9pt7b);
  x = rightColumn - getStringLength(text)/2;
  y = dataPositionHeight + bottomLabelOffset;
  gfx.setCursor(x, y);
  gfx.print(text);

  // Sunrise/sunset.
  char strbuff[80];
  time_t local;
  struct tm *timeinfo;
  // Sunrise.
  local = owcdata.sunrise + owcdata.timezone + 30; // Round to nearest minute.
  timeinfo = gmtime(&local);
  strftime(strbuff, 80, "%I", timeinfo);
  String datestr = String(atoi(strbuff));
  strftime(strbuff, 80, ":%M %p", timeinfo);
  datestr = datestr + String(strbuff) + " - ";
  // Sunset.
  local = owcdata.sunset + owcdata.timezone + 30; // Round to nearest minute.
  timeinfo = gmtime(&local);
  strftime(strbuff, 80, "%I", timeinfo);
  datestr = datestr + String(atoi(strbuff));
  strftime(strbuff, 80, ":%M %p", timeinfo);
  datestr = datestr + String(strbuff);
  // Display sunrise and sunset text.
  text = datestr;
  gfx.setFont(&FreeSans9pt7b);
  x = middleColumn - (getStringLength(text) / 2);
  y = sunriseSunsetTextHeight;
  gfx.setCursor(x, y);
  gfx.print(text);
  // Draw sunrise icon.
  // Sunrise icon is "B".
  String sunriseIcon = "B";
  gfx.setFont(&meteocons16pt7b);
  x = middleColumn - (getStringLength(text) / 2) - getStringLength(sunriseIcon) - 12;
  y = sunriseSunsetIconHeight;
  gfx.setCursor(x, y);
  gfx.print(sunriseIcon);
  // Draw sunset icon.
  // Sunset icon is "A".
  String sunsetIcon = "A";
  gfx.setFont(&meteocons16pt7b);
  x = middleColumn - (getStringLength(text) / 2) - getStringLength(sunsetIcon) - 12;
  y = sunriseSunsetIconHeight;
  gfx.setCursor(x, y);
  gfx.print(sunsetIcon);

  // Power down the display.
  gfx.display();
  gfx.powerDown();
  // Turn off LED.
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
}

void displayTemperature(AirQualityObservation &airQualityData, OpenWeatherMapCurrentData &owcdata) {
  // Set LED to Green while updating display.
  neopixel.setPixelColor(0, neopixel.Color(0, 255, 0));
  neopixel.show();
  // Power up the display.
  gfx.powerUp();
  gfx.clearBuffer();
  gfx.setTextColor(EPD_BLACK);
  // Display header.
  displayHeading(owcdata);

  // Prepare graphics variables.
  String text = "";
  int x, y;
  int weatherIconX = gfx.width() / 4;
  int weatherIconY = 156;
  int weatherDescriptionX = gfx.width() * 3 / 4;
  int weatherDescriptionY = 160;
  int weatherTemperatureX = gfx.width() * 3 / 4;
  int weatherTemperatureY = 130;

  // Weather icon.
  text = owclient.getMeteoconIcon(owcdata.icon);
  gfx.setFont(&meteocons48pt7b);
  x = weatherIconX - (getStringLength(text) / 2);
  y = weatherIconY;
  gfx.setCursor(x, y);
  gfx.print(text);

  // Temperature.
  gfx.setFont(&FreeSansBold24pt7b);
  int itemp = owcdata.temp + .5;
  text = String(itemp);
  x = weatherTemperatureX - (getStringLength(text) / 2);
  y = weatherTemperatureY;
  gfx.setCursor(x, y);
  gfx.print(text);
  gfx.setTextColor(EPD_BLACK);
  // Temperature degree symbol (not available as a font).
  x = weatherTemperatureX + (getStringLength(text) / 2) + 10;
  y = weatherTemperatureY - 26;
  gfx.drawCircle(x, y, 4, EPD_BLACK);
  gfx.drawCircle(x, y, 3, EPD_BLACK);

  // Weather description.
  text = owcdata.main;
  gfx.setFont(&FreeSans9pt7b);
  x = weatherDescriptionX - (getStringLength(text) / 2);
  y = weatherDescriptionY;
  gfx.setCursor(x, y);
  gfx.print(text);

  // Power down the display.
  gfx.display();
  gfx.powerDown();
  // Turn off LED.
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
}

void displayForecast(AirQualityObservation &airQualityData, OpenWeatherMapCurrentData &owcdata, OpenWeatherMapForecastData owfdata[], int count = 3)
{
  // Set LED to Green while updating display.
  neopixel.setPixelColor(0, neopixel.Color(0, 255, 0));
  neopixel.show();
  // Power up the display.
  gfx.powerUp();
  gfx.clearBuffer();
  gfx.setTextColor(EPD_BLACK);
  // Display header.
  displayHeading(owcdata);

  // Display forecast.
  int columnSeparation = gfx.width() / count;
  int timeHeight = 94;
  int dataPositionHeight = 130;
  String text;
  int x, y;
  for(int i = 0; i < count; i++) {
    // Date.
    time_t local = owfdata[i].observationTime + owcdata.timezone;
    struct tm *timeinfo = gmtime(&local);
    char strbuff[80];
    strftime(strbuff,80,"%I",timeinfo);
    text = String(atoi(strbuff));
    strftime(strbuff,80,"%p",timeinfo);
    // Convert AM/PM to lowercase.
    strbuff[0] = tolower(strbuff[0]);
    strbuff[1] = tolower(strbuff[1]);
    text = text + " " + String(strbuff);
    gfx.setFont(&FreeSans9pt7b);
    x = i * columnSeparation + (columnSeparation - getStringLength(text)) / 2;
    y = timeHeight;
    gfx.setCursor(x, y);
    gfx.print(text);

    // Weather icon.
    text = owclient.getMeteoconIcon(owfdata[i].icon);
    gfx.setFont(&meteocons20pt7b);
    x = i * columnSeparation + (columnSeparation - getStringLength(text)) / 2;
    y = dataPositionHeight;
    gfx.setCursor(x, y);
    gfx.print(text);

    // Weather description.
    text = owfdata[i].main;
    gfx.setFont(&FreeSans9pt7b);
    x = i * columnSeparation + (columnSeparation - getStringLength(text)) / 2;
    y = dataPositionHeight + 20;
    gfx.setCursor(x, y);
    gfx.print(text);

    // Temperature.
    int itemp = (int)(owfdata[i].temp + .5);
    text = String(itemp);
    gfx.setFont(&FreeSans9pt7b);
    x = i * columnSeparation + (columnSeparation - getStringLength(text)) / 2;
    y = dataPositionHeight + 38;
    gfx.setCursor(x, y);
    gfx.print(text);
    gfx.drawCircle(x + getStringLength(text) + 6, y-9, 3, EPD_BLACK);
    gfx.drawCircle(x + getStringLength(text) + 6, y-9, 2, EPD_BLACK);
  }

  gfx.display();
  gfx.powerDown();
  neopixel.setPixelColor(0, neopixel.Color(0, 0, 0));
  neopixel.show();
}

void setup() {
  neopixel.begin();
  neopixel.show();
  gfx.begin();
  Serial.println("ePaper display initialized");
  gfx.setRotation(2);
  gfx.setTextWrap(false);
}

void loop() {
  static uint32_t timer = millis();
  static uint8_t lastbutton = 1;
  static bool firsttime = true;

  int button = readButtons();

  // Update weather data at specified interval or when button 4 is pressed.
  if ((millis() >= (timer + 1000*60*UPDATE_INTERVAL)) || (button == 4) || firsttime) {
    char data[4000];
    Serial.println("getting weather data");
    firsttime = false;
    timer = millis();

    // Connect to WiFi.
    int retry = 6;
    while(!wifi_connect()) {
      delay(5000);
      retry--;
      if (retry < 0) {
        displayError("Can not connect to WiFi, press reset to restart");
        return;
      }
    }
    // Update weather data.
    String urlc = owclient.buildUrlCurrent();
    Serial.println(urlc);
    retry = 6;
    do
    {
      retry--;
      wget(urlc, 80, data);
    } while((strlen(data) == 0) && (retry >= 0));
    if (strlen(data) == 0) {
      Serial.println("Can not get weather data, press reset to restart");
      displayError("Can not get weather data, press reset to restart");
    } else {
      Serial.print("Weather Data: ");
      Serial.println(data);
    }
    retry = 6;
    while (!owclient.updateCurrent(owcdata, data)) {
      retry--;
      if (retry < 0) {
        displayError(owclient.getError());
        return;
      }
      delay(5000);
    }
    // Update air quality data.
    bool ozone = false;
    retry = 6;
    do
    {
      retry--;
      String url_quality = airQualityApi.buildUrlCurrent(ozone);
      Serial.println(ozone ? "Requesting Ozone Air Quality Data" : "Requesting PM2.5 Air Quality Data");
      Serial.println(url_quality);
      wget(url_quality, 80, data);
      if (strlen(data) == 0) {
        Serial.print("Switch PM2.5 / ozone... ");
        ozone = !ozone; // Try the other data type.
      }
    } while ((strlen(data) == 0) && retry >= 0);
    if (strlen(data) == 0) {
      Serial.println("Can not get air quality data, press reset to restart");
      displayError("Can not get air quality data, press reset to restart");
    } else {
      Serial.print("Air Quality Data: ");
      Serial.println(data);
    }
    delay(1000);
    retry = 2;
    while (!airQualityApi.updateCurrent(airQualityData, data)) {
      retry--;
      if (retry < 0) {
        displayError(airQualityApi.getError());
        return;
      }
      delay(5000);
    }
    // Update forecast data.
    String urlf = owclient.buildUrlForecast(OWM_API_KEY, "San Francisco,CA,US");
    Serial.println(urlf);
    wget(urlf, 80, data);
    Serial.print("Forecast Data: ");
    Serial.println(data);
    if (!owclient.updateForecast(owfdata[0], data, 0)) {
      displayError(owclient.getError());
      return;
    }
    if (!owclient.updateForecast(owfdata[1], data, 2)) {
      displayError(owclient.getError());
      return;
    }
    if (!owclient.updateForecast(owfdata[2], data, 4)) {
      displayError(owclient.getError());
      return;
    }
    switch (lastbutton) {
      case 1:
        displayTempIconAqi(airQualityData, owcdata);
        break;
      case 2:
        displayTemperature(airQualityData, owcdata);
        break;
      case 3:
        displayForecast(airQualityData,owcdata,owfdata,3);
        break;
    }
  }

  // If no buttons are pressed, we are done with the loop.
  if (button == 0) {
    return;
  }
  // Handle the button press.
  Serial.print("Button "); Serial.print(button); Serial.println(" pressed");
  if (button == 1) {
    displayTempIconAqi(airQualityData, owcdata);
    lastbutton = button;
  }
  if (button == 2) {
    displayTemperature(airQualityData,owcdata);
    lastbutton = button;
  }
  if (button == 3) {
    displayForecast(airQualityData,owcdata,owfdata,3);
    lastbutton = button;
  }
  // Wait until button is released.
  while (readButtons()) {
    delay(10);
  }
}
