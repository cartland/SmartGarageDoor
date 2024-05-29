# Smart Garage Door
**Author:** Christopher Cartland

DIY smart garage door system built with Arduino, Firebase, and Android.

## Table of Contents
* [Microcontroller](#microcontroller): Adafruit HUZZAH32 - ESP32 Feather
* [Sensor](#sensor): Adafruit Magnetic contact switch (door sensor)
* [Button](#button): Adafruit Non-Latching Mini Relay FeatherWing
* [Server](#server): Firebase Functions
* [Android](#android): Android app

## Microcontroller
* **Hardware**: [Adafruit HUZZAH32 - ESP32 Feather](https://www.adafruit.com/product/3405)

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
* **Hardware**: [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)

**I use 2 door sensors with 1 microcontroller**. I assemble the physical system so one circuit is
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
Based on my experiments, 50 milliseconds removed almost all errors and still felt fast.
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

**Reset Pin - Reboot After Error**: If there is any error (usually caused by a glitch in the WiFi connection),
the client will reboot itself. This is implemented with a physical wire that drives a reset pin `LOW`
to trigger a device reset. To avoid accidentally turning off the system during client boot,
we set the pin `HIGH` before doing any other setup functions.
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/DoorSensor.ino#L141-L146
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/DoorSensor/DoorSensor.ino#L72-L76

**Sensor Reliability**: The sensors have been running for 3 years without code changes.
The sensors are physically installed on the ceiling, and I haven't needed to use the ladder since installation.
The reset pin has eventually been able to recover from all issues!

## Button
* **Hardware**:
  * [Adafruit Non-Latching Mini Relay FeatherWing](https://www.adafruit.com/product/2895)
  * Spare garage remote

**I use 1 relay with 1 microcontroller to activate a garage remote button**.
The primary job of this device is to listen to the "push button" command from the server,
and then "push the button."

**Physical Configuration**: I bought a regular garage remote that works with my garage.
I dismantled the case to find the electrical button. Using an ohmmeter,
I figured out which pins are connected when the button is pressed.
I soldered wires to the two contact points and connected them to the relay.
Whenever the button needs to be pressed, it flips the relay
for `PUSH_REMOTE_BUTTON_DURATION_MILLIS` (500 milliseconds).
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/RemoteButton/RemoteButton.ino#L35-L41

**Update Frequency**: The client polls the server every 5 seconds.
This means the garage remote has a latency of **0-5 seconds + network latency**,
and the client polls the server about **17K times per day**: 
_(86400 seconds / day) / (5 seconds / poll) = 17280 polls_.
Higher frequency increases the costs.
Lower frequency makes the button feel unresponsive.

**Cost Update**: The system has been running 24/7 for 3 years
(April 2021 to May 2024 as of this update).
The monthly Firebase server cost is approximately $1.50 USD.
This cost is low enough for a personal project (1 garage)
that I haven't spent time on further optimizations.

**Button Ack Token Protocol**: One of the most important requirements of pushing the garage door button
is that **the button must not be pressed more than once in response to a server command**.
The client and server need a protocol to ensure that the client only pushes the button at most once.
**The client and server solve this problem with a "Button Ack Token"** (button acknowledgment token).

* **First request**: Client sends the first request with any `buttonAckToken`.
* **Server remembers**: Server reads the `buttonAckToken` to determine if the client needs to push the button.
  * If the server wants the client to push the button, respond with a **new token**.
  * If the server does not want the client to push the button, respond with the **same token**.
* **Client remembers**: Client reads the token and remembers the `buttonAckToken` for the next request.
  * If the token is different, push the button.
  * If the token is the same, do not push the button.
* **Client ack**: Client pings the server every 500 milliseconds with the most recent `buttonAckToken`
(sending the recent value is the "acknowledgment" of the latest button press).

https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/RemoteButton/RemoteButton.ino#L107-L108
https://github.com/cartland/SmartGarageDoor/blob/98711935828d8e87f669eef987a58a4e491bca90/RemoteButton/RemoteButton.ino#L75-L78

**Button Ack Token During Network Disruptions**:
The `buttonAckToken` protocol is resilient to some network disruptions and client reboots.
* **Client request unable to reach server**: The client will repeat the request without pushing the button.
* **Server response unable to reach client**: The client will repeat the request without pushing the button.
The server sees multiple outdated requests from the client, which means the client is
failing to acknowledge the button. The server must keep responding with the same `buttonAckToken`.
  * **Important**: If the server sends a new token every time the client sends an outdated token,
    there is a risk of the client interpreting these as multiple button press requests.
    When the server wants the client to push the button, the server should pick a new token
    and then **the server should consistently send the same token until it is acknowledged**.

## Server
* **Platform**: Firebase Functions

## Android
* **Platform**: Android

## Limitations
* **Hard-coded WiFi**: If my WiFi password changes, I will need to reprogram the Arduino boards.
* **Polling**: Polling is more expensive and has high latency.
  * I could improve latency with a server-only change. The idea is to wait during
    the polling period to see if a button press should happen,
    and respond immediately to the client. This requires testing to make sure
    the client doesn't timeout, and to implement this without skyrocketing CPU costs.
* **Button press race condition**: If the device crashes in the 500 ms
  after successfully pressing the button but before sending the `buttonAckToken`
  to the server, the client might push the button more than once.

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
