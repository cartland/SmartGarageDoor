---
category: reference
status: active
last_verified: 2026-04-25
---
# Smart Garage Door
**Author:** Christopher Cartland

Smart garage door system built with ESP32 microcontroller, Firebase "serverless" backend, and Android app.

My original firmware implementation was based on Arduino (2021). I've since migrated to ESP32 and FreeRTOS (2024).

* 2021: Arduino on Adafruit HUZZAH32 - ESP32 Feather
* 2024: FreeRTOS on ESP32-DevKitC ESP32-WROOM-32U Development Board

<img src="AndroidGarage/screenshots/home_closed.png" width="200" alt="Garage app home screen with the door closed and the button ready to press"> <img src="AndroidGarage/screenshots/history.png" width="200" alt="Garage history screen with recent door events">

## Getting Started

Pick the path that matches what you came here for:

| I want to… | Start here |
|---|---|
| **Contribute to the Android app** | [`AndroidGarage/README.md`](AndroidGarage/README.md) → [`AndroidGarage/docs/ARCHITECTURE.md`](AndroidGarage/docs/ARCHITECTURE.md) |
| **Deploy or operate the Firebase server** | [`FirebaseServer/README.md`](FirebaseServer/README.md) → [`docs/FIREBASE_DEPLOY_SETUP.md`](docs/FIREBASE_DEPLOY_SETUP.md) |
| **Understand the system as a whole** | This page → [`docs/AGENTS.md`](docs/AGENTS.md) → [`AndroidGarage/docs/ARCHITECTURE.md`](AndroidGarage/docs/ARCHITECTURE.md) |
| **Build the ESP32 firmware** | [`GarageFirmware_ESP32/README.md`](GarageFirmware_ESP32/README.md) (FreeRTOS, current) or [`Arduino_ESP32/README.md`](Arduino_ESP32/README.md) (legacy 2021) |
| **Work as an AI agent in this repo** | [`CLAUDE.md`](CLAUDE.md) → [`docs/AGENTS.md`](docs/AGENTS.md) |

## Repository Layout

```
SmartGarageDoor/
├── AndroidGarage/         Android app (Kotlin, Compose, KMP-bound) — see AndroidGarage/README.md
├── FirebaseServer/        Cloud Functions backend (TypeScript) — see FirebaseServer/README.md
├── GarageFirmware_ESP32/  Current ESP32 firmware (FreeRTOS + ESP-IDF)
├── Arduino_ESP32/         Legacy 2021 Arduino firmware (kept as reference)
├── docs/                  Cross-cutting docs (AGENTS contract, Firebase ops, architecture)
└── scripts/               Validation, release, and helper scripts
```

