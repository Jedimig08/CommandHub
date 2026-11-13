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
- [Settings](#settings)
- [Camera](#camera)
- [Sensors](#sensors)
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

Returns the Control Center web UI.

**Response:** `200 text/html`

### `GET /log`

Returns the dashboard logger page.

**Response:** `200 text/html`

### `GET /layout` (legacy)

Returns `{}`. The current WebView UI does not load `assets/layout.json`; this route is
kept for compatibility with older clients.

**Response:** `200 application/json`

### `POST /api/log`

Accepts a JSON `LogRequest` (`id`, `text`). Used by the optional logger page at `/log`.

---

## Settings

### `GET /api/settings/state`

Returns the current runtime settings.

```json
{
  "screenOn": true,
  "sensorFastest": false,
  "processingUrl": "http://127.0.0.1:5000/"
}
```

### `GET /api/settings/processing-url`

Returns the saved Linux/Termux processing-UI URL.

### `POST /api/settings/processing-url`

Saves the processing-UI URL. Accepts either a JSON body (`{"url":"http://..."}`) or an
`url` query parameter.

### `POST /api/settings/publish-domain`

Re-publishes the mDNS/DNS-SD advertisement.

### `POST /api/settings/screen-on/toggle`

Toggles keep-screen-awake and returns the new state.

### `POST /api/settings/sensors-speed/toggle`

Toggles sensor sampling between UI rate and `SENSOR_DELAY_FASTEST` and returns the new
state.

---

## Camera

### `GET /camera`

Camera hub page listing available cameras.

**Response:** `200 text/html`

### `GET /camera/{id}`

Camera viewer page for a specific camera ID.

**Response:** `200 text/html`

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

WebSocket streaming JPEG frames. Supports query parameters `res`, `fps`, `quality`, and
`grayscale`.

### `WS /h264-ws/{id}`

WebSocket streaming hardware-encoded H.264. Supports `res`, `fps`, `bitrate`, and
`grayscale`. The bundled viewer uses JPEG-over-WebSocket instead.

### `GET /api/camera/stats`

Returns real-time camera streaming performance metrics (current FPS, bitrate, dropped frames, viewers, zoom, exposure).

**Response:** `200 application/json` — [`CameraStats`](#camerastats).

### `POST /api/camera/control`

Remote camera control endpoint. Accepts query parameters:
- `zoom`: float (e.g. `1.5`)
- `exposure`: int (e.g. `1`)
- `torch`: boolean (`true`/`false`)

---

## Sensors

### `GET /sensors`

Sensor hub page listing the device's available sensors.

**Response:** `200 text/html`

### `GET /sensors/{type}`

Sensor viewer page for an Android sensor type constant.

**Response:** `200 text/html`

### `GET /api/sensors`

Lists the device's hardware sensors.

**Response:** `200 application/json` — array of [`SensorInfo`](#sensorinfo).

### `GET /api/sensors/{type}`

Returns the latest buffered reading for one sensor type. The intended response is
`{"id": <type>, "value": <SensorData>}` when data is available.

> **Known issue:** the current mixed-type map response can fail serialization. Use
> `/sensors-ws/{type}` until this is fixed.

### `WS /sensors-ws/{type}`

Streams live sensor readings for a specific sensor type constant.

**Response:** UTF-8 JSON [`SensorData`](#sensordata).

---

## Hardware Telemetry

### `GET /hardware`

Hardware and power dashboard page.

**Response:** `200 text/html`

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

### `CameraInfo`
| Field | Type | Description |
|---|---|---|
| `id` | string | Camera ID |
| `facing` | string | Front/back/external |
| `type` | string | Logical or physical |
| `focalLength` | float | Focal length |
| `isPhysical` | bool | Physical camera flag |
| `supportedResolutions` | array | Supported `{width, height}` pairs |

### `CameraStats`
| Field | Type | Description |
|---|---|---|
| `activeCameraId` | string? | Camera currently streaming |
| `resolution` | string? | Current `WxH` |
| `targetFps` | int | Requested FPS |
| `currentFps` | float | Actual measured FPS |
| `totalFramesCaptured` | long | Frames captured |
| `totalFramesDropped` | long | Dropped frames |
| `totalBytesStreamed` | long | Bytes sent to viewers |
| `bitrateBps` | long | Throughput in bits per second |
| `activeViewers` | int | Active stream subscriber count |
| `zoomRatio` | float | Current zoom level |
| `minZoomRatio`, `maxZoomRatio` | float | Zoom range |
| `exposureIndex` | int | Current exposure compensation |
| `minExposureIndex`, `maxExposureIndex` | int | Exposure range |
| `torchEnabled` | bool | Torch state |

### `SensorInfo`
| Field | Type | Description |
|---|---|---|
| `id` | int | Sensor ID |
| `name` | string | Sensor name |
| `vendor` | string | Sensor vendor |
| `type` | int | Android sensor type constant |
| `stringType` | string | Android string type |

### `SensorData`
| Field | Type | Description |
|---|---|---|
| `type` | int | Android sensor type constant |
| `timestamp` | long | Sensor timestamp in nanoseconds |
| `values` | float[] | Sensor values in Android order |

### `HardwareTelemetry`
| Field | Type | Description |
|---|---|---|
| `batteryLevelPct` | int | Battery percentage |
| `batteryTempCelsius` | float | Battery temperature |
| `batteryVoltageV` | float | Battery voltage |
| `currentNowMa` | float | Instantaneous current in mA |
| `powerWatts` | float | Instantaneous power draw |
| `isCharging` | bool | Charging state |
| `chargingSource` | string | Charging source |
| `thermalStatus` | string | Thermal status |
| `isOverheating` | bool | Thermal throttling / overheating flag |
| `cpuUsagePct` | float | App CPU usage percentage |
| `ramUsedMb` | long | Used RAM in MB |
| `ramTotalMb` | long | Total RAM in MB |

---

## Errors

| Status | Meaning |
|---|---|
| `400 Bad Request` | Malformed JSON body or missing/invalid parameter |
| `503 Service Unavailable` | Camera provider not ready (`/api/cameras`) |
| WebSocket close | Missing/invalid path parameter or client disconnect |

---

## Quick Examples

**Stream Grayscale Camera in OpenCV:**
```python
import cv2
cap = cv2.VideoCapture("http://192.168.1.42:8080/stream/0?res=640x480&fps=15&grayscale=true")
```

**Stream the accelerometer over WebSocket (Android type `1`):**
```bash
npx wscat -c ws://192.168.1.42:8080/sensors-ws/1
```

**Toggle keep-screen-awake:**
```bash
curl -X POST http://192.168.1.42:8080/api/settings/screen-on/toggle
```

**Send G-Code to Polargraph over UART:**
```bash
curl -X POST http://192.168.1.42:8080/uart-send -d 'G28'
```
