# CommandHub — Problems Ordered by Fix Priority

- Repository: `Jedimig08/CommandHub`
- Branch reviewed: `main`
- Commit reviewed: `a1ee218` (2026-11-12)
- Review type: static code review. No Gradle build/lint was run because the review machine had no JDK or Android SDK. Build-related findings still need confirmation with a clean build.
- Purpose: prioritize what to fix first. This file does not change application code.

## Priority legend

- **P0 — Critical:** fix before exposing the app to any network or adding features.
- **P1 — High:** fix before calling the project stable; affects core reliability, camera, permissions, and the open GitHub issues.
- **P2 — Medium:** correctness, build, documentation, and test hygiene.
- **P3 — Feature work:** planned features and lower-risk improvements.

## Open GitHub issue mapping

| Issue | Title | Priority | Main sections |
|---|---|---|---|
| #3 | Bluetooth terminal constant pinging | P1 | P1-13 |
| #4 | Adding auth | P0 | P0-01, P0-02, P0-05 |
| #5 | The stream freezes | P1 | P1-03, P1-04, P1-05, P1-06 |
| #6 | Privacy and access | P1 | P1-11, P1-12, P1-14 |
| #7 | Microphone streaming | P3 | P3-01 |

---

# P0 — Critical security and privacy

## P0-01 — The entire HTTP/WebSocket API is unauthenticated

- **Evidence:** server binds `0.0.0.0` at `app/src/main/java/com/commandhub/viewmodel/network.kt:102`; public control routes include `CameraRoutes.kt:210`, `UartRoutes.kt:53`, `BluetoothRoutes.kt:89`, `TcpRoutes.kt:80`, `CoreRoutes.kt:62`; documented limitation in `SECURITY.md:28`.
- **Impact:** anyone who can reach the phone can read camera/sensor data, toggle the torch, and send arbitrary data to UART, Bluetooth, and TCP.
- **Fix:** add authentication middleware in front of all routes except `/pair`; generate a random token on first launch; support a session cookie and/or bearer token; add a pairing page; rate-limit auth attempts.
- **Done when:** unauthenticated requests to `/stream/0`, `/camera-ws/0`, `/uart-send`, and `/api/flashlight/toggle` are rejected.

## P0-02 — No CSRF or Origin protection for state-changing requests

- **Evidence:** simple POST endpoints accept empty or `text/plain` bodies, e.g. `FlashlightRoutes.kt:26`, `UartRoutes.kt:53`, `BluetoothRoutes.kt:89`, `TcpRoutes.kt:74`; `POST /api/settings/processing-url` also accepts a query-string fallback at `CoreRoutes.kt:62-72`.
- **Impact:** a malicious website visited by someone on the LAN can trigger hardware actions with a cross-origin form POST.
- **Fix:** validate `Origin`; require a custom header/token or a `SameSite=Strict` session cookie; reject wrong content types; test cross-origin form requests.
- **Done when:** a cross-origin HTML form cannot toggle hardware or change settings.

## P0-03 — WebView loads arbitrary external URLs with unsafe settings

- **Evidence:** JavaScript, file access, and mixed content are enabled in `MainActivity.kt:112-118`; the processing URL is user/attacker-settable via `CoreRoutes.kt:62-72` and loaded by `index.html:61-68`; JavaScript is injected into every external page in `MainActivity.kt:95-108`.
- **Impact:** an attacker can make the app load attacker-controlled HTML/JS in a privileged WebView. `javascript:`/`data:` URLs can execute script through `window.location.href = u`. File access and mixed content increase the impact.
- **Fix:** allow only `http`/`https`; set `allowFileAccess = false`; disable mixed content; add a strict CSP; open external processing UIs in a Custom Tab or a separate hardened WebView; stop injecting scripts into untrusted pages.
- **Done when:** `javascript:`, `data:`, `file:`, and non-allowlisted URLs are rejected.

## P0-04 — XSS through unescaped device data

