package com.example.uitest.viewmodel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

class DashboardServer(
    private val context: Context,
    private val port: Int = 8080,
    private val layoutProvider: () -> String,
    private val uartManager: UartManager,
    private val bluetoothManager: BluetoothClassicManager,
    private val cameraManager: CameraManager,
    private val sensorManager: SensorManager,
    private val tcpManager: TcpManager,
    private val onLogReceived: (String, String) -> Unit,
) {

    private fun isValidBluetoothInput(text: String): Boolean {
        return (text.isNotBlank()) &&
                (text.length <= 200) &&
                (!text.startsWith("__"))
    }

    private fun terminalPage(
        title: String,
        wsEndpoint: String,
        sendEndpoint: String,
        extraControls: String = "",
    ): String {

        return """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body {
                    font-family: 'Courier New', monospace;
                    background: #000;
                    color: #0f0;
                    padding: 20px;
                }

                #console {
                    border: 1px solid #333;
                    height: 400px;
                    overflow-y: scroll;
                    padding: 10px;
                    margin-bottom: 10px;
                }

                .line {
                    margin-bottom: 2px;
                    border-bottom: 1px solid #111;
                }

                .time {
                    color: #888;
                    font-size: 0.8em;
                    margin-right: 10px;
                }

                input {
                    width: 70%;
                    background: #111;
                    color: #fff;
                    border: 1px solid #444;
                    padding: 10px;
                }

                button {
                    padding: 10px;
                    background: #007AFF;
                    color: #fff;
                    border: none;
                    cursor: pointer;
                }

                select {
                    padding: 10px;
                    background: #111;
                    color: #fff;
                    border: 1px solid #444;
                }
            </style>

            <script>

                let ws;
                let lastActivity = Date.now();

                function getTime() {
                    const d = new Date();

                    return d.getHours().toString().padStart(2,'0') + ":" +
                           d.getMinutes().toString().padStart(2,'0') + ":" +
                           d.getSeconds().toString().padStart(2,'0') + "." +
                           d.getMilliseconds().toString().padStart(3,'0');
                }

                function appendLog(text) {

                    if(!text || !text.trim())
                        return;

                    const consoleDiv =
                        document.getElementById('console');

                    const div =
                        document.createElement('div');

                    div.className = 'line';

                    div.innerHTML =
                        '<span class="time">' +
                        getTime() +
                        '</span>' +
                        text;

                    consoleDiv.appendChild(div);

                    consoleDiv.scrollTop =
                        consoleDiv.scrollHeight;
                }

                function connectWs() {

                    const protocol =
                        window.location.protocol === 'https:'
                        ? 'wss:'
                        : 'ws:';

                    ws = new WebSocket(
                        protocol +
                        '//' +
                        window.location.host +
                        '$wsEndpoint'
                    );

                    ws.onmessage = (e) => {
                        appendLog(
                            '<span style="color:#0f0;">RX:</span> ' +
                            e.data
                        );
                    };

                    ws.onclose = () => {
                        setTimeout(connectWs, 2000);
                    };

                    // Smart idle heartbeat
                    setInterval(() => {

                        if(
                            ws &&
                            ws.readyState === WebSocket.OPEN &&
                            (Date.now() - lastActivity > 5000)
                        ) {
                            ws.send("PING");
                        }

                    }, 2000);
                }

                function send() {

                    const input =
                        document.getElementById('input');

                    const val =
                        input.value.trim();

                    if(!val)
                        return;

                    lastActivity = Date.now();

                    appendLog(
                        '<span style="color:#007AFF;">TX:</span> ' +
                        val
                    );

                    if(
                        ws &&
                        ws.readyState === WebSocket.OPEN
                    ) {

                        ws.send(val);

                    } else {

                        fetch(
                            '$sendEndpoint',
                            {
                                method:'POST',
                                body: val
                            }
                        );
                    }

                    input.value = '';
                }

                window.onload = connectWs;

            </script>
        </head>

        <body>

            <h1>$title</h1>

            $extraControls

            <div id="console"></div>

            <input
                type="text"
                id="input"
                placeholder="Enter command..."
                onkeydown="if(event.key==='Enter') send()">

            <button onclick="send()">
                Send
            </button>

        </body>
        </html>
    """.trimIndent()
    }
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceName = "command-hub"
    private val serviceType = "_http._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null


    private val registeredEndpoints =
        Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    fun start() {
        registeredEndpoints.clear()
        // Acquire Locks to prevent sleep and throttling
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("CommandHubLock").apply {
                setReferenceCounted(true)
                acquire()
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CommandHub:WakeLock",
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { json() }
            install(WebSockets) {
                pingPeriod = 20.seconds
                timeout = 40.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                fun Route.register(path: String, name: String, body: suspend RoutingContext.() -> Unit) {
                    registeredEndpoints.add(path to name)
                    get(path) { body() }
                }

                register("/layout", "Layout Config (JSON)") {
                    call.respondText(layoutProvider(), ContentType.Application.Json)
                }

                register("/log", "Dashboard Logger") {
                    val html = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { font-family: sans-serif; padding: 20px; background: #f4f4f9; }
                                input, textarea { width: 100%; padding: 10px; margin-bottom: 10px; border: 1px solid #ccc; border-radius: 4px; }
                                button { padding: 10px 20px; background: #007AFF; color: white; border: none; border-radius: 4px; cursor: pointer; }
                            </style>
                            <script>
                                function sendLog() {
                                    const id = document.getElementById('moduleId').value;
                                    const text = document.getElementById('message').value;
                                    fetch('/api/log', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({ id: id, text: text })
                                    }).then(() => alert('Sent!'));
                                }
                            </script>
                        </head>
                        <body>
                            <h1>Update Dashboard Module</h1>
                            <input type="text" id="moduleId" placeholder="Module ID (e.g., logs)">
                            <textarea id="message" rows="4" placeholder="Enter message..."></textarea>
                            <button onclick="sendLog()">Update Dashboard</button>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                post("/api/log") {
                    val body = call.receiveText()
                    try {
                        val json = kotlinx.serialization.json.Json.parseToJsonElement(body).let { it as kotlinx.serialization.json.JsonObject }
                        val id = json["id"]?.let { it as kotlinx.serialization.json.JsonPrimitive }?.content ?: ""
                        val text = json["text"]?.let { it as kotlinx.serialization.json.JsonPrimitive }?.content ?: ""
                        onLogReceived(id, text)
                        call.respond(HttpStatusCode.OK)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }

                register("/bluetooth", "Bluetooth Terminal") {

                    val devices =
                        bluetoothManager.getPairedDevices()

                    val deviceOptions =
                        devices.joinToString("") {
                            "<option value=\"${it.address}\">${it.name} (${it.address})</option>"
                        }

                    val controls = """
                        <select id="device">
                            $deviceOptions
                        </select>

                        <button onclick="
                            const addr =
                                document.getElementById('device').value;

                            fetch('/bluetooth-connect', {
                                method:'POST',
                                body: addr
                            })
                            .then(r => r.text())
                            .then(m => appendLog('SYSTEM: ' + m));
                        ">
                            Connect
                        </button>

                        <br><br>
                    """.trimIndent()

                    call.respondText(
                        terminalPage(
                            title = "Bluetooth Control",
                            wsEndpoint = "/bluetooth-ws",
                            sendEndpoint = "/bluetooth-send",
                            extraControls = controls,
                        ),
                        ContentType.Text.Html,
                    )
                }

                webSocket("/bluetooth-ws") {
                    val sendJob = launch {
                        try {
                            bluetoothManager.dataFlow.collect {
                                send(it)
                            }
                        } catch (_: Exception) {
                            cancel()
                        }
                    }

                    try {
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText()?.trim() ?: continue
                            if (isValidBluetoothInput(text)) {
                                bluetoothManager.send(text)
                            }
                        }
                    } finally {
                        sendJob.cancel()
                    }
                }

                post("/bluetooth-send") {
                    val text = call.receiveText()
                    if (isValidBluetoothInput(text)) {
                        bluetoothManager.send(text)
                    }
                    call.respond(HttpStatusCode.OK)
                }

                post("/bluetooth-connect") {
                    val address = call.receiveText()
                    val result = bluetoothManager.connect(address)
                    call.respondText(result)
                }

                register("/tcp", "TCP Link") {
                    val controls = """
                        <input type="text" id="ip" placeholder="IP Address" style="width: 50%;">
                        <input type="number" id="port" placeholder="Port" value="8888" style="width: 20%;">
                        <button onclick="
                            const ip = document.getElementById('ip').value;
                            const port = document.getElementById('port').value;
                            fetch('/tcp-connect', {
                                method:'POST',
                                body: JSON.stringify({ip: ip, port: parseInt(port)})
                            })
                            .then(r => r.text())
                            .then(m => appendLog('SYSTEM: ' + m));
                        ">
                            Connect
                        </button>
                        <br><br>
                    """.trimIndent()

                    call.respondText(
                        terminalPage(
                            title = "TCP Console",
                            wsEndpoint = "/tcp-ws",
                            sendEndpoint = "/tcp-send",
                            extraControls = controls
                        ),
                        ContentType.Text.Html
                    )
                }

                webSocket("/tcp-ws") {
                    val job = launch {
                        tcpManager.dataFlow.collect { data ->
                            send(data)
                        }
                    }
                    try {
                        incoming.consumeEach { frame ->
                            (frame as? Frame.Text)?.let {
                                tcpManager.send(it.readText())
                            }
                        }
                    } finally {
                        job.cancel()
                    }
                }

                post("/tcp-send") {
                    val text = call.receiveText()
                    tcpManager.send(text)
                    call.respond(HttpStatusCode.OK)
                }

                post("/tcp-connect") {
                    val body = call.receiveText()
                    try {
                        val json = kotlinx.serialization.json.Json.parseToJsonElement(body).let { it as kotlinx.serialization.json.JsonObject }
                        val ip = json["ip"]?.let { it as kotlinx.serialization.json.JsonPrimitive }?.content ?: ""
                        val port = json["port"]?.let { it as kotlinx.serialization.json.JsonPrimitive }?.content?.toIntOrNull() ?: 8888
                        val result = tcpManager.connect(ip, port)
                        call.respondText(result)
                    } catch (_: Exception) {
                        call.respondText("Invalid connection parameters", status = HttpStatusCode.BadRequest)
                    }
                }

                register("/uart", "UART Terminal") {
                    call.respondText(
                        terminalPage(
                            title = "UART Control",
                            wsEndpoint = "/uart-ws",
                            sendEndpoint = "/uart-send"
                        ),
                        ContentType.Text.Html
                    )
                }

                webSocket("/uart-ws") {
                    val job = launch {
                        uartManager.dataFlow.collect { data ->
                            send(data)
                        }
                    }
                    try {
                        incoming.consumeEach { frame ->
                            (frame as? Frame.Text)?.let {
                                uartManager.send(it.readText())
                            }
                        }
                    } finally {
                        job.cancel()
                    }
                }

                post("/uart-send") {
                    val text = call.receiveText()
                    uartManager.send(text)
                    call.respond(HttpStatusCode.OK)
                }

                register("/camera", "Camera Dashboard") {
                    if (!cameraManager.isReady()) {
                        call.respondText("""
                            <html><head><script>setTimeout(() => location.reload(), 2000);</script></head>
                            <body style="font-family:sans-serif; text-align:center; padding-top:100px;">
                                <h1>Camera system loading...</h1>
                                <p>Initializing CameraX provider. Please wait.</p>
                            </body></html>
                        """.trimIndent(), ContentType.Text.Html)
                        return@register
                    }

                    val device = cameraManager.getDeviceName()
                    val cameras = cameraManager.getCameraInfos()

                    val cameraRows = cameras.joinToString("") { info ->
                        val resLinks = info.supportedResolutions.asSequence().take(8).joinToString(" ") { res ->
                            "<a href='/stream/${info.id}?res=${res.width}x${res.height}' style='color:#007AFF; text-decoration:none; font-size:0.85em; margin-right:5px;'>[$res]</a>"
                        }
                        """
                        <tr>
                            <td><strong>${info.id}</strong></td>
                            <td>${info.facing}</td>
                            <td>${info.type}</td>
                            <td>
                                <a href='/camera/${info.id}' style="background:#007AFF; color:white; padding:4px 10px; border-radius:4px; text-decoration:none; font-weight:bold;">Viewer</a>
                                <div style="margin-top:8px; color:#666;">Streams: $resLinks</div>
                            </td>
                        </tr>
                        """
                    }

                    val html = """
                        <html>
                        <head>
                            <title>Camera Hub</title>
                            <style>
                                body { font-family: sans-serif; padding: 40px; background: #f4f4f9; color: #333; }
                                .card { background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                                table { width: 100%; border-collapse: collapse; }
                                th, td { text-align: left; padding: 12px; border-bottom: 1px solid #eee; }
                                .status { padding: 10px; border-radius: 4px; font-weight: bold; margin-bottom: 10px; }
                                h1, h2, h3 { margin-top: 0; }
                            </style>
                        </head>
                        <body>
                            <h1>Camera Hub</h1>
                            <p>Device: $device</p>

                            <div class="card">
                                <h2>Available Cameras</h2>
                                <table>
                                    <thead><tr><th>ID</th><th>Facing</th><th>Type</th><th>Action</th></tr></thead>
                                    <tbody>$cameraRows</tbody>
                                </table>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/camera/{id}") {
                    val cameraId = call.parameters["id"] ?: ""
                    val isMinimal = call.request.queryParameters["minimal"] == "true"
                    val initialRes = call.request.queryParameters["res"] ?: "640x480"
                    val initialFps = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
                    val cameras = cameraManager.getCameraInfos()
                    val currentCamera = cameras.find { it.id == cameraId }

                    val otherCamerasLinks = cameras.asSequence().filter { it.id != cameraId }.joinToString(" | ") {
                        "<a href='/camera/${it.id}'>Camera ${it.id}</a>"
                    }

                    val resOptions = currentCamera?.supportedResolutions?.joinToString("") {
                        "<button onclick=\"changeRes('${it.width}x${it.height}')\">${it.width}x${it.height}</button>"
                    } ?: "No resolutions found"

                    val fpsList = listOf(5, 10, 15, 20, 25, 30, 60)
                    val fpsOptions = fpsList.joinToString("") { fps ->
                        "<button onclick=\"changeFps($fps)\">$fps FPS</button>"
                    }

                    val html = """
                        <html>
                        <head>
                            <title>Camera $cameraId</title>
                            <style>
                                body { margin: 0; background: #000; color: #fff; font-family: sans-serif; text-align: center; }
                                .header { padding: 20px; background: #111; display: ${if (isMinimal) "none" else "block"}; }
                                img { max-width: 100%; height: auto; border: 2px solid #333; margin-top: ${if (isMinimal) "0" else "10px"}; }
                                .nav { padding: 10px; }
                                .res-picker { padding: 10px; background: rgba(34,34,34,0.8); position: ${if (isMinimal) "fixed" else "static"}; top: 0; width: 100%; z-index: 100; }
                                .picker-group { margin: 4px 0; }
                                a { color: #007AFF; text-decoration: none; margin: 0 10px; }
                                button { background: #444; color: white; border: 1px solid #666; padding: 5px 10px; margin: 2px; border-radius: 4px; cursor: pointer; }
                                button:hover { background: #555; }
                                .min-toggle { position: fixed; bottom: 10px; right: 10px; opacity: 0.3; }
                                .min-toggle:hover { opacity: 1.0; }
                            </style>
                            <script>
                                const urlParams = new URLSearchParams(window.location.search);
                                let currentRes = urlParams.get('res') || '$initialRes';
                                let currentFps = urlParams.get('fps') || '$initialFps';

                                function updateStream() {
                                    const img = document.getElementById('stream');
                                    img.src = '/stream/$cameraId?res=' + currentRes + '&fps=' + currentFps + '&t=' + Date.now();
                                }
                                function changeRes(res) {
                                    currentRes = res;
                                    updateStream();
                                }
                                function changeFps(fps) {
                                    currentFps = fps;
                                    updateStream();
                                }
                                function checkStream() {
                                    const img = document.getElementById('stream');
                                    setTimeout(() => {
                                        if (img.naturalWidth === 0) {
                                            img.src = img.src.split('&t=')[0] + '&t=' + Date.now();
                                        }
                                    }, 3000);
                                }
                            </script>
                        </head>
                        <body onload="checkStream()">
                            <div class="header">
                                <h1>Camera $cameraId Stream</h1>
                                <div class="nav">
                                    <a href="/camera">&larr; Back to Hub</a> | $otherCamerasLinks
                                </div>
                            </div>
                            <div class="res-picker">
                                <div class="picker-group">
                                    <strong>Resolutions:</strong> $resOptions
                                </div>
                                <div class="picker-group">
                                    <strong>Frame Rate (FPS):</strong> $fpsOptions
                                </div>
                            </div>
                            <img id="stream" src="/stream/$cameraId?res=$initialRes&fps=$initialFps">
                            <div class="min-toggle">
                                <a href="?minimal=${!isMinimal}" style="color:white; font-size:10px;">[Toggle UI]</a>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/stream/{id}") {
                    val cameraId = call.parameters["id"] ?: return@get
                    val resParam = call.request.queryParameters["res"] ?: "640x480"
                    val fpsParam = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
                    val parts = resParam.split("x")
                    val width = parts.getOrNull(0)?.toIntOrNull() ?: 640
                    val height = parts.getOrNull(1)?.toIntOrNull() ?: 480

                    val boundary = "frame"
                    call.respondBytesWriter(contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")) {
                        streamCamera(cameraManager.getFlow(cameraId, width, height, fpsParam), boundary)
                    }
                }

                register("/sensors", "Sensors Hub") {
                    val sensors = sensorManager.getAvailableSensors()
                    val sensorCards = sensors.joinToString("") { info ->
                        """
                        <div style="background: white; padding: 20px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 10px;">
                            <h3>${info.name}</h3>
                            <p>Type: ${info.stringType} | Vendor: ${info.vendor}</p>
                            <a href='/sensors/${info.type}' style="display: inline-block; background: #007AFF; color: white; padding: 10px 20px; border-radius: 6px; text-decoration: none; font-weight: bold;">View Data</a>
                        </div>
                        """
                    }

                    val html = """
                        <html>
                        <head><title>Sensor Hub</title><style>body { font-family: sans-serif; padding: 40px; background: #f4f4f9; }</style></head>
                        <body>
                            <h1>Available Sensors</h1>
                            <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px;">
                                $sensorCards
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/sensors/{type}") {
                    val sensorType = call.parameters["type"]?.toIntOrNull() ?: return@get
                    val sensors = sensorManager.getAvailableSensors()
                    val info = sensors.find { it.type == sensorType }

                    val html = """
                        <html>
                        <head>
                            <title>${info?.name}</title>
                            <style>
                                body { font-family: 'Courier New', monospace; background: #000; color: #0f0; padding: 40px; }
                                #data { font-size: 2em; margin-top: 20px; }
                            </style>
                            <script>
                                function connect() {
                                    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                                    const ws = new WebSocket(protocol + '//' + window.location.host + '/sensors-ws/$sensorType');
                                    ws.onmessage = (e) => {
                                        document.getElementById('data').innerText = e.data;
                                    };
                                }
                                window.onload = connect;
                            </script>
                        </head>
                        <body>
                            <h1>Sensor: ${info?.name}</h1>
                            <p>${info?.vendor}</p>
                            <div id="data">Waiting for data...</div>
                            <br><a href="/sensors" style="color: #007AFF; text-decoration: none;">&larr; Back to Hub</a>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                webSocket("/sensors-ws/{type}") {
                    val sensorType = call.parameters["type"]?.toIntOrNull() ?: return@webSocket
                    val job = launch {
                        sensorManager.getSensorFlow(sensorType).collect { data ->
                            send(kotlinx.serialization.json.Json.encodeToString(data))
                        }
                    }
                    try {
                        incoming.consumeEach { } // Keep connection open
                    } finally {
                        job.cancel()
                        sensorManager.stopSensor(sensorType)
                    }
                }

                // JSON API for Sensors
                get("/api/sensors") {
                    call.respond(sensorManager.getAvailableSensors())
                }

                get("/api/sensors/{type}") {
                    val type = call.parameters["type"]?.toIntOrNull() ?: return@get
                    val flow = sensorManager.getSensorFlow(type)
                    // Get the latest value from the flow's replay cache
                    val latestValue = flow.replayCache.firstOrNull() ?: "waiting for data"
                    call.respond(mapOf("id" to type, "value" to latestValue))
                }

                // JSON API for Cameras
                get("/api/cameras") {
                    if (!cameraManager.isReady()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera provider not ready"))
                    } else {
                        call.respond(cameraManager.getCameraInfos())
                    }
                }

                get("/") {
                    val listItems = registeredEndpoints.joinToString("") { (path, name) ->
                        "<li><a href=\"$path\">$name</a></li>"
                    }
                    val html = """
                        <html>
                            <head>
                                <title>CommandHub</title>
                                <style>
                                    body { font-family: sans-serif; padding: 40px; line-height: 1.6; background: #f4f4f9; }
                                    h1 { color: #333; }
                                    li { margin: 10px 0; }
                                    a { color: #007AFF; text-decoration: none; font-weight: bold; }
                                </style>
                            </head>
                            <body>
                                <h1>CommandHub Terminal</h1>
                                <p>Select a connection type:</p>
                                <ul>$listItems</ul>
                            </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }
            }
        }.start(wait = false)
        registerService(port)
    }

    private suspend fun ByteWriteChannel.streamCamera(flow: SharedFlow<ByteArray>, boundary: String) {
        try {
            flow.collect { jpegBytes ->
                writeStringUtf8("--$boundary\r\n")
                writeStringUtf8("Content-Type: image/jpeg\r\n")
                writeStringUtf8("Content-Length: ${jpegBytes.size}\r\n\r\n")
                writeFully(jpegBytes)
                writeStringUtf8("\r\n")
                flush()
            }
        } catch (_: Exception) {
            // Client disconnected
        }
    }

    fun registerService(port: Int = this.port) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            registrationListener = null
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@DashboardServer.serviceName
            serviceType = this@DashboardServer.serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                println("mDNS: Service registered")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }

        try {
            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        multicastLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        server?.stop(1000, 2000)
        nsdManager?.unregisterService(registrationListener)
        registrationListener = null
    }
}
