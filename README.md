# CommandHub

#### Video Demo: https://youtu.be/YOUR_VIDEO_URL

## Description

CommandHub is an Android application designed to serve as a central control station for robotics, embedded systems, and hardware development projects. It combines multiple communication methods, hardware interfaces, and monitoring tools into a single customizable application, allowing developers to interact with microcontrollers and other devices without switching between several separate applications.

The original motivation behind this project came from my own experience working with Arduino, ESP32, and robotics projects. During development I often found myself opening multiple applications simultaneously: one for USB serial communication, another for Bluetooth, another for viewing camera streams, and sometimes a browser for accessing a web server. This workflow quickly became inconvenient and difficult to manage. CommandHub was created to solve this problem by bringing these tools together into one modular Android application.

Unlike many hardware utility applications that provide only one function, CommandHub was designed from the beginning to be extensible. Rather than creating a fixed interface, the application uses configurable dashboard layouts stored as JSON files. This allows different dashboards to be loaded without modifying the application's source code. The long-term goal is to create a platform that can adapt to many different robotics and embedded projects instead of being tied to one specific device.

## Features

CommandHub currently includes several major components:

- Modular dashboard built with Jetpack Compose
- JSON-based dashboard configuration
- USB Serial (UART) communication
- Bluetooth Classic communication
- Integrated Ktor web server
- Local network discovery using mDNS
- Camera support
- Terminal interfaces for connected devices
- Material Design user interface
- Multiple dashboard pages
- Configurable layouts

Each feature is designed as an independent component, making the application easier to maintain and expand.

## Technologies Used

The application is primarily written in Kotlin and targets Android devices.

Major technologies used include:

- Kotlin
- Jetpack Compose
- Android ViewModel
- Kotlin Coroutines
- StateFlow
- Ktor Server
- Android Camera APIs
- USB Host API
- Bluetooth Classic API
- Kotlin Serialization
- JSON

These technologies were chosen because they allow modern Android development while supporting asynchronous hardware communication.

## Project Structure

The project follows a modular architecture where different responsibilities are separated into their own packages.

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
 ┌──────────┬──────────┬──────────┬──────────┬──────────┐
 │          │          │          │          │          │
 ▼          ▼          ▼          ▼          ▼
Bluetooth   UART     Camera    Sensors   Networking
             USB                GPS
                              IMU
                              Compass
                              Barometer
                              Light

                             │
                             ▼
                    Embedded Ktor Web Server
                             │
                    HTTP • WebSockets • mDNS
                             │
                             ▼
                  Browser / Remote Dashboard
