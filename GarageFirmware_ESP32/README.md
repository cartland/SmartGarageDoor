# Smart Garage Door - GarageFirmware_ESP32

## Overview
This project is for the firmware of a smart garage door opener using an ESP32 microcontroller. The firmware allows you to control and monitor your garage door remotely. Tasks are scheduled with FreeRTOS.

## Features
- Open and close the garage door remotely
- Monitor the status of the garage door (open/closed)
- Secure communication over HTTPS

## Physical Requirements
- ESP32 (developed on ESP32-DevKitC ESP32-WROOM-32U Core Board)
- Garage door opener remote
- Magnetic reed sensors
- Wi-Fi network

## Physical Wiring
- 2x Magnetic Reed Sensors with pull-up resistors (LOW 0 == circuit closed)
    - Sensor A: If the value is LOW, the door is confirmed to be closed
    - Sensor B: If the value is LOW, the door is confirmed to be open
    - If the door is in between sensors, the value of both will be HIGH
    - If both sensors are LOW, something is wrong with the sensors
- 1x Button: HIGH 1 == push, LOW 0 == release
    - The microcontroller is connected to a relay
    - When the GPIO is HIGH, the relay connects two wires together, which "pushes" the button
    - The wires are attached to the terminals of a momentary switch on the garage door opener
    - The garage door opener remains functional -- we just added 2 wires to the remote

## Tool Requirements
- ESP-IDF https://docs.espressif.com/projects/esp-idf/en/stable/esp32/get-started/index.html

## Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/cartland/SmartGarageDoor
    ```
1. Navigate to the firmware directory:
    ```sh
    cd SmartGarageDoor/GarageFirmware_ESP32
    ```
1. Configure WiFi credentials and HTTPS endpoints
    ```sh
    idf.py menuconfig
    ```
1. Configure GPIO pins in `components/garage_hal/src/garage_hal.c`
    ```c
    #define SENSOR_A_GPIO GPIO_NUM_25
    #define SENSOR_B_GPIO GPIO_NUM_26
    #define BUTTON_GPIO GPIO_NUM_27
    ```
1. If you don't have a server, use fakes by modifying `garage_config/garage_config.h`
    ```c
    // #define CONFIG_USE_FAKE_GARAGE_SERVER 1
    // #define CONFIG_USE_FAKE_GARAGE_HAL 1
    // #define CONFIG_USE_FAKE_BUTTON_TOKEN 1
    ```
1. Build
    ```sh
    idf.py build
    ```
1. Flash
    ```sh
    idf.py flash
    ```
1. Monitor
    ```sh
    idf.py monitor
    ```

## Project Structure
```sh
├── CMakeLists.txt
├── README.md
├── components
│   ├── button_token
│   ├── door_sensors
│   ├── garage_config
│   ├── garage_hal
│   ├── garage_http_client
│   └── wifi_connector
├── main
│   ├── CMakeLists.txt
│   ├── Kconfig.projbuild
│   └── main.c
├── sdkconfig.defaults
└── setup_idf_env.ps1
```

### garage_config

Contains configuration options and settings for the project:

- Feature flags for using fake implementations
- Constants for string sizes

### garage_hal

Hardware abstraction layer for garage door operations:

- Set GPIO pins
- GPIO control for sensors and button
- Hardware initialization
- Direct hardware access functions

### door_sensors

Manages the door position sensors:

- Reading sensor states from 2 magnetic reed sensors (0 == circuit closed)
- Debouncing sensor inputs

### button_token

- Negotiates with the server to push the button
- The server will publish a new button token for each push
- The client will acknowledge the token to the server
- The client will only push the button once for each token (until the token changes)

### garage_http_client

Handles HTTP communication with the server:

- Making HTTPS requests (check root CA expiry, expected to expire in 2036)

### wifi_connector

Manages WiFi connectivity:

- WiFi initialization
- Set WiFi credentials in idf.py menuconfig

## Tasks

FreeRTOS tasks are created in `main.c`:

```c
xTaskCreate(log_hello, "log_hello", 2048, NULL, 5, NULL);
xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
xTaskCreate(upload_sensors, "upload_sensors", 4096, NULL, 5, NULL);
xTaskCreate(download_button_commands, "download_button", 8192, NULL, 5, NULL);
xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
```

### log_hello

Logs a hello message to the console.

### read_sensors

Reads the sensor states and puts changes in a queue.

### upload_sensors

Uploads the sensor states to the server.

### download_button_commands

Downloads button commands from the server and puts commands in a queue.

### push_button

Pushes the button.

## Design Choices

- Prefer static stack allocation to heap allocation
- Prefer simple library components over fewer components
- Prefer simple tasks over fewer tasks
