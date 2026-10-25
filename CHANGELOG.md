# Changelog

All notable changes to CommandHub are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Camera streaming performance and H.264 work that has landed on `main` since the
`v1.2.0` tag, plus documentation and repository housekeeping.

### Added

- Hardware **H.264 encoding** (`MediaCodec`) with a `WS /h264-ws/{id}` endpoint that
  delivers SPS/PPS configuration followed by NAL units for WebCodecs decoding.
- JPEG-over-WebSocket camera streams at `WS /camera-ws/{id}` as a lower-overhead
  alternative to MJPEG.
- **Grayscale / Monochrome streaming mode** (`grayscale=true`) across camera streams, reducing bandwidth by 33%–50%.
- **Camera statistics & control endpoints** (`/api/camera/stats`, `/api/camera/control`) for real-time FPS, bitrate tracking, zoom, and exposure adjustments.
- **Synchronized 9-DOF IMU telemetry** endpoint (`/ws/sensors/imu`) combining accelerometer, gyroscope, magnetometer, and orientation quaternions.
- **WebView-based Control Center UI** hosting the local Ktor web dashboard directly inside the app with hardware back-navigation support.
- **`API.md`** — complete HTTP and WebSocket reference: every endpoint, query
  parameter, JSON schema, frame format, and error code.
- GitHub issue templates, a pull request template, and `SECURITY.md`.
- README quick start, table of contents, badges, and a Web API summary section.

### Changed

- Web server routes split out of `network.kt` into eight focused modules under
  `server/routes/`.
- Inline HTML strings extracted into editable templates under `app/src/main/assets/web/`,
  loaded via `WebAssetLoader` and `{{placeholder}}` substitution.
- Application package renamed from `com.example.uitest` to `com.commandhub`
  (namespace and `applicationId` updated).
- README rewritten to match the current architecture; CHANGELOG rebuilt from git
  history to match the released tags.

### Fixed

- Kotlin serialization plugin bumped to a version compatible with Kotlin 2.3
  (`1.9.0` → `2.4.21`).
- `COLOR_FormatYUV420SemiPlanar` deprecation suppressed explicitly at the one call
  site that requires it.
- Web assets given proper metadata tags and accessibility attributes.

### Performance

- Camera streaming moved to asynchronous encoding with frame dropping and reduced
  buffer capacity.
- MJPEG frame headers preallocated and buffers recycled instead of reallocated
  per frame.

### Removed

- Dead Compose UI dashboard components (`DashboardPage`, `DashboardPager`, `KeepScreenOn`, `ModuleUtils`) in favor of the thin-client WebView interface.
- `.idea/` IDE files are no longer tracked in version control.

## [1.2.0] - 2026-10-10

Tag: `v1.2.0`

### Added

- **Hardware telemetry**: a dashboard module and `/api/hardware` endpoint exposing
  battery level, temperature, voltage, current, power, charge state, thermal status,
  CPU usage, and memory usage.
- **Flashlight control**: on/off/toggle from the camera manager, dashboard UI, and
  `POST /api/flashlight` web API.
- **Frame rate (FPS) controls** for camera streaming across the UI and web streams.
- **Settings menu** with additional configuration options.
- **Configurable macro buttons** on the network terminal for sending canned commands.
- **Immersive mode** for the main activity and auto-hiding UI for camera streams.
- Module **pause/active toggle** and configurable sensor sampling delays.

### Changed

- Project upgraded to JDK 25.

## [1.1.0] - 2026-09-03

Tag: `v1.1.0`

### Added

- **TCP console**: outbound TCP client connections with `POST /tcp-connect`,
  `POST /tcp-send`, and a `WS /tcp-ws` relay.
- Network connection handling surfaced in the dashboard.

## 1.0 — early preview - 2026-08-28

Tag: `App_apk` (pre-1.0 snapshot)

### Added

- Resolution selection for camera streams, with a resolution picker and a minimal UI
  mode on the web hub.

## [1.0.0]

Initial public release.

### Added

- Modular dashboard built with Jetpack Compose and driven by a JSON layout.
- Multi-page dashboard with reorderable, editable widget grid and persistent layouts.
- Bluetooth Classic terminal with paired-device discovery and bidirectional data.
- USB Serial (UART) communication with automatic connection on launch.
- Camera manager with dashboard integration.
- Embedded Ktor web server with HTTP and WebSocket support.
- mDNS service discovery (`command-hub`, `_http._tcp.`) for clients on the local network.
- Sensor dashboard widgets fed by the Android Sensor API.
- `pc_files/android_domain.py` companion utility for desktop discovery and browser launch.
- Terminal interfaces for connected devices.
- Material Design 3 interface.

[Unreleased]: https://github.com/Jedimig08/CommandHub/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/Jedimig08/CommandHub/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/Jedimig08/CommandHub/compare/App_apk...v1.1.0