```


### UI

The UI package contains the Jetpack Compose interface used throughout the application. Instead of traditional XML layouts, every screen is implemented using Compose. This makes the interface reactive and allows dashboard widgets to update automatically when new data becomes available.

The dashboard is the central part of the application. Widgets are displayed dynamically according to the selected JSON layout, allowing different configurations without recompiling the application.

### Data

The data package contains the models and repository classes responsible for loading dashboard configurations and managing persistent application data.

Dashboard layouts are stored as JSON files. This design makes it possible to create new interfaces simply by editing configuration files instead of modifying source code.

### Hardware Integration

A major goal of CommandHub is to provide a unified interface for interacting with both external hardware and the sensors built into an Android device.

The application supports communication with external devices using USB Serial (UART) and Bluetooth while also exposing data from the phone's onboard sensors. These include motion sensors such as the accelerometer and gyroscope, environmental sensors when available, GPS location, camera access, and other Android hardware APIs.

The hardware layer was designed to remain modular so that additional communication methods and sensors can be integrated without requiring major changes to the rest of the application.

### Hardware Managers

One of the most important design decisions was separating each hardware interface into its own manager class.

For example:

- CameraManager controls camera initialization and image capture.
- BluetoothManager manages Bluetooth discovery, connections, and data transfer.
- UartManager handles USB serial communication with external microcontrollers.
- SensorManager provides access to the device's built-in sensors, including the accelerometer, gyroscope, magnetometer, GPS, light sensor, and other available hardware sensors.
- network.kt configures the application's embedded networking services, including the Ktor server and local network communication.

Keeping these systems independent improves maintainability while making it easier to add support for additional communication protocols and hardware features in future versions.

### Networking

CommandHub includes an embedded Ktor server that runs directly on the Android device.

The server exposes HTTP endpoints and WebSocket connections, allowing external devices on the local network to communicate with the application. This enables browser-based monitoring and creates opportunities for future remote-control features.

### Assets

The assets folder contains the default dashboard configuration along with other resources used during initialization.

## Design Decisions

One of the biggest design decisions was choosing Jetpack Compose instead of Android's traditional XML interface system.

Compose allows the dashboard to be generated dynamically from configuration data while significantly reducing UI boilerplate. Since layouts are loaded from JSON, Compose makes it much easier to generate widgets programmatically.

Another important decision was separating hardware communication into dedicated manager classes. Rather than placing Bluetooth, USB, networking, and camera logic inside a single large activity, each subsystem has its own implementation. This keeps responsibilities clearly separated and improves code readability.

The project also makes extensive use of Kotlin Coroutines and StateFlow. Hardware communication is naturally asynchronous, and reactive programming allows the interface to update automatically whenever new information is received.

## Important Files

### MainActivity.kt

The application's entry point. It initializes the user interface, requests runtime permissions, and starts the main dashboard.

### DashboardViewModel.kt

Maintains the application's state and coordinates communication between the user interface, hardware components, and background services.

### DashboardPager.kt

Provides navigation between dashboard pages and allows multiple configurable layouts.

### DashboardPage.kt

Defines the individual dashboard screens and renders widgets using Jetpack Compose.

### LayoutRepository.kt

Loads, saves, and manages dashboard configurations stored as JSON files.

### layout.json

Contains the default dashboard layout used when the application starts. The dashboard can be modified by changing this configuration instead of editing source code.

### BluetoothManager.kt
Handles Bluetooth Classic device discovery, connection management, and bidirectional data communication.

### UartManager.kt
Provides USB Serial (UART) communication with compatible external devices such as Arduino and ESP32 boards.

### CameraManager.kt
Manages camera access and integrates camera functionality into the dashboard.

### SensorManager.kt
Interfaces with Android's SensorManager API to collect data from available device sensors such as the accelerometer, gyroscope, magnetometer, GPS, light sensor, and barometer when supported by the hardware.

### network.kt
Initializes and configures the embedded Ktor server used for HTTP, WebSocket communication, and local network services.


## Challenges

The largest challenge during development was integrating several Android hardware APIs into one application.

USB serial communication, Bluetooth, camera access, networking, and Compose each have different permission models and lifecycle requirements. Ensuring these systems worked together reliably required significant experimentation, debugging, and testing.

Another challenge was designing a dashboard system flexible enough to support different widget types while remaining easy to configure using JSON files.

## Future Improvements

Although the current version is functional, several improvements are planned.

These include:

- Bluetooth Low Energy support
- MAVLink support for drone communication
- Additional telemetry widgets
- Graph visualization
- User-created dashboard editor
- Plugin system
- Wi-Fi communication modules
- Cloud synchronization
- Improved remote control capabilities

The modular architecture was specifically designed to make these additions possible without major changes to the existing codebase.

## What I Learned

This project became much larger than I originally expected and allowed me to apply many concepts learned throughout CS50 while exploring Android development in greater depth.

During development I gained practical experience with:

- Modern Android architecture
- Jetpack Compose
- Asynchronous programming
- Networking
- Embedded web servers
- Bluetooth communication
- USB serial communication
- JSON serialization
- Software architecture
- Modular application design

The project also reinforced the importance of planning, refactoring, documentation, and writing maintainable code.


## AI Usage

This project was designed and implemented by me. During development, I used AI-assisted programming tools, including ChatGPT and the built-in Gemini assistant in Android Studio, as development aids. These tools were used to:

- Explain Android and Kotlin APIs
- Help understand Jetpack Compose concepts
- Suggest implementation approaches for certain features
- Review code for potential improvements
- Assist with debugging and troubleshooting
- Improve project documentation and the README

All architectural decisions, feature design, integration, testing, debugging, and the final implementation were completed by me. AI tools were used to assist my learning and productivity, not to replace my own work, in accordance with the CS50 AI policy.

## Building the Project

### Requirements

- Android Studio Meerkat or newer
- Android SDK 35
- Kotlin
- Gradle

### Build

1. Clone the repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Run the application on a physical Android device.

### Optional Companion Utility

The repository also includes `android_domain.py`, a small Python utility that automatically discovers the embedded web server running on CommandHub using mDNS (ZeroConf). Once the application is discovered on the local network, the script automatically opens the dashboard in your default web browser.

#### Requirements

- Python 3
- zeroconf

Install the required package with:

```bash
pip install zeroconf
```

Then run:

```bash
python android_domain.py
```

The utility works on Windows, Linux, and macOS, provided mDNS (Bonjour/ZeroConf) is available on the system.

## Conclusion

CommandHub represents the culmination of the concepts I learned throughout CS50 while exploring Android development beyond the scope of the course. It combines modern Android development practices, hardware communication, networking, asynchronous programming, and software architecture into a single extensible application.

Although the project is already functional, I intend to continue developing it beyond CS50 by adding additional communication protocols, telemetry visualization, and a fully configurable dashboard editor.
