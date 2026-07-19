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
   Layout Engine      Configuration       State Management
     (JSON)             Repository          (StateFlow)

                             │
                             ▼
                    Hardware Abstraction Layer
                             │
 ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
 │          │          │          │          │          │          │
 ▼          ▼          ▼          ▼          ▼          ▼          ▼
Bluetooth   UART     Cameras    Sensors    Network     Storage   Location
 Manager   Manager   Manager      API      Services    Manager      GPS
                                    │
                                    ▼
                    Accelerometer • Gyroscope
                  Magnetometer • Orientation
                    Barometer • Light • etc.

                             │
                             ▼
                       Embedded Ktor Server
                             │
            HTTP • WebSockets • mDNS Discovery
                             │
                             ▼
                    Browser / Remote Clients
```


### UI

The UI package contains the Jetpack Compose interface used throughout the application. Instead of traditional XML layouts, every screen is implemented using Compose. This makes the interface reactive and allows dashboard widgets to update automatically when new data becomes available.

The dashboard is the central part of the application. Widgets are displayed dynamically according to the selected JSON layout, allowing different configurations without recompiling the application.

### Data

The data package contains the models and repository classes responsible for loading dashboard configurations and managing persistent application data.

Dashboard layouts are stored as JSON files. This design makes it possible to create new interfaces simply by editing configuration files instead of modifying source code.

### Hardware Managers

One of the most important design decisions was separating each hardware interface into its own manager.

For example:

- CameraManager controls camera initialization and streaming.
- BluetoothClassicManager manages Bluetooth discovery, connection, and communication.
- UartManager handles USB serial devices and terminal communication.

Keeping these systems independent improves maintainability while making it easier to add support for additional communication protocols in future versions.

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

This project was developed primarily by me. During development, I used AI-assisted programming tools, including ChatGPT and the built-in Gemini assistant in Android Studio, as development aids. These tools were used to:

- Explain Android and Kotlin APIs
- Help understand Jetpack Compose concepts
- Suggest implementation approaches for certain features
- Review code for potential improvements
- Assist with debugging and troubleshooting
- Improve project documentation and the README

All architectural decisions, feature design, integration, testing, debugging, and the final implementation were completed by me. AI tools were used to assist my learning and productivity, not to replace my own work, in accordance with the CS50 AI policy.
