# CommandHub

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2026-green.svg?logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/Web%20API-see%20API.md-orange.svg)](API.md)
[![Build](https://img.shields.io/badge/AGP-9.0.1-blue.svg)](gradle/libs.versions.toml)

#### Video Demo: https://youtu.be/YOUR_VIDEO_URL
*(placeholder — replace with the real link before sharing)*

CommandHub turns an Android phone into a modular control station for robotics and
embedded projects. It exposes the phone's cameras, sensors, USB serial, Bluetooth, and
TCP links through both a Jetpack Compose dashboard and a built-in **HTTP + WebSocket
server** on your local network.

---

## Table of Contents

- [Why I Built CommandHub](#why-i-built-commandhub)
- [Description](#description)
- [Quick Start](#quick-start)
- [Features](#features)
- [Web API](#web-api)
- [Technologies Used](#technologies-used)
- [Project Structure](#project-structure)
- [Design Decisions](#design-decisions)
- [Important Files](#important-files)
- [Building the Project](#building-the-project)
- [Challenges](#challenges)
- [Future Improvements](#future-improvements)
- [What I Learned](#what-i-learned)
- [AI Usage](#ai-usage)
- [Conclusion](#conclusion)

---

## Why I Built CommandHub

The idea for CommandHub came from two observations.

First, modern Android phones are remarkably capable computers that already contain a
powerful collection of hardware, including high-resolution cameras, Bluetooth, Wi-Fi,
USB OTG support, GPS, accelerometers, gyroscopes, magnetometers, and many other sensors.
Despite this, many perfectly functional Android devices end up sitting unused in drawers
after being replaced.

Second, building a robotics or embedded systems project often requires purchasing a
Raspberry Pi together with additional hardware such as a camera module, wireless
communication adapters, GPS, and various sensors. Even purchasing a used Android phone
can often cost less than buying these components separately, while already including
most of them in a single compact device.

This made me wonder: instead of buying dedicated hardware, why not turn an Android phone
into the hardware?

Since many people already own an unused Android device, or can obtain one inexpensively,
I wanted to explore using it as an affordable yet capable robotics companion. Combined
with applications such as **Termux**, an Android phone can run Linux tools, scripts, and
development utilities locally, acting kinda like a Raspberry Pi, while CommandHub
provides access to its communication interfaces, sensors, camera, and networking
capabilities. The phone can also expose its embedded web server, allowing it to be
monitored and controlled remotely from another computer through a web browser.

During development I also found myself constantly switching between multiple
applications: one for USB serial communication, another for Bluetooth, another for
viewing camera streams, another for monitoring sensors, and sometimes a browser for
accessing a local web server. This workflow quickly became inconvenient and difficult
to manage.

CommandHub was created to solve both of these problems. It transforms an Android phone
into a modular control station by bringing communication, monitoring, networking, and
hardware interfaces together in a single extensible application.

## Description

CommandHub is an Android application designed to serve as a central control station for
robotics, embedded systems, and hardware development projects. It combines multiple
communication methods, hardware interfaces, and monitoring tools into a single
customizable application, allowing developers to interact with microcontrollers and
other devices without constantly switching between separate applications.

Rather than creating a fixed interface, CommandHub was designed from the beginning to be
extensible. Dashboard layouts are generated from configurable JSON files, allowing
different interfaces to be loaded without modifying the application's source code. The
application can be used entirely on the Android device, alongside tools such as Termux,
or remotely from another computer through its embedded Ktor web server and companion
desktop discovery utility. The long-term goal is to create a flexible platform that
adapts to many different robotics and embedded projects instead of being tied to a
single device or workflow.

<img width="829" height="372" alt="Screenshot_20260730_202411" src="https://github.com/user-attachments/assets/f8680d6b-0ff5-4c04-815a-982d2f359bfe" />

---

## Quick Start

1. **Clone and build**

   ```bash
   git clone https://github.com/Jedimig08/CommandHub.git
   cd CommandHub
   ```

2. Open the project in **Android Studio Meerkat or newer** and let Gradle sync.

3. **Run on a physical Android device.** Emulators lack the USB, Bluetooth, and camera
   hardware this project is built around, so a real phone is required.

4. Grant the runtime permissions when prompted (camera, nearby devices, location,
   notification).

5. From a computer on the same Wi-Fi network, either:

   ```bash
   # Auto-discover the phone and open the dashboard
   pip install zeroconf
   python pc_files/android_domain.py
   ```

   or browse to `http://<phone-ip>:8080/` yourself.

6. Read **[API.md](API.md)** for the full endpoint reference.

---

## Features

### Dashboard

- Modular dashboard built with **Jetpack Compose**
- JSON-based layout configuration (`app/src/main/assets/layout.json`)
- Multiple dashboard pages with swipe navigation
- Reorderable widget grid with configurable spans and aspect ratios
- Preset layouts for the same widget slots
- Macro button grids (configurable from the JSON layout) for sending canned commands to
  a connected UART or Bluetooth device
- Material Design 3 UI

### Connectivity

- **USB Serial (UART)** — auto-connects on launch at 115200 baud, 8N1 (Arduino, ESP32, …)
- **Bluetooth Classic** — paired-device picker, reconnect loop, bidirectional terminal
- **TCP client** — connect the phone to any remote host and relay text both ways
- **Local network discovery** via mDNS/DNS-SD (`_http._tcp.`, service `command-hub`)

### Camera

- Multi-camera enumeration, including logical multi-camera sub-IDs
- **MJPEG** stream at `/stream/{id}` for browsers and OpenCV `VideoCapture`
- **JPEG-over-WebSocket** at `/camera-ws/{id}` for low-overhead custom clients
- **Hardware H.264** over WebSocket at `/h264-ws/{id}`, decoded in the browser with
  WebCodecs (`VideoDecoder`) and `SharedArrayBuffer` on Chrome 138+
- Per-stream resolution, frame rate, JPEG quality, and bitrate controls
- Torch / flashlight control (on, off, toggle, state query)

### Sensors & Telemetry

- Live sensor streams over WebSocket for accelerometer, gyroscope, magnetometer, light,
  barometer, and anything else the phone reports
- One-shot sensor reads over HTTP for cheap polling
- Hardware telemetry snapshot: battery level, temperature, voltage, current, power,
  charge state, thermal status, CPU utilisation, and memory usage

### Embedded Web Server

- Ktor CIO server running **on the device**, binding `0.0.0.0:8080`
- HTTP endpoints and WebSocket streams
- Server-rendered HTML pages loaded from `app/src/main/assets/web/` templates with
  `{{placeholder}}` substitution, so web pages live as editable assets rather than
  inline Kotlin strings
- Auto-generated index at `/` listing every registered route

---

## Web API

The full reference lives in **[API.md](API.md)**: every endpoint, parameter, JSON
schema, WebSocket frame format, and error code.

| Subsystem  | Key endpoints                                                              |
| ---------- | -------------------------------------------------------------------------- |
| Core       | `GET /`, `GET /layout`, `POST /api/log`                                    |
| Camera     | `GET /api/cameras`, `GET /stream/{id}`, `WS /camera-ws/{id}`, `WS /h264-ws/{id}` |
| Sensors    | `GET /api/sensors`, `GET /api/sensors/{type}`, `WS /sensors-ws/{type}`     |
| Hardware   | `GET /api/hardware`                                                        |
| Flashlight | `GET /api/flashlight`, `POST /api/flashlight/toggle`                       |
| UART       | `GET /uart`, `WS /uart-ws`, `POST /uart-send`                              |
| Bluetooth  | `GET /bluetooth`, `WS /bluetooth-ws`, `POST /bluetooth-send`, `POST /bluetooth-connect` |
| TCP        | `GET /tcp`, `WS /tcp-ws`, `POST /tcp-send`, `POST /tcp-connect`            |

Example — watch a stream from Python:

```python
import cv2
cap = cv2.VideoCapture("http://192.168.1.42:8080/stream/0?res=640x480&fps=15")
while True:
    ok, frame = cap.read()
    if ok:
        cv2.imshow("stream", frame)
    if cv2.waitKey(1) & 0xFF == ord("q"):
        break
```

> ⚠️ **The server has no authentication.** Anyone on your network can read the camera
> streams and drive the attached hardware. Use it only on trusted networks. See
> [SECURITY.md](SECURITY.md) and [issue #4](https://github.com/Jedimig08/CommandHub/issues/4).

---

## Technologies Used

- Kotlin
- Jetpack Compose (Material Design 3)
- Android ViewModel + Coroutines + StateFlow
- Ktor Server (CIO) — HTTP, WebSockets, content negotiation
- kotlinx.serialization for JSON
- CameraX, plus `MediaCodec` for H.264 hardware encoding
- Android Sensor API
- USB Host API / USB Serial for Android
- Bluetooth Classic (RFCOMM SPP)
- Android `ConnectivityManager` for TCP link metrics
- Android NsdManager for mDNS
- Zeroconf (Python) for the desktop discovery utility

These technologies were chosen because they allow modern Android development while
supporting asynchronous hardware communication.

---

## Project Structure

The project follows a modular architecture where different responsibilities are
separated into their own packages.

```
                          CommandHub

                     Jetpack Compose UI
                             │
                    DashboardViewModel
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
   Dashboard Engine     Configuration      State Management
      (JSON)             Repository          (StateFlow)

                             │
                             ▼
                  Hardware & Services Layer
                             │
 ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
 │          │          │          │          │          │          │
 ▼          ▼          ▼          ▼          ▼          ▼          ▼
Bluetooth   UART     Camera    Sensors   Networking  TCP     Flashlight
             USB                GPS
                              IMU
                              Compass
                              Barometer
                              Light

                             │
                             ▼
                   Embedded Ktor Web Server
                             │
                ┌────────────┴────────────┐
                ▼                         ▼
       server/routes/*.kt          WebAssetLoader
       (8 route modules)          + assets/web/*.html
                │                         │
                └────────────┬────────────┘
                             ▼
                    HTTP • WebSockets • mDNS
                             │
                             ▼
                  Browser / Remote Dashboard
```

### Packages

| Package            | Responsibility                                                       |
| ------------------ | -------------------------------------------------------------------- |
| `ui/`              | Jetpack Compose screens — `MainActivity`, `DashboardPager`, `DashboardPage`, theme |
| `data/`            | Models and `LayoutRepository` for loading/saving dashboard JSON      |
| `viewmodel/`       | `DashboardViewModel` plus one manager class per hardware subsystem   |
| `server/`          | Ktor server setup, `WebAssetLoader`, and the `routes/` modules        |
| `server/routes/`   | One file per subsystem — the source of truth for [API.md](API.md)     |
| `util/`            | Small helpers                                                        |

### UI

The UI package contains the Jetpack Compose interface used throughout the application.
Instead of traditional XML layouts, every screen is implemented using Compose. This makes
the interface reactive and allows dashboard widgets to update automatically when new data
becomes available.

The dashboard is the central part of the application. Widgets are displayed dynamically
according to the selected JSON layout, allowing different configurations without
recompiling the application.

### Data

The data package contains the models and repository classes responsible for loading
dashboard configurations and managing persistent application data. Dashboard layouts are
stored as JSON files, making it possible to create new interfaces simply by editing
configuration files instead of modifying source code.

### Hardware Managers

One of the most important design decisions was separating each hardware interface into
its own manager class:

- **`CameraManager.kt`** — CameraX setup, JPEG/MJPEG frame emission, H.264 encoding,
  camera enumeration, and the flashlight.
- **`BluetoothManager.kt`** — Bluetooth Classic discovery, connection management, and
  bidirectional data communication.
- **`UartManager.kt`** — USB serial communication with external microcontrollers.
- **`SensorManager.kt`** — Android `SensorManager` access for the accelerometer,
  gyroscope, magnetometer, light sensor, barometer, and other available hardware.
- **`TcpManager.kt`** — outbound TCP client connections with per-interface link
  metrics (Wi-Fi vs. cellular, SSID, signal, link speed).
- **`HardwareManager.kt`** — battery, charging, thermal, CPU, and memory telemetry.
- **`network.kt`** — the `DashboardServer` class: builds and owns the Ktor server,
  wires up every route module, manages mDNS registration, and holds the Wi-Fi multicast
  lock. Route logic itself lives in `server/routes/`, not here.

Keeping these systems independent improves maintainability while making it easier to add
support for additional communication protocols and hardware features in future versions.

### Networking

`network.kt` exposes `DashboardServer`, which embeds a Ktor server that runs directly on
the Android device. It applies JSON content negotiation and WebSocket support, then
delegates each subsystem to a module in `server/routes/`:

| Route module       | Mounted paths                                   |
| ------------------ | ----------------------------------------------- |
| `CoreRoutes.kt`    | `/`, `/layout`, `/log`, `/api/log`              |
| `CameraRoutes.kt`  | `/camera`, `/stream/{id}`, `/api/cameras`, `/camera-ws/{id}`, `/h264-ws/{id}` |
| `SensorRoutes.kt`  | `/sensors`, `/api/sensors`, `/sensors-ws/{type}` |
| `HardwareRoutes.kt`| `/hardware`, `/api/hardware`                    |
| `FlashlightRoutes.kt` | `/api/flashlight`, `/api/flashlight/toggle`   |
| `UartRoutes.kt`    | `/uart`, `/uart-ws`, `/uart-send`               |
| `BluetoothRoutes.kt` | `/bluetooth`, `/bluetooth-ws`, `/bluetooth-send`, `/bluetooth-connect` |
| `TcpRoutes.kt`     | `/tcp`, `/tcp-ws`, `/tcp-send`, `/tcp-connect`  |

Each module registers its routes through a shared `register(path, name)` helper that
also records the path for the auto-generated index page at `/`.

### Assets

- `assets/layout.json` — the default dashboard layout.
- `assets/web/*.html` — templates for every web page, filled in by `WebAssetLoader`
  using `{{placeholder}}` substitution. Keeping pages in `assets/` means they can be
  edited without touching Kotlin code.

---

## Design Decisions

**Jetpack Compose over XML.** Compose allows the dashboard to be generated dynamically
from configuration data while significantly reducing UI boilerplate. Since layouts are
loaded from JSON, Compose makes it much easier to generate widgets programmatically.

**Separate manager classes per subsystem.** Rather than placing Bluetooth, USB,
networking, and camera logic inside a single large activity, each subsystem has its own
implementation. This keeps responsibilities clearly separated and improves readability.

**Coroutines and StateFlow throughout.** Hardware communication is naturally
asynchronous, and reactive programming allows the interface to update automatically
whenever new information is received.

**HTML pages as assets, not inline strings.** Early versions of the server built every
web page with Kotlin string literals, which became unreadable as the pages grew. Moving
them to `assets/web/*.html` with `{{placeholder}}` substitution made the HTML editable in
a browser and kept the route files small.

**Route modules split by subsystem.** A single monolithic networking file was hard to
navigate. Splitting it into eight route modules means each feature's HTTP surface can be
read in isolation.

---

## Important Files

| File / Directory        | Purpose                                                             |
| ----------------------- | ------------------------------------------------------------------- |
| `ui/MainActivity.kt`    | Entry point — initializes the UI, requests runtime permissions, starts the dashboard |
| `viewmodel/DashboardViewModel.kt` | Owns application state and coordinates the UI, hardware, and background services |
| `ui/DashboardPager.kt`  | Navigation between dashboard pages                                  |
| `ui/DashboardPage.kt`   | Renders widgets on each dashboard screen                            |
| `data/LayoutRepository.kt` | Loads, saves, and manages dashboard configurations as JSON        |
| `assets/layout.json`    | Default dashboard layout, editable without recompiling              |
| `viewmodel/network.kt`  | `DashboardServer` — Ktor server construction and lifecycle          |
| `server/routes/*.kt`    | The full HTTP/WebSocket surface (see [API.md](API.md))              |
| `server/WebAssetLoader.kt` | Loads and caches `assets/web/*.html` templates                  |
| `viewmodel/CameraManager.kt`    | Camera access, streaming, H.264 encoding, flashlight      |
| `viewmodel/BluetoothManager.kt` | Bluetooth Classic connections and data transfer            |
| `viewmodel/UartManager.kt`      | USB serial communication                           |
| `viewmodel/SensorManager.kt`    | Onboard sensor access                             |
| `viewmodel/TcpManager.kt`       | TCP client connections and link metrics           |
| `viewmodel/HardwareManager.kt`  | Battery, thermal, CPU, and RAM telemetry           |
| `pc_files/android_domain.py`    | mDNS discovery helper for desktop clients         |

---

## Building the Project

### Requirements

- Android Studio Meerkat or newer
- Android SDK **36** (`compileSdk` / `targetSdk`; `minSdk` 26)
- JDK 17+ (Android Studio's bundled JBR works)
- Kotlin and Gradle are provided by the project's wrapper and version catalog

### Build

```bash
# From a machine with the Android SDK installed
./gradlew assembleDebug
```

On a machine without `java` on `PATH`, point Gradle at Android Studio's bundled JDK:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
```

Or simply open the project in Android Studio, let Gradle sync, and press **Run** on a
physical Android device.

### Optional Companion Utility

The repository includes `pc_files/android_domain.py`, a small Python utility that
automatically discovers the embedded web server running on CommandHub using mDNS
(ZeroConf). Once the application is discovered on the local network, the script
automatically opens the dashboard in your default web browser.

#### Requirements

- Python 3
- [zeroconf](https://pypi.org/project/zeroconf/)

```bash
pip install zeroconf
python pc_files/android_domain.py
```

The utility works on Windows, Linux, and macOS, provided mDNS (Bonjour/ZeroConf) is
available on the system.

### Tests and Lint

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
```

---

## Challenges

The largest challenge during development was integrating several Android hardware APIs
into one application. USB serial communication, Bluetooth, camera access, networking, and
Compose each have different permission models and lifecycle requirements. Ensuring these
systems worked together reliably required significant experimentation, debugging, and
testing.

Another challenge was designing a dashboard system flexible enough to support different
widget types while remaining easy to configure using JSON files.

On the streaming side, delivering smooth video meant choosing between latency and
compatibility: MJPEG works everywhere but is heavy on the wire, while hardware H.264 is
far more efficient but requires WebCodecs and a shared-memory configuration handshake to
decode in the browser.

## Future Improvements

Although the current version is functional, several improvements are planned:

- **Authentication and access control** for the embedded server
  ([issue #4](https://github.com/Jedimig08/CommandHub/issues/4))
- Bluetooth Low Energy support
- Fix periodic re-pings for connected Bluetooth Classic devices
  ([issue #3](https://github.com/Jedimig08/CommandHub/issues/3))
- MAVLink support for drone communication
- Additional telemetry widgets and graph visualization
- User-created dashboard editor
- Plugin system
- Wi-Fi communication modules
- Cloud synchronization
- Improved remote control capabilities

The modular architecture was specifically designed to make these additions possible
without major changes to the existing codebase.

## What I Learned

This project became much larger than I originally expected and allowed me to apply many
concepts learned throughout CS50 while exploring Android development in greater depth.

During development I gained practical experience with:

- Modern Android architecture
- Jetpack Compose
- Asynchronous programming
- Networking and embedded web servers
- Bluetooth and USB serial communication
- Video encoding and streaming
- JSON serialization
- Software architecture and modular application design

The project also reinforced the importance of planning, refactoring, documentation, and
writing maintainable code.

## AI Usage

This project was designed and implemented by me. During development, I used AI-assisted
programming tools, including ChatGPT and the built-in Gemini assistant in Android Studio,
as development aids. These tools were used to:

- Explain Android and Kotlin APIs
- Help understand Jetpack Compose concepts
- Suggest implementation approaches for certain features
- Review code for potential improvements
- Assist with debugging and troubleshooting
- Improve project documentation and the README

All architectural decisions, feature design, integration, testing, debugging, and the
final implementation were completed by me. AI tools were used to assist my learning and
productivity, not to replace my own work, in accordance with the CS50 AI policy.

## Conclusion

CommandHub represents the culmination of the concepts I learned throughout CS50 while
exploring Android development beyond the scope of the course. It combines modern Android
development practices, hardware communication, networking, asynchronous programming, and
software architecture into a single extensible application.

Although the project is already functional, I intend to continue developing it beyond
CS50 by adding additional communication protocols, telemetry visualization, and a fully
configurable dashboard editor.

## License

Released under the [MIT License](LICENSE).
