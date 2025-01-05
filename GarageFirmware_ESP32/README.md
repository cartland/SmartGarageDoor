# Smart Garage Door - GarageFirmware_ESP32

## Overview
This is the current firmware implementation (2024) for the smart garage door controller using ESP32 with FreeRTOS. The firmware allows you to control and monitor your garage door remotely through a secure HTTPS connection.

## Features
- Open and close the garage door remotely
- Monitor the status of the garage door (open/closed)
- Secure communication over HTTPS
- FreeRTOS task management
- ESP-IDF native WiFi stack
- Configurable fake implementations for testing
- Menuconfig for WiFi and server settings

## Physical Requirements
- ESP32 (developed on ESP32-DevKitC ESP32-WROOM-32U Core Board)
    - https://www.amazon.com/HiLetgo-ESP32-DevKitC-ESP32-WROOM-32U-ESP-WROOM-32U-Development/dp/B09KLS2YB3
    - https://www.amazon.com/Freenove-Breakout-ESP32-S3-Terminal-Outputs/dp/B0CD2512JV
- [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)
    - 2x sensors needed: one for open position, one for closed position
- [Adafruit Non-Latching Mini Relay FeatherWing](https://www.adafruit.com/product/2895)
    - Used to control the garage door button
- Wi-Fi network

## Physical Wiring
- 2x Magnetic Reed Sensors with pull-up resistors (LOW 0 == circuit closed)
    - Sensor A: If the value is LOW, the door is confirmed to be closed
    - Sensor B: If the value is LOW, the door is confirmed to be open
    - If the door is in between sensors, the value of both will be HIGH
    - If both sensors are LOW, something is wrong with the sensors
- 1x Relay Button Control:
    - The relay connects to the garage door opener's existing button terminals
    - When GPIO goes HIGH, relay closes, simulating a button press
    - The garage door opener's original button remains functional

## Tool Requirements
- ESP-IDF https://docs.espressif.com/projects/esp-idf/en/stable/esp32/get-started/index.html

## Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/cartland/SmartGarageDoor
    ```
2. Navigate to the firmware directory:
    ```sh
    cd SmartGarageDoor/GarageFirmware_ESP32
    ```
3. Configure WiFi credentials and HTTPS endpoints:
    ```sh
    idf.py menuconfig
    ```
4. Configure GPIO pins in `components/garage_hal/src/garage_hal.c`:
    ```c
    #define SENSOR_A_GPIO GPIO_NUM_25
    #define SENSOR_B_GPIO GPIO_NUM_26
    #define BUTTON_GPIO GPIO_NUM_27
    ```
5. If you don't have a server, use fakes by modifying `garage_config/garage_config.h`:
    ```c
    // #define CONFIG_USE_FAKE_GARAGE_SERVER 1
    // #define CONFIG_USE_FAKE_GARAGE_HAL 1
    // #define CONFIG_USE_FAKE_BUTTON_TOKEN 1
    ```
6. Build and flash:
    ```sh
    idf.py build
    idf.py flash
    idf.py monitor
    ```

## Project Structure
```sh
├── CMakeLists.txt
├── README.md
├── components
│   ├── button_token      # Button press protocol with server
│   ├── door_sensors      # Door position sensor management
│   ├── garage_config     # Configuration options
│   ├── garage_hal        # Hardware abstraction layer
│   ├── garage_http_client # HTTPS communication
│   └── wifi_connector    # WiFi connectivity management
├── main
│   ├── CMakeLists.txt
│   ├── Kconfig.projbuild
│   └── main.c
├── sdkconfig.defaults
└── setup_idf_env.ps1
```

## Tasks
FreeRTOS tasks in `main.c`:
```c
xTaskCreate(log_hello, "log_hello", 2048, NULL, 5, NULL);
xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
xTaskCreate(upload_sensors, "upload_sensors", 4096, NULL, 5, NULL);
xTaskCreate(download_button_commands, "download_button", 8192, NULL, 5, NULL);
xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
```

## Design Choices
- Prefer static stack allocation to heap allocation
- Prefer simple library components over fewer components
- Prefer simple tasks over fewer tasks