## Table of Contents
* [Firmware](#firmware): ESP32 Firmware (FreeRTOS and Legacy Arduino)
* [Android](#android): Android app
* [Server](#server): Firebase Functions
* [Limitations](#limitations)

<!-- not-actively-maintained: Firmware section (2024 FreeRTOS + 2021 Arduino) is reference-only. Active doc maintenance focuses on Android + Firebase server. See GarageFirmware_ESP32/README.md and Arduino_ESP32/README.md for firmware-specific guidance. -->

## Firmware

### 2024 Implementation (ESP32 FreeRTOS)
Located in `GarageFirmware_ESP32/`, this version offers improved reliability through:
* FreeRTOS task management
* ESP-IDF native WiFi stack
* Structured project architecture with components
* Configurable fake implementations for testing
* Menuconfig for WiFi and server settings

**Hardware**:
* [ESP32-DevKitC ESP32-WROOM-32U Development Board](https://www.amazon.com/dp/B09KLS2YB3)
* [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)
* [Adafruit Non-Latching Mini Relay FeatherWing](https://www.adafruit.com/product/2895)


Key components:
* `garage_hal`: Hardware abstraction for sensors and button
* `door_sensors`: Manages door position sensors with debouncing
* `button_token`: Handles button press protocol with server
* `wifi_connector`: Manages WiFi connectivity
* `garage_http_client`: Handles HTTPS communication

### 2021 Implementation (Arduino ESP32)
The original Arduino-based implementation is preserved in `Arduino_ESP32/`. While still functional, it is considered legacy code.
Configuring the legacy code is more difficult because you need to manually change the WiFi credentials in the code.
This is one of the reasons I migrated to FreeRTOS with ESP-IDF, which supports menuconfig to set WiFi credentials.
You can browse the Arduino code in `Arduino_ESP32/`.

Contains:
* `DoorSensor/`: Code for monitoring door position
* `RemoteButton/`: Code for controlling the garage door button

**Legacy Hardware**:
* [Adafruit HUZZAH32 - ESP32 Feather](https://www.adafruit.com/product/3405)
* [Adafruit Magnetic contact switch (door sensor)](https://www.adafruit.com/product/375)
* [Adafruit Non-Latching Mini Relay FeatherWing](https://www.adafruit.com/product/2895)

## Android

**Platform**: Android (Kotlin + Jetpack Compose, kotlin-inject DI, Ktor + Room, KMP-bound)

The app is a thin client over the server. Key behaviors:

* **Door state** — displays the door status as interpreted by the server. The app does not interpret raw sensor data.
* **Push the garage button** — tap-to-confirm interaction; on second tap, sends a command to the server.
* **Authentication** — Google Sign-In via Credential Manager, then Firebase Auth ID token. The server keeps an allow-list of authorized accounts.
* **Two ID tokens** — Google ID token (for Sign-In) and Firebase ID token (sent to the server as `X-AuthTokenGoogle`). Only the Firebase token is accepted by the server.

For the full architecture, module graph, and ADRs, see [`AndroidGarage/docs/ARCHITECTURE.md`](AndroidGarage/docs/ARCHITECTURE.md) and [`AndroidGarage/docs/DECISIONS.md`](AndroidGarage/docs/DECISIONS.md).

## Server

**Platform**: Firebase Cloud Functions (TypeScript on Node 22)

**All critical logic lives on the server.** Clients (firmware and app) are kept simple so that features can ship without client updates.

Client responsibilities are deliberately minimal:
* **Sensors** — report raw sensor values to the server on change. No interpretation.
* **Button** — push the relay when the server sends a command. No authorization decisions.
* **Android** — display server-computed state. Send button-press requests; do not interpret sensor data.

Server responsibilities (entry points exported from [`FirebaseServer/src/index.ts`](FirebaseServer/src/index.ts)):
* **Store all client requests** — every sensor update is persisted.
* **Interpret sensor data** — convert raw signal input into door events (open / closed / error).
* **Serve the current event** — clients query for the latest interpreted state.
* **Push the remote button** — implements the Button Ack Token Protocol so a device crash can't replay a press.
* **Authorize Android button presses** — Firebase ID token + email allow-list.
* **Check for door errors every minute** — e.g., door stuck halfway.
* **Check for open doors every 5 minutes** — send an FCM notification if a door has been open more than 15 minutes.
* **Enforce data retention** — scheduled cleanup of old event data.

For the full operational guide (deploy, rollback, monitoring, GCP setup), see [`docs/FIREBASE_DEPLOY_SETUP.md`](docs/FIREBASE_DEPLOY_SETUP.md).

## Limitations
* **Root CA Expiration**: The server uses a hard-coded root CA that expires in 2036.
* **Polling**: Polling is more expensive and has high latency.
  * I could improve latency with a server-only change. The idea is to wait during
    the polling period to see if a button press should happen,
    and respond immediately to the client. This requires testing to make sure
    the client doesn't timeout. I haven't prioritized this because the polling
    only costs about $1 per month, which is cheap enough for my use case.
* **Button press race condition**: If the device crashes immediately
  after successfully pressing the button but before sending the `buttonAckToken`
  to the server, the client might push the button more than once.
* **Reset recovery in FreeRTOS**: The FreeRTOS implementation hasn't been tested
  with a hard reset. The Arduino implementation has been tested for 3.5 years,
  and it contains a physical reset wire that is triggered when there is an issue.
  I need to implement recovery logic for the FreeRTOS implementation.

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
 
## Reset Arduino in Software
* https://www.instructables.com/two-ways-to-reset-arduino-in-software/
