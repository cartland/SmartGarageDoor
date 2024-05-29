# Smart Garage Door
**Author:** Christopher Cartland

DIY smart garage door system built with Arduino, Firebase, and Android.

## Table of Contents
* [Microcontroller](#microcontroller): Adafruit HUZZAH32 - ESP32 Feather
* [Sensor](#sensor): AdafruitMagnetic contact switch (door sensor)
* [Button](#button): Adafruit Non-Latching Mini Relay FeatherWing
* [Server](#server): Firebase Functions
* [Android](#android): Android

## Microcontroller
* [Adafruit HUZZAH32 - ESP32 Feather](https://www.adafruit.com/product/3405)

**I used 2 WiFi capable boards for this project**:
* 1 board for 2 [door sensors](#sensor)
* 1 board for 1 [garage button](#button)

I also did some tests with
[Adafruit Metro M4 Express AirLift (WiFi) - Lite](https://www.adafruit.com/product/4000),
but I mostly used the Feather because it is smaller.

To build for the Metro M4, two flags need to change. The Feather is selected by default. 
```
#define USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER true
#define USE_ADAFRUIT_METRO_M4_EXPRESS_AIRLIFT false
```
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/secrets.h#L29-L35

The main difference between the boards is that different WiFi libraries are used.
```
#if USE_WIFI_NINA
  return WiFiNINASetup(wifiSSID, wifiPassword);
#endif
#if USE_MULTI_WIFI
  return wifiMultiSetup(wifiSSID, wifiPassword);
#endif
```
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/WiFiGet.cpp#L19-L26

## Sensor
* [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)

**I use 2 door sensors**. I assemble the physical system so one circuit is
"closed" when the garage door is "closed.
The other circuit is "closed" when the garage door is "open".
Having 2 sensors allows the server to detect when the door is
"open", "closed", or something unexpected.

**Sensor A and Sensor B**: The microcontroller reads the signals and sends the status to the server as `SENSOR_A` and `SENSOR_B`.
The client does not make a distinction about the meaning of the sensors.
The meaning of each sensor is defined by the server.

**Debounce**: Raw sensor data is noisy.
When the sensor value changes, it often flickers before settling on the new value.
I wrote a debouncer that makes sure the signal is stable for `DEBOUNCE_MILLIS` (50 milliseconds).
`debounceUpdate()` reads the current input value and checks to see if it has changed.
When there is a change, the time is reset to the current time.
If the value has not changed for more than the required duration, the state is set to the value.
If the state changes, the function returns `true`. If the state has not changed, the function returns `false`.
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/Debouncer.cpp#L19-L39

When the state changes, the client sends the updated server information to the server.
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/DoorSensor.ino#L115-L122

If the client has not reported the value to the server for `HEARTBEAT_INTERVAL` (10 minutes),
then the client sends a heartbeat update to the server with both sensor values.
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/DoorSensor.ino#L234-L254

## Button
* [Button](#button): Adafruit Non-Latching Mini Relay FeatherWing

## Server
* [Server](#server): Firebase Functions

## Android
* [Android](#android): Android

## License
This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.

---

# Acknowledgments
Below are some of the main resources that helped me create this project.
Adafruit has amazing products and educational materials.

## Feather
* https://learn.adafruit.com/adafruit-huzzah32-esp32-feather/using-with-arduino-ide
* https://github.com/espressif/arduino-esp32/blob/master/docs/arduino-ide/boards_manager.md
* https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers

## Arduino IDE
* Board
  * https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
  * Board: ESP32 -> Adafruit ESP32 Feather
* Port: cu.SLAB_USBtoUART

## AirLift
* https://learn.adafruit.com/adafruit-metro-m4-express-airlift-wifi/update-the-uf2-bootloader

## ESP32
* Update firmware https://learn.adafruit.com/upgrading-esp32-firmware/overview
  * https://learn.adafruit.com/upgrading-esp32-firmware/upgrade-an-airlift-all-in-one-board
 
# Reset Arduino in Software
* https://www.instructables.com/two-ways-to-reset-arduino-in-software/