- **Evidence:** terminal RX uses `innerHTML` at `app/src/main/assets/web/terminal.html:150-157` and `:165`; the existing `escapeHtml()` helper at `:179` is not applied to RX data; Bluetooth device names are inserted unescaped at `BluetoothRoutes.kt:31-33`; sensor name/vendor at `SensorRoutes.kt:25-31`; camera fields at `CameraRoutes.kt:65-80`.
- **Impact:** a malicious or malformed serial/Bluetooth/TCP payload can execute JavaScript in the control UI and call every public API.
- **Fix:** use `textContent` for device-controlled strings; HTML-escape every dynamic value passed through `WebAssetLoader`; remove `innerHTML` for RX/log data.
- **Done when:** payloads such as `<img src=x onerror=alert(1)>` render as text and never execute.

## P0-05 — Arbitrary TCP connect creates an SSRF/pivot primitive

- **Evidence:** `POST /tcp-connect` at `app/src/main/java/com/commandhub/server/routes/TcpRoutes.kt:80` calls `TcpManager.connect(ip, port)` at `TcpManager.kt:23` without auth or target restrictions.
- **Impact:** an unauthenticated LAN client can make the phone connect to arbitrary local/private hosts and ports, then send data through the phone.
- **Fix:** require authentication; add an optional allowlist/private-range policy; warn on non-private targets; rate-limit connections; add a connect timeout.
- **Done when:** unauthenticated or disallowed targets are rejected.

## P0-06 — Unbounded request, frame, and camera parameters

- **Evidence:** WebSocket `maxFrameSize = Long.MAX_VALUE` at `network.kt:107`; no body limits on `receiveText()` in `UartRoutes.kt:54`, `BluetoothRoutes.kt:90`, `TcpRoutes.kt:75`; camera width/height/fps/quality/bitrate are not clamped at `CameraRoutes.kt:138-145`, `:164-172`, `:184-190`.
- **Impact:** a LAN client can exhaust memory/CPU or crash the app with huge frames, bodies, camera resolution, or H.264 bitrate.
- **Fix:** clamp camera parameters; set body and WebSocket frame limits; cap concurrent streams/connections; add basic rate limiting.
- **Done when:** oversized requests return an error and cannot crash the app.

## P0-07 — Cleartext traffic, backups, and payload logging

