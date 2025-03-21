# Smart Garage Door - Legacy Arduino Implementation (2021)

## Overview
This is the original Arduino-based implementation of the smart garage door controller, using ESP32. While still functional, it has been superseded by the FreeRTOS implementation in `GarageFirmware_ESP32/`.

## Components
The system uses two separate ESP32 boards:
* `DoorSensor/`: Monitors garage door position using two magnetic reed switches
* `RemoteButton/`: Controls the garage door button via relay

## Hardware Requirements
* [Adafruit HUZZAH32 - ESP32 Feather](https://www.adafruit.com/product/3405)
* [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)
* [Adafruit Non-Latching Mini Relay FeatherWing](https://www.adafruit.com/product/2895)

## Features
* Door position monitoring with debouncing
* Remote button control with server token system
* Battery voltage monitoring (using pin A13)
* Error recovery with physical reset wire (pin 27)
* Status indication via LED pins
* Heartbeat updates every 10 minutes

## Configuration
1. Create a `secrets.h` file in both `DoorSensor/` and `RemoteButton/` directories
2. Configure your WiFi credentials and server settings:

```cpp
#define WIFI_SSID "your_ssid"
#define WIFI_PASSWORD "your_password"
#define USE_ADAFRUIT_HUZZAH32_ESP32_FEATHER 1
#define USE_SENSOR_A 1
#define USE_SENSOR_B 1
```

## Pin Configuration
### DoorSensor
* Sensor A: GPIO 14
* Sensor B: GPIO 32
* LED A: GPIO 15
* LED B: GPIO 33
* Reset: GPIO 27
* Battery Voltage: A13 (GPIO 35)

### RemoteButton
* Button: A1
* Reset: GPIO 27

## Legacy Status
This implementation ran reliably from 2021-2024 but has been replaced by the FreeRTOS implementation. Key differences from current version:
* Manual WiFi credential configuration (vs menuconfig)
* Two separate boards (vs single board)
* Hardware reset wire (vs software recovery)
* Arduino framework (vs ESP-IDF/FreeRTOS)
