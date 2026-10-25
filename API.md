# CommandHub API Reference

CommandHub embeds a [Ktor](https://ktor.io/) CIO server inside the Android app. Any
device on the same local network can drive the phone's cameras, sensors, and attached
hardware over plain HTTP and WebSockets.

> **Security notice.** The server binds to `0.0.0.0:8080` and has **no authentication**.
> Anyone who can reach the phone's IP can read camera streams, send data to connected
> devices, and toggle hardware. It is intended for **trusted local networks only**.

---

## Table of Contents

- [Base URL](#base-url)
- [Service Discovery](#service-discovery)
- [Conventions](#conventions)
- [Core](#core)
- [Camera](#camera)
- [Sensors & IMU](#sensors--imu)
- [Hardware Telemetry](#hardware-telemetry)
- [Flashlight](#flashlight)
- [UART Terminal](#uart-terminal)
- [Bluetooth Terminal](#bluetooth-terminal)
- [TCP Console](#tcp-console)
- [Data Schemas](#data-schemas)
- [Errors](#errors)
- [Quick Examples](#quick-examples)

---

## Base URL

```
http://<phone-ip>:8080
```

| Property  | Value                     |
| --------- | ------------------------- |
| Host      | `0.0.0.0` (all interfaces)|
| Port      | `8080` (default)          |
| Protocol  | HTTP/1.1 + WebSocket      |
| Encoding  | UTF-8                     |
| Auth      | None                      |

---

## Service Discovery

CommandHub registers itself over mDNS/DNS-SD so clients don't need to hard-code an IP.

| Property       | Value             |
| -------------- | ----------------- |
| Service name   | `command-hub`     |
| Service type   | `_http._tcp.`     |
| Port           | `8080`            |

---

## Conventions

- **HTML routes** return `text/html` — pages meant for a browser.
- **`/api/*` routes** return `application/json` — meant for programs.
- **WebSocket routes** stream UTF-8 text or binary frames.

### Common query parameters

Camera and stream routes share a common set of query parameters:

| Param       | Type    | Default   | Applies to                          |
| ----------- | ------- | --------- | ----------------------------------- |
| `res`       | string  | `640x480` | `WxH`, e.g. `1280x720`              |
| `fps`       | int     | `30`      | Frame rate                          |
| `quality`   | int     | `50`      | JPEG quality, `10..100`             |
| `grayscale` | boolean | `false`   | Monochrome / Grayscale streaming    |
| `bitrate`   | int     | `2000000` | H.264 bitrate in bits per second    |
| `minimal`   | bool    | `false`   | Hides the viewer chrome when `true` |

---

## Core

### `GET /`

Returns the universal Control Center dashboard web UI.

**Response:** `200 text/html`

### `GET /layout`

Returns the currently active dashboard layout as JSON.

**Response:** `200 application/json`

### `POST /api/log`

Updates the text of a dashboard module. Body is JSON (`LogRequest`).

---

## Camera

### `GET /api/cameras`

Lists every camera the phone exposes, including physical and logical multi-camera sub-IDs.

**Response:** `200 application/json` — array of [`CameraInfo`](#camerainfo).

### `GET /stream/{id}`

MJPEG (`multipart/x-mixed-replace`) stream. Works with browser `<img>` tags and OpenCV `cv2.VideoCapture`.

```python
import cv2
cap = cv2.VideoCapture("http://192.168.1.42:8080/stream/0?res=640x480&fps=15&grayscale=true")
```

### `WS /camera-ws/{id}`

WebSocket streaming JPEG frames. Supports query parameters `res`, `fps`, `quality`, and `grayscale`.

### `GET /api/camera/stats`

Returns real-time camera streaming performance metrics (current FPS, bitrate, dropped frames, viewers, zoom, exposure).

**Response:** `200 application/json` — [`CameraStats`](#camerastats).

### `POST /api/camera/control`

Remote camera control endpoint. Accepts query parameters:
- `zoom`: float (e.g. `1.5`)
- `exposure`: int (e.g. `1`)
- `torch`: boolean (`true`/`false`)

---

## Sensors & IMU

### `GET /api/sensors`

Lists the device's hardware sensors.

### `WS /sensors-ws/{type}`

Streams live sensor readings for a specific sensor type constant.

### `WS /ws/sensors/imu`

Streams high-rate synchronized 9-DOF IMU telemetry (Accelerometer, Gyroscope, Magnetometer, and Orientation Quaternions).

**Response:** UTF-8 JSON [`ImuData`](#imudata).

### `GET /api/sensors/imu`

Returns the most recent 9-DOF IMU reading snapshot.

---

## Hardware Telemetry

### `GET /api/hardware`

Returns a snapshot of battery, power, thermal status, overheating flag, CPU usage, and RAM telemetry.

**Response:** `200 application/json` — [`HardwareTelemetry`](#hardwaretelemetry).

---

## Flashlight

### `GET /api/flashlight`, `POST /api/flashlight`, `POST /api/flashlight/toggle`

Reads or sets torch/flashlight state.

---

## UART Terminal

Talks to a USB serial device (Arduino, ESP32, Polargraph controller) through the phone's USB OTG port at 115200 baud.

### `WS /uart-ws`, `POST /uart-send`

Bidirectional UART communication endpoints.

---

## Bluetooth Terminal & TCP Console

### `GET /bluetooth`, `WS /bluetooth-ws`, `POST /bluetooth-send`, `POST /bluetooth-connect`
### `GET /tcp`, `WS /tcp-ws`, `POST /tcp-send`, `POST /tcp-connect`

---

## Data Schemas

### `CameraStats`
| Field | Type | Description |
|---|---|---|
| `currentFps` | float | Actual measured FPS |
| `bitrateBps` | long | Throughput in bits per second |
| `totalFramesDropped` | long | Dropped frames count |
| `activeViewers` | int | Active stream subscriber count |
| `zoomRatio` | float | Current zoom level |

### `ImuData`
| Field | Type | Description |
|---|---|---|
| `timestampNanos` | long | Nanosecond sensor timestamp |
| `accelX`, `accelY`, `accelZ` | float | Linear acceleration (m/s²) |
| `gyroX`, `gyroY`, `gyroZ` | float | Angular velocity (rad/s) |
| `quatX`, `quatY`, `quatZ`, `quatW` | float | Orientation quaternion |

### `HardwareTelemetry`
| Field | Type | Description |
|---|---|---|
| `batteryLevelPct` | int | Battery percentage |
| `batteryTempCelsius` | float | Battery temperature |
| `powerWatts` | float | Instantaneous power draw |
| `isOverheating` | bool | Thermal throttling / overheating flag |
| `cpuUsagePct` | float | App CPU usage percentage |
| `ramUsedMb` | long | Used RAM in MB |

---

## Quick Examples

**Stream Grayscale Camera in OpenCV:**
```python
import cv2
cap = cv2.VideoCapture("http://192.168.1.42:8080/stream/0?res=640x480&fps=15&grayscale=true")
```

**Stream 9-DOF IMU over WebSocket:**
```bash
npx wscat -c ws://192.168.1.42:8080/ws/sensors/imu
```

**Send G-Code to Polargraph over UART:**
```bash
curl -X POST http://192.168.1.42:8080/uart-send -d 'G28'
```