- **Evidence:** `android:usesCleartextTraffic="true"` at `AndroidManifest.xml:21`; `android:allowBackup="true"` with empty rules at `AndroidManifest.xml:20`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`; full device payloads logged at `BluetoothManager.kt:105` and `UartManager.kt`/Bluetooth read/write paths.
- **Impact:** credentials/tokens will be sniffable if auth is added over HTTP; app data may be backed up; device payloads leak into logcat.
- **Fix:** use a network security config limited to local addresses where possible; disable or tightly scope backups; stop logging raw TX/RX payloads; for auth, consider self-signed HTTPS or explicitly document trusted-LAN-only use.
- **Done when:** no raw payloads are logged and backup rules are explicit.

---

# P1 — High: lifecycle, reliability, and open issues

## P1-01 — Foreground service is dead code; server lifetime is Activity-scoped

- **Evidence:** `service/CommandHubService.kt:20` exists and is declared in `AndroidManifest.xml:46-49`, but no code calls `startService`/`startForegroundService`. The real server starts in `DashboardViewModel.init` at `DashboardViewModel.kt:52-72`.
- **Impact:** the server stops when the Activity/ViewModel is cleared; the advertised background server does not exist; remote streams freeze when the app leaves the foreground.
- **Fix:** create one application-scoped container; start exactly one server from a proper foreground service; keep the Activity as an observer/controller only.
- **Done when:** the server can outlive Activity recreation and is explicitly started/stopped from the UI.

## P1-02 — Duplicate server and manager construction

- **Evidence:** `DashboardViewModel.kt:12-17` and `CommandHubService.kt:36-41` each create UART, Bluetooth, camera, sensor, TCP, and hardware managers; the service variant also uses `layoutProvider = { "{}" }` and a no-op log callback at `CommandHubService.kt:46-53`.
- **Impact:** if the service is used, it creates a second server on port 8080 and a second set of hardware managers, splitting state and conflicting with the ViewModel.
- **Fix:** remove the service copy or the ViewModel copy; inject the same managers into both; make the server construction single-owner.

## P1-03 — Camera is bound to the Activity lifecycle

- **Evidence:** `MainActivity.kt:63` passes the Activity as lifecycle owner; CameraX binds at `CameraManager.kt:228-234` and `:336`.
- **Impact:** backgrounding/rotation tears down or rebinds the camera while remote clients remain connected; camera frames stop without the server knowing.
- **Fix:** bind camera to a foreground-service/process lifecycle; ref-count viewers; stop only after the last viewer leaves with a grace period.
- **Done when:** streaming survives Activity recreation and behaves deliberately when the app is backgrounded.

## P1-04 — Camera viewer has no WebSocket reconnect (root of issue #5)

- **Evidence:** `app/src/main/assets/web/camera_viewer.html:67-91` opens a WebSocket but defines no `onclose`/`onerror` reconnect; `MainActivity.kt:119` always loads `/` on recreation.
- **Impact:** any socket close during rotation/rebind leaves the page frozen; the user must close and reopen the tab.
- **Fix:** add exponential-backoff reconnect, `onerror`, and `visibilitychange` handling; preserve/restore the viewer route across rotation.
- **Done when:** rotating the phone resumes the stream without reopening the tab.

## P1-05 — Camera start races the lifecycle owner

- **Evidence:** `getFlow()` only schedules `startCamera()` when `lastLifecycleOwner` is non-null at `CameraManager.kt:256-262`; if a client connects before the new owner is registered, nothing starts. `startCamera()` returns early based on the old owner at `CameraManager.kt:277-289`.
- **Impact:** a WebSocket can collect a flow that never emits, producing a permanent freeze after rotation/startup.
- **Fix:** store the pending camera config and start it when the lifecycle owner becomes available; make start failures visible; avoid early-return decisions based on stale owners.

## P1-06 — Camera state is globally shared and mode-conflicted

- **Evidence:** `cameraFlows`/`h264Flows` are plain maps at `CameraManager.kt:89-90`; JPEG and H.264 share `activeCameraId`, `activeResolution`, `activeFps`, `recycledNv21Buffer`, and `encoding` at `:86-101`; `provider.unbindAll()` runs on every config at `:292` and `:581`.
- **Impact:** two viewers with different settings continually rebind the camera; switching JPEG/H.264 can leave the server thinking a pipeline is active while the wrong analyzer is bound; concurrent map access can throw.
- **Fix:** serialize camera state on the main thread; track the active pipeline mode explicitly; ref-count viewers; remove `unbindAll()` from the hot path.

## P1-07 — Camera/encoder resources are not released when viewers leave

- **Evidence:** `processImage()` returns early when `flow.subscriptionCount == 0` at `CameraManager.kt:414-417`, but the camera stays bound and encoder/executors stay alive; `stopStreaming()` is only called from `onCleared()`/service destroy.
- **Impact:** battery drain, camera privacy indicator stays on, retained buffers.
- **Fix:** stop the pipeline after an idle grace period; shut down executors; release MediaCodec and retained byte buffers.

## P1-08 — Route serialization will likely return 500 for mixed-type maps

- **Evidence:** `CameraRoutes.kt:228` returns `mapOf("success" to updated, "stats" to cameraManager.getCameraStats())`; `SensorRoutes.kt:78` returns `mapOf("id" to type, "value" to latestValue)`. Both infer `Map<String, Any>`.
- **Impact:** Ktor's kotlinx-serialization converter has no serializer for `Any`; these endpoints very likely throw `SerializationException` at runtime.
- **Fix:** return `@Serializable` DTOs (`CameraControlResponse`, `SensorReadingResponse`); add Ktor route tests.
- **Done when:** both endpoints return valid JSON in a `testApplication` test.

## P1-09 — NV21 conversion is wrong on common camera layouts

- **Evidence:** optimized branch at `CameraManager.kt:468-472` copies the V plane into the NV21 buffer and then copies only one U byte; the same logic appears in `:684-688`.
- **Impact:** almost all U samples can be unset, producing wrong colors/green-purple video on devices where U and V are separate planes with `pixelStride == 2`.
- **Fix:** copy U and V samples from their separate planes with bounds checks, or use `ImageProxy.toBitmap()`/libyuv; add a synthetic-unit test.

## P1-10 — H.264 SPS/PPS delivery race

- **Evidence:** `CameraRoutes.kt:145-151` calls `getH264SpsPps()` immediately after starting the encoder; the data is only populated later at `CameraManager.kt:818-823`.
- **Impact:** the first client usually receives no configuration and cannot decode the stream.
- **Fix:** send SPS/PPS after the encoder emits them; include keyframe metadata; test with a browser WebCodecs client.

## P1-11 — Issue #6: camera failure blocks/degrades unrelated modules

- **Evidence:** only `CAMERA` is requested at launch in `MainActivity.kt:62-68`; CameraX initialization calls `future.get()` without try/catch at `CameraManager.kt:212-225`; `/camera` reloads forever at `CameraRoutes.kt:48-59`.
- **Impact:** denying/blocking camera permission or losing the camera can crash the app or leave the user in an endless loading loop; other modules appear blocked.
- **Fix:** catch camera-init/enumeration errors; add a camera error state and a clear permission page; keep the server and all other managers independent of camera availability.
- **Done when:** denying camera permission still allows sensors, UART, Bluetooth, TCP, and telemetry to work.

## P1-12 — Issue #6: UI advertises unavailable/implemented features

- **Evidence:** `app/src/main/assets/web/index.html:29-54` hardcodes cards for every module; there is no capability check. There is no microphone implementation in the code despite the issue mentioning a microphone entry.
- **Impact:** users see modules that do not exist or cannot work on their device; privacy expectations are unclear.
- **Fix:** add `GET /api/capabilities`; render cards dynamically; hide unavailable/unimplemented modules; remove the microphone card until issue #7 exists.
- **Done when:** a device without a camera/Bluetooth/USB host only shows modules it can actually use.

## P1-13 — Issue #3: terminal heartbeat is forwarded to hardware and intervals leak

- **Evidence:** `terminal.html:172-176` sends the literal text `"PING"` every 2 seconds; all transports forward text frames to the device (`BluetoothRoutes.kt:77-82`, `UartRoutes.kt:43-47`, `TcpRoutes.kt:64-68`). `setInterval` is created inside `connectWs()` and `onclose` calls `connectWs()` again at `terminal.html:160-177`, multiplying timers on every reconnect. `lastActivity` is not updated on RX.
- **Impact:** Bluetooth/UART/TCP devices receive unwanted `PING` commands; timer multiplication increases traffic over time.
- **Fix:** remove the application-level heartbeat or use a reserved control message that is never forwarded; create one interval outside `connectWs`; clear it on close; update activity on RX; add the toggle requested in #3.
- **Done when:** no `PING` reaches the hardware after 10+ minutes idle and reconnect does not increase ping frequency.

## P1-14 — Missing runtime permissions and uncaught SecurityExceptions

- **Evidence:** only `CAMERA` is requested (`MainActivity.kt:62-68`); Bluetooth is suppressed with `@SuppressLint("MissingPermission")` at `BluetoothManager.kt:20`; `connect()` catches only `IOException` at `:56-70`; `getPairedDevices()` silently returns an empty list on `SecurityException` at `:47-50`; `POST_NOTIFICATIONS` is absent from the manifest.
- **Impact:** Bluetooth fails silently or returns 500 on Android 12+; foreground-service notifications are not visible on Android 13+.
- **Fix:** request `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`, `POST_NOTIFICATIONS`, and any needed location permission lazily with rationale; handle `SecurityException` explicitly and return actionable errors.
- **Done when:** Bluetooth works after permission grant and shows a clear message when denied.

## P1-15 — Foreground-service type is wrong and Android 14/15 rules are not handled

- **Evidence:** `android:foregroundServiceType="dataSync"` at `AndroidManifest.xml:48` and `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` at `CommandHubService.kt:64`.
- **Impact:** Android 15+ limits `dataSync` foreground services to roughly six hours per day; camera/microphone background use requires the corresponding FGS types; the service may be stopped or throw on newer Android versions.
- **Fix:** choose the correct FGS type(s) for the actual workload (`camera`, `microphone`, possibly `connectedDevice`/`specialUse`); implement `onTimeout`; start only from valid foreground contexts.

## P1-16 — Wake lock is held indefinitely

- **Evidence:** `network.kt:89-96` acquires a partial wake lock with no timeout and suppresses the lint warning.
- **Impact:** battery drain; lock may be held longer than intended.
- **Fix:** use a bounded timeout or tie it explicitly to the foreground server lifecycle; release in all stop paths.

## P1-17 — Server stop can block the main thread and throw during NSD cleanup

- **Evidence:** `DashboardViewModel.onCleared()` calls `server.stop()` at `DashboardViewModel.kt:78`; `DashboardServer.stop()` calls `server?.stop(1000, 2000)` and `nsdManager?.unregisterService(registrationListener)` at `network.kt:185-191`.
- **Impact:** ANR risk; NSD unregister can throw if registration never succeeded.
- **Fix:** stop on a background dispatcher; guard NSD unregister with try/catch and null checks; make shutdown idempotent.

## P1-18 — Blocking, unordered transport I/O

- **Evidence:** TCP `connect()` blocks with `join(5000)` and can leave the background thread running after timeout (`TcpManager.kt:23-46`); TCP `send()` spawns a thread per message (`:72-82`); Bluetooth `send()` writes from arbitrary Ktor threads without a lock (`BluetoothManager.kt:103-112`); UART writes can block for 2 seconds (`UartManager.kt:117-125`); `isRunning` is not `@Volatile` (`TcpManager.kt:17`, `BluetoothManager.kt:29`).
- **Impact:** out-of-order writes, thread exhaustion, data corruption, leaked sockets, and blocked request threads.
- **Fix:** use `Dispatchers.IO`, one writer mutex/actor per transport, `Socket.connect(address, timeout)`, and coroutine cancellation/volatile stop flags.

## P1-19 — Hardware telemetry loop leaks and reports misleading RAM

- **Evidence:** `HardwareManager.kt:47-54` launches a non-cancellable infinite loop; `stop()` is empty at `:181-183`; RAM is the app's Java heap at `:118-124`, not system memory.
- **Impact:** the loop continues after the ViewModel/service is destroyed; telemetry labels are misleading.
- **Fix:** keep the `Job` and cancel the scope in `stop()`; use `ActivityManager.MemoryInfo` for system RAM or rename the fields to “app heap.”

## P1-20 — Sensor listeners are shared but unregistered per client

- **Evidence:** `SensorRoutes.kt:55-68` calls `stopSensor()` when one WebSocket closes; `SensorManager.kt:49-50` uses non-thread-safe mutable maps; `stopAll()` at `:176-181` does not stop the IMU listener or reset `isImuRegistered`.
- **Impact:** one viewer disconnecting stops the sensor for all viewers; concurrent access can throw; IMU listeners leak.
- **Fix:** reference-count listeners per sensor type; protect state with a mutex; stop IMU in `stopAll()` and reset its flag.

## P1-21 — IMU endpoints are documented but not implemented

- **Evidence:** `SensorManager.kt:104-168` contains `getImuFlow()`, but nothing calls it; there is no `/ws/sensors/imu` or `/api/sensors/imu` route. README/API have been corrected; `CHANGELOG.md:21` still claims them.
- **Impact:** users reading the CHANGELOG expect a feature that does not exist.
- **Fix:** either register the routes and a client page, or remove the CHANGELOG claim. Prefer implementing and testing the routes since the manager logic already exists.

---

# P2 — Medium: correctness, build, documentation, tests

## P2-01 — README/API/CHANGELOG describe removed or nonexistent features

- **Evidence:** README/API were updated to describe the WebView Control Center, current packages, routes, settings, and schemas. `CONTRIBUTING.md` still tells contributors to edit `ui/DashboardPage.kt`; `CHANGELOG.md` still claims IMU endpoints; `README.md:408` still says JDK 17+ while the build uses Java 11.
- **Impact:** remaining contributor docs are stale and setup expectations differ.
- **Fix:** update CONTRIBUTING and CHANGELOG; reconcile the JDK target.

## P2-02 — `/api/log` and `/layout` are effectively dead

- **Evidence:** `onLogReceived` is a no-op in `DashboardViewModel.kt:69` and `CommandHubService.kt:53`; `layoutProvider` always returns `"{}"` at `DashboardViewModel.kt:55` and `CommandHubService.kt:46`; `layout.json` is unused. README/API now mark `/layout` as legacy.
- **Impact:** the legacy routes still do nothing, but they are no longer advertised as active features.
- **Fix:** implement persistence and an update path, or remove the routes/pages.

## P2-03 — Auto-generated endpoint index is unused

- **Evidence:** `CoreRoutes.kt:111-117` builds `listItems` and injects it into `index.html`, but the template has no `{{listItems}}` placeholder. The README claim has been removed.
- **Impact:** dead code remains and new routes are still not discoverable from the index.
- **Fix:** add the placeholder and render the list, or remove the dead code.

## P2-04 — Kotlin serialization plugin version likely mismatches Kotlin

- **Evidence:** `app/build.gradle.kts:4` uses serialization plugin `2.4.21`; `gradle/libs.versions.toml:9` uses Kotlin `2.3.10`.
- **Impact:** clean builds may fail with compiler/plugin incompatibility.
- **Fix:** align the serialization plugin version with the Kotlin version (or move it into the version catalog); verify with a clean build.

## P2-05 — Inconsistent Java/JDK target

- **Evidence:** Java 11 source/target at `app/build.gradle.kts:32-35`; README says JDK 17+ at `README.md:408`; CHANGELOG says JDK 25 at `CHANGELOG.md:78`.
- **Impact:** confusing setup and possible compiler warnings/failures.
- **Fix:** pick one target, set `jvmTarget` explicitly, and update docs.

## P2-06 — Release/versioning hygiene

- **Evidence:** `versionCode = 1`, `versionName = "1.0"` at `app/build.gradle.kts:17-18` despite v1.2.0; `isMinifyEnabled = false` at `:25`; unused `androidx.benchmark.traceprocessor` in production at `:53`.
- **Impact:** stale version metadata, larger APK, unnecessary dependency risk.
- **Fix:** derive version from tags/flavors, enable minify/shrink for release, remove the benchmark dependency, and add release signing.

## P2-07 — No CI workflow

- **Evidence:** no `.github/workflows` directory in the current tree; the PR template asks for Gradle checks that are not automated.
- **Impact:** regressions such as the mixed-map serialization issue are not caught.
- **Fix:** add CI for `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug`, and a Markdown link/format check.

## P2-08 — Tests are too thin

- **Evidence:** unit tests only cover JSON serialization in `app/src/test/java/com/commandhub/CommandHubUnitTest.kt`; `ExampleUnitTest.kt` is the default placeholder; no Ktor route tests, camera-conversion tests, or lifecycle tests.
- **Impact:** runtime serialization and video-color bugs are not detected.
- **Fix:** add `testApplication` route tests (auth, limits, DTOs), synthetic NV21 tests, URL-validation tests, and instrumentation smoke tests.

## P2-09 — mDNS desktop helper opens the first HTTP service it finds

- **Evidence:** `pc_files/android_domain.py:7-26` listens to `_http._tcp.local.` and opens the first result without checking for the `command-hub` service name.
- **Impact:** it may open a printer, router, or unrelated service.
- **Fix:** filter by service name/type and add error handling/timeouts.

## P2-10 — mDNS re-registration is not fully thread-safe

- **Evidence:** `/api/settings/publish-domain` calls `registerService()` from a Ktor thread at `CoreRoutes.kt:75-78`; `registerService()` reads/mutates `registrationListener` at `network.kt:146-183`.
- **Impact:** race during publish/rotate; unregister can throw.
- **Fix:** serialize NSD operations on a single dispatcher; make publish idempotent.

## P2-11 — WebView instance is never destroyed

- **Evidence:** `MainActivity.kt:78-124` creates a WebView in `AndroidView` but there is no release/destroy path; `webViewInstance` holds the old Activity WebView across recreation.
- **Impact:** memory/Activity leak.
- **Fix:** use `AndroidView`'s `onRelease`/`DisposableEffect` to destroy the WebView.

## P2-12 — Notification uses the adaptive launcher icon

- **Evidence:** `CommandHubService.kt:59` uses `R.mipmap.ic_launcher` as the small icon.
- **Impact:** notification icon may render as a white square or incorrectly on some devices.
- **Fix:** add a dedicated monochrome notification icon drawable.

## P2-13 — Settings writes from non-main threads

- **Evidence:** `/api/settings/screen-on/toggle` and `/api/settings/sensors-speed/toggle` update Compose state/Android SensorManager from Ktor threads at `CoreRoutes.kt:80-90`.
- **Impact:** potential snapshot/threading issues.
- **Fix:** dispatch settings mutations to the main thread.

## P2-14 — Inconsistent persistence of settings

- **Evidence:** `keepScreenOn` and `processingUrl` are persisted at `DashboardViewModel.kt:19-30` and `:43-46`; `sensorDelayFastest` is not persisted at `:32-41`.
- **Impact:** sensor mode resets on restart unexpectedly.
- **Fix:** persist all settings consistently and document them.

## P2-15 — Dead code and duplicate helpers

- **Evidence:** `getBuffer()` in UART/Bluetooth, unused byte counters, unused `ImuData` routes, duplicated NV21 conversion in JPEG/H.264 paths, `registeredEndpoints` partially unused, and duplicated server construction.
- **Impact:** larger maintenance surface and more places for bugs.
- **Fix:** remove or consolidate dead code; extract the NV21 conversion into one testable helper.

## P2-16 — No idle timeout for camera or sensors

- **Evidence:** camera remains bound after viewers leave (`CameraManager.kt:414-417`); sensors remain registered until a client disconnects (`SensorRoutes.kt:55-68`); hardware telemetry runs continuously (`HardwareManager.kt:47-54`).
- **Impact:** battery and privacy cost when nobody is using the device.
- **Fix:** add idle timeouts and release hardware when unused.

---

# P3 — Feature work

## P3-01 — Issue #7: Microphone streaming

- **Current state:** not implemented. No `RECORD_AUDIO`, `AudioRecord`, audio route, or microphone foreground-service type.
- **Suggested design:** add runtime `RECORD_AUDIO`; use `AudioRecord` on an IO dispatcher; start with 16 kHz mono PCM16; stream over `WS /mic-ws` with a metadata frame and backpressure; optionally encode Opus with `MediaCodec`; play in the browser with Web Audio/AudioWorklet; require auth; add a `/mic` page with a start/stop control and visible recording indicator.
- **Android constraints:** Android 14+ background microphone use requires the `microphone` foreground-service type and while-in-use restrictions; add `FOREGROUND_SERVICE_MICROPHONE`.
- **Done when:** live audio streams to an authenticated browser, stops cleanly when the last client leaves, and never records without a visible indicator.

## P3-02 — Closed issue #1: editable dashboard modules

- **Current state:** the old Compose module editor was removed; the current UI is a WebView and `layout.json` is unused.
- **Suggested design:** add `GET/POST /api/layout`, persist the JSON, render the dashboard from it, and build the editor in the web UI; decide whether presets/reordering are still in scope.

## P3-03 — Closed issue #2: Termux companion files

- **Current state:** no `termux/` directory in the repository.
- **Suggested design:** add documented setup scripts for running the desktop/helper tooling in Termux, with a README section and tested commands.

## P3-04 — Future features

- BLE support.
- MAVLink.
- Dashboard graph visualization.
- Plugin system.
- Wi-Fi communication modules.
- Cloud synchronization.
- Improved remote control.

Do not start these until P0 and P1 are complete; they add new attack surface and lifecycle complexity.

---

# Recommended fix order

1. **P0-01, P0-02, P0-05** — authentication, CSRF, and TCP target restrictions.
2. **P0-03, P0-04** — WebView hardening and XSS escaping.
3. **P0-06, P0-07** — input limits and privacy cleanup.
4. **P1-01, P1-02, P1-15** — one server owner, one manager set, correct foreground service.
5. **P1-03 through P1-07, P1-11, P1-12** — camera lifecycle, reconnect, rotation (issue #5), and capability/permission handling (issue #6).
6. **P1-13** — remove PING and fix terminal heartbeat (issue #3).
7. **P1-08, P1-09, P1-10, P1-20, P1-21** — serialization, NV21, SPS/PPS, sensors, IMU.
8. **P1-14, P1-16 through P1-19** — permissions, wake lock, shutdown, transport I/O, hardware loop.
9. **P2-01 through P2-16** — docs, build, CI, tests, and cleanup.
10. **P3-01** — microphone streaming (issue #7), after auth and lifecycle work.

## Suggested PR sequence

1. Auth + CSRF + input limits.
2. WebView/XSS hardening.
3. Single server owner + foreground service + camera lifecycle.
4. WebSocket reconnect + rotation + PING fix (issues #5 and #3).
5. Correctness: DTOs, NV21, SPS/PPS, sensors, IMU.
6. Capability detection + lazy permissions (issue #6).
7. Docs truth pass + CI + build fixes.
8. Microphone streaming (issue #7).

Each PR should include its own tests and update `API.md`/`CHANGELOG.md` when behavior changes.
