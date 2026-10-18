# CommandHub API Reference

CommandHub embeds a [Ktor](https://ktor.io/) CIO server inside the Android app. Any
device on the same local network can drive the phone's cameras, sensors, and attached
hardware over plain HTTP and WebSockets.

This document is generated from the route modules under
[`app/src/main/java/com/commandhub/server/routes/`](app/src/main/java/com/commandhub/server/routes/).

> **Security notice.** The server binds to `0.0.0.0:8080` and has **no authentication**.
> Anyone who can reach the phone's IP can read camera streams, send data to connected
> devices, and toggle hardware. It is intended for **trusted local networks only**.
> See [issue #4](https://github.com/Jedimig08/CommandHub/issues/4) for the planned auth work.

---

## Table of Contents

- [Base URL](#base-url)
- [Service Discovery](#service-discovery)
- [Conventions](#conventions)
- [Core](#core)
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

The port is fixed at construction time in `DashboardServer`. Changing it requires a
source edit (`DashboardViewModel` constructs the server on port `8080`).

---

## Service Discovery

CommandHub registers itself over mDNS/DNS-SD so clients don't need to hard-code an IP.

| Property       | Value             |
| -------------- | ----------------- |
| Service name   | `command-hub`     |
| Service type   | `_http._tcp.`     |
| Port           | `8080`            |

**Python helper.** [`pc_files/android_domain.py`](pc_files/android_domain.py) browses for
the service with `zeroconf` and opens the dashboard in your browser:

```bash
pip install zeroconf
python pc_files/android_domain.py
```

**Caching.** Companion clients may cache the last-known URL. The VisionGraph client, for
example, stores it in `~/.config/visiongraph/last_hub_url` and falls back to live
discovery when the saved URL stops responding.

---

## Conventions

- **HTML routes** return `text/html` — pages meant for a browser.
- **`/api/*` routes** return `application/json` — meant for programs.
- **WebSocket routes** stream UTF-8 text or binary frames.
- The `register(...)` helper both serves an HTML page and records the path for the index
  page at `GET /`. That is why HTML pages appear in the auto-generated link list.

### Common query parameters

Camera and stream routes share a common set of query parameters:

| Param     | Type   | Default   | Applies to                          |
| --------- | ------ | --------- | ----------------------------------- |
| `res`     | string | `640x480` | `WxH`, e.g. `1920x1080`             |
| `fps`     | int    | `30`      | Frame rate                          |
| `quality` | int    | `50`      | JPEG quality, `1..100`              |
| `bitrate` | int    | `2000000` | H.264 bitrate in bits per second    |
| `minimal` | bool   | `false`   | Hides the viewer chrome when `true` |

---

## Core

### `GET /`

Returns an HTML index listing every registered route with a human-readable name.

**Response:** `200 text/html`

### `GET /layout`

Returns the currently active dashboard layout as JSON. The shape matches
`LayoutConfig` in [`app/src/main/assets/layout.json`](app/src/main/assets/layout.json):

```json
{
  "columns": 4,
  "presets": {
    "preset1": [
      { "id": 0, "type": "SENSOR:1", "spanX": 1, "aspRatio": 2.0 },
      { "id": 1, "type": "LOG", "spanX": 2, "aspRatio": 2.0 }
    ]
  }
}
```

Supported widget `type` values:

| Type         | Renders                          |
| ------------ | -------------------------------- |
| `LOG`        | Scrollable text log module       |
| `SENSOR:<n>` | Live value from sensor type `n`  |
| `CAMERA:<n>` | Camera stream for camera `n`     |
| `CAMERA:<n>:<fps>` | Stream with an explicit FPS |

**Response:** `200 application/json`

### `GET /log`

HTML form that POSTs to `/api/log` to push a message onto a dashboard module.

**Response:** `200 text/html`

### `POST /api/log`

Updates the text of a dashboard module. Body is JSON (`LogRequest`).

| Field  | Type   | Description                                     |
| ------ | ------ | ----------------------------------------------- |
| `id`   | string | Target module id. Parsed as an integer; non-numeric values resolve to `-1` and match nothing. |
| `text` | string | New text to display                             |

**Example:**

```bash
curl -X POST http://192.168.1.42:8080/api/log \
  -H 'Content-Type: application/json' \
  -d '{"id": "0", "text": "Motor armed"}'
```

**Responses:** `200 OK` on success · `400 Bad Request` if the body is not valid JSON.

---

## Camera

### `GET /api/cameras`

Lists every camera the phone exposes, including physical and logical multi-camera
sub-IDs. This is the primary machine-readable entry point for camera clients.

**Response:** `200 application/json` — array of [`CameraInfo`](#camerainfo).

```json
[
  {
    "id": "0",
    "facing": "Back",
    "type": "Wide",
    "focalLength": 4.75,
    "isPhysical": true,
    "supportedResolutions": [
      { "width": 640, "height": 480 },
      { "width": 1920, "height": 1080 }
    ]
  }
]
```

**Responses:** `200 OK` · `503 Service Unavailable` if the CameraX provider is still
initializing.

### `GET /camera`

HTML hub listing all cameras with links to viewers and direct MJPEG streams.

**Response:** `200 text/html`

### `GET /camera/{id}`

HTML camera viewer. Uses WebCodecs (H.264) by default and falls back to JPEG over
WebSocket on browsers without `VideoDecoder`.

| Param     | Type   | Default   | Description                          |
| --------- | ------ | --------- | ------------------------------------ |
| `minimal` | bool   | `false`   | Auto-hides the control chrome        |
| `res`     | string | `640x480` | Initial resolution                   |
| `fps`     | int    | `30`      | Initial frame rate                   |
| `quality` | int    | `50`      | Initial JPEG quality                 |

**Response:** `200 text/html`

### `GET /stream/{id}`

MJPEG (`multipart/x-mixed-replace`) stream. Works with any browser `<img>` tag and with
OpenCV's `cv2.VideoCapture`. This is the endpoint used by the VisionGraph client.

```bash
curl -o - http://192.168.1.42:8080/stream/0?res=640x480&fps=15 > /dev/null
```

```html
<img src="http://192.168.1.42:8080/stream/0?res=640x480&fps=15" />
```

**Response:** `200 multipart/x-mixed-replace; boundary=frame`

### `WS /camera-ws/{id}`

WebSocket that sends one JPEG per binary frame. Lower overhead than MJPEG for clients
that can handle framing themselves.

**Outgoing frames:** binary, each a complete JPEG image.

### `WS /h264-ws/{id}`

WebSocket that sends raw H.264 NAL units for hardware-decoded playback via WebCodecs.

- The first binary frame is the `SPS`/`PPS` configuration (codec config).
- Every subsequent frame is an encoded NAL unit to pass to `VideoDecoder`.

| Param     | Type   | Default   | Description            |
| --------- | ------ | --------- | ---------------------- |
| `res`     | string | `640x480` | Capture resolution     |
| `fps`     | int    | `30`      | Capture frame rate     |
| `bitrate` | int    | `2000000` | Encoder bitrate (bps)  |

**Outgoing frames:** binary H.264 NAL data.

---

## Sensors

### `GET /api/sensors`

Lists the device's hardware sensors.

**Response:** `200 application/json` — array of [`SensorInfo`](#sensorinfo).

```json
[
  {
    "id": 1,
    "name": "LSM6DSO Accelerometer",
    "vendor": "STMicroelectronics",
    "type": 1,
    "stringType": "android.sensor.accelerometer"
  }
]
```

### `GET /api/sensors/{type}`

Returns the most recent reading for a sensor type, without opening a WebSocket. The
reading comes from the flow's replay cache; sensors are only actively sampled while
something is subscribed.

**Response:** `200 application/json`

```json
{ "id": 1, "value": "{\"type\":1,\"timestamp\":1712345678901,\"values\":[0.1,-9.8,0.4]}" }
```

> The `value` field is a JSON-encoded [`SensorData`](#sensordata) string. Parse it twice
> if you need the numeric readings.

### `GET /sensors`

HTML hub listing available sensors.

**Response:** `200 text/html`

### `GET /sensors/{type}`

HTML viewer for a single sensor.

**Response:** `200 text/html`

### `WS /sensors-ws/{type}`

Streams live sensor readings. The connection keeps the sensor active for as long as it
is open; closing it stops the sensor.

**Outgoing frames:** UTF-8 text, each a JSON [`SensorData`](#sensordata) object.

```json
{ "type": 1, "timestamp": 1712345678901, "values": [0.12, -9.81, 0.44] }
```

**Incoming frames:** ignored. The socket is kept open by consuming whatever arrives.

---

## Hardware Telemetry

### `GET /api/hardware`

Returns a snapshot of battery, power, thermal, CPU, and memory telemetry.

**Response:** `200 application/json` — [`HardwareTelemetry`](#hardwaretelemetry).

```json
{
  "batteryLevelPct": 87,
  "batteryTempCelsius": 31.5,
  "batteryVoltageV": 4.12,
  "currentNowMa": -180.0,
  "powerWatts": 0.74,
  "isCharging": false,
  "chargingSource": "Unknown",
  "thermalStatus": "Normal",
  "cpuUsagePct": 12.5,
  "ramUsedMb": 2148,
  "ramTotalMb": 5820
}
```

### `GET /hardware`

HTML dashboard that polls `/api/hardware` once per second.

**Response:** `200 text/html`

---

## Flashlight

### `GET /api/flashlight`

Reads the current torch state.

**Response:** `200 application/json`

```json
{ "enabled": true }
```

### `POST /api/flashlight`

Sets the torch to an explicit state. Accepts either a JSON body
([`FlashlightRequest`](#flashlightrequest)) or an `enabled` query parameter. The query
parameter is a fallback for clients that cannot send a JSON body.

```bash
curl -X POST http://192.168.1.42:8080/api/flashlight \
  -H 'Content-Type: application/json' -d '{"enabled": true}'

curl -X POST "http://192.168.1.42:8080/api/flashlight?enabled=true"
```

**Response:** `200 application/json` — the resulting state.

### `POST /api/flashlight/toggle`

Toggles the torch regardless of its current state.

```bash
curl -X POST http://192.168.1.42:8080/api/flashlight/toggle
```

**Response:** `200 application/json`

```json
{ "enabled": false }
```

---

## UART Terminal

Talks to a USB serial device (Arduino, ESP32, etc.) through the phone's USB OTG port.
The app auto-connects on launch at **115200 baud, 8N1**.

### `GET /uart`

HTML terminal with a configurable macro button grid.

**Response:** `200 text/html`

### `WS /uart-ws`

Bidirectional terminal. Incoming frames from the serial device are forwarded as text;
text frames sent by the client are written to the serial port.

**Outgoing frames:** UTF-8 serial data received from the device.
**Incoming frames:** UTF-8 text, written verbatim to the device.

### `POST /uart-send`

Writes a raw text payload to the serial port. Body is plain text, not JSON.

```bash
curl -X POST http://192.168.1.42:8080/uart-send -d 'G28'
```

**Response:** `200 OK`

---

## Bluetooth Terminal

Connects to a paired Bluetooth Classic SPP device.

### `GET /bluetooth`

HTML terminal. Includes a `<select>` populated from the phone's paired devices and a
Connect button.

**Response:** `200 text/html`

### `POST /bluetooth-connect`

Connects to a device by MAC address. Body is the raw address string.

```bash
curl -X POST http://192.168.1.42:8080/bluetooth-connect -d '00:11:22:33:44:55'
```

**Response:** `200 text/plain` — a status string such as `Connected to HC-05`,
`Connection failed: ...`, or `Device not found`.

### `WS /bluetooth-ws`

Bidirectional Bluetooth terminal.

**Outgoing frames:** UTF-8 data received from the device.
**Incoming frames:** UTF-8 text, subject to input validation (see below).

### `POST /bluetooth-send`

Sends a payload to the connected device. Body is plain text.

```bash
curl -X POST http://192.168.1.42:8080/bluetooth-send -d 'LED_ON'
```

**Response:** `200 OK` — returned whether or not the payload passed validation.

### Input validation

Both `/bluetooth-ws` and `/bluetooth-send` drop a payload unless it satisfies all three
conditions:

| Rule                    | Requirement      |
| ----------------------- | ---------------- |
| Not blank               | `text.isNotBlank()` |
| Length limit            | `text.length <= 200` |
| No reserved prefix      | `!text.startsWith("__")` |

The `__` prefix is reserved for internal control frames.

---

## TCP Console

Opens a TCP client socket from the phone to another host on the network.

### `GET /tcp`

HTML console with IP/port inputs.

**Response:** `200 text/html`

### `POST /tcp-connect`

Connects to a remote host. Body is JSON ([`TcpConnectRequest`](#tcpconnectrequest)).

```bash
curl -X POST http://192.168.1.42:8080/tcp-connect \
  -H 'Content-Type: application/json' -d '{"ip": "192.168.1.10", "port": 8888}'
```

| Field  | Type   | Default | Description            |
| ------ | ------ | ------- | ---------------------- |
| `ip`   | string | `""`    | Remote host address    |
| `port` | int    | `8888`  | Remote port            |

**Response:** `200 text/plain` status string · `400 Bad Request` on invalid JSON.

### `WS /tcp-ws`

Bidirectional TCP relay.

**Outgoing frames:** UTF-8 data read from the remote socket.
**Incoming frames:** UTF-8 text, written to the remote socket.

### `POST /tcp-send`

Writes a raw text payload to the remote socket. Body is plain text.

```bash
curl -X POST http://192.168.1.42:8080/tcp-send -d 'STATUS?'
```

**Response:** `200 OK`

---

## Data Schemas

All schemas are `@Serializable` Kotlin data classes. Field names below match the JSON
keys exactly.

### `CameraInfo`

| Field                | Type                          | Description                          |
| -------------------- | ----------------------------- | ------------------------------------ |
| `id`                 | string                        | Camera id used in stream URLs        |
| `facing`             | string                        | `Front`, `Back`, or `External`       |
| `type`               | string                        | Lens type, e.g. `Wide`, `UltraWide`  |
| `focalLength`        | float                         | Physical focal length                |
| `isPhysical`         | bool                          | `false` for logical multi-camera IDs |
| `supportedResolutions` | [`Resolution`](#resolution)[] | Reportable capture sizes          |

### `Resolution`

| Field    | Type | Description      |
| -------- | ---- | ---------------- |
| `width`  | int  | Pixels           |
| `height` | int  | Pixels           |

### `SensorInfo`

| Field        | Type   | Description                          |
| ------------ | ------ | ------------------------------------ |
| `id`         | int    | Sensor handle                        |
| `name`       | string | Human-readable sensor name           |
| `vendor`     | string | Manufacturer                         |
| `type`       | int    | Android sensor type constant         |
| `stringType` | string | e.g. `android.sensor.accelerometer` |

### `SensorData`

| Field       | Type      | Description                       |
| ----------- | --------- | --------------------------------- |
| `type`      | int       | Android sensor type constant      |
| `timestamp` | long      | Nanosecond timestamp              |
| `values`    | float[]   | Raw readings; meaning depends on sensor type |

For an accelerometer, `values` is `[x, y, z]` in m/s².

### `HardwareTelemetry`

| Field                 | Type   | Description                              |
| --------------------- | ------ | ---------------------------------------- |
| `batteryLevelPct`     | int    | Charge percentage                        |
| `batteryTempCelsius`  | float  | Battery temperature                      |
| `batteryVoltageV`     | float  | Instantaneous voltage                    |
| `currentNowMa`        | float  | Signed current; negative when discharging|
| `powerWatts`          | float  | Instantaneous power draw                 |
| `isCharging`          | bool   | Whether a charger is connected           |
| `chargingSource`      | string | `AC`, `USB`, or `Unknown`                |
| `thermalStatus`       | string | Android thermal status name              |
| `cpuUsagePct`         | float  | Aggregate CPU utilisation                |
| `ramUsedMb`           | long   | Used memory in megabytes                 |
| `ramTotalMb`          | long   | Total memory in megabytes                |

### `LogRequest`

| Field  | Type   | Description                     |
| ------ | ------ | ------------------------------- |
| `id`   | string | Target module id                |
| `text` | string | New module text                 |

### `TcpConnectRequest`

| Field  | Type   | Default | Description         |
| ------ | ------ | ------- | ------------------- |
| `ip`   | string | `""`    | Remote host address |
| `port` | int    | `8888`  | Remote port         |

### `FlashlightRequest`

| Field     | Type | Default | Description          |
| --------- | ---- | ------- | -------------------- |
| `enabled` | bool | `false` | Desired torch state  |

### `PairedDeviceInfo`

Returned by the Bluetooth manager to populate the HTML terminal's device picker. Not
currently exposed as a JSON endpoint.

| Field     | Type   | Description          |
| --------- | ------ | -------------------- |
| `name`    | string | Device name          |
| `address` | string | MAC address          |

---

## Errors

| Status              | When                                                     |
| ------------------- | -------------------------------------------------------- |
| `200 OK`            | Request succeeded                                        |
| `400 Bad Request`   | Malformed JSON body (`/api/log`, `/tcp-connect`)         |
| `503 Service Unavailable` | CameraX provider not yet ready (`/api/cameras`)    |

Terminal send endpoints (`/uart-send`, `/bluetooth-send`, `/tcp-send`) return `200` even
when the underlying manager drops the payload, so check the terminal UI or logs for
delivery failures.

---

## Quick Examples

**Find the phone:**

```bash
python pc_files/android_domain.py
```

**List cameras and pick the best one:**

```bash
curl -s http://192.168.1.42:8080/api/cameras | python -m json.tool
```

**Watch a stream in OpenCV:**

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

**Stream the accelerometer over a WebSocket** (using [`wscat`](https://github.com/websockets/wscat)):

```bash
npx wscat -c ws://192.168.1.42:8080/sensors-ws/1
```

**Drive an ESP32 over USB serial:**

```bash
curl -X POST http://192.168.1.42:8080/uart-send -d 'G28'
```

**Read hardware telemetry:**

```bash
curl -s http://192.168.1.42:8080/api/hardware
```

**Toggle the flashlight:**

```bash
curl -X POST http://192.168.1.42:8080/api/flashlight/toggle
```
