package com.example.uitest.viewmodel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
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
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

class DashboardServer(
    private val context: Context,
    private val port: Int = 8080,
    private val layoutProvider: () -> String,
    private val uartManager: UartManager,
    private val bluetoothManager: BluetoothClassicManager
) {

    private fun isValidBluetoothInput(text: String): Boolean {
        return text.isNotBlank() &&
                text.length <= 200 &&
                !text.startsWith("__")
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun start() {
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
                "CommandHub:WakeLock"
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

                fun Route.register(path: String, name: String, body: suspend RoutingContext.() -> Unit) {
                    registeredEndpoints.add(path to name)
                    get(path) { body() }
                }

                register("/layout", "Layout Config (JSON)") {
                    call.respondText(layoutProvider(), ContentType.Application.Json)
                }

                register("/bluetooth", "Bluetooth Terminal") {
                    val devices = bluetoothManager.getPairedDevices()
                    val deviceOptions = devices.joinToString("") {
                        "<option value=\"${it.address}\">${it.name} (${it.address})</option>"
                    }

                    val html = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { font-family: 'Courier New', monospace; background: #000; color: #0f0; padding: 20px; }
                                #console { border: 1px solid #333; height: 400px; overflow-y: scroll; padding: 10px; margin-bottom: 10px; }
                                .line { margin-bottom: 2px; border-bottom: 1px solid #111; }
                                .time { color: #888; font-size: 0.8em; margin-right: 10px; }
                                input { width: 70%; background: #111; color: #fff; border: 1px solid #444; padding: 10px; }
                                button { padding: 10px; background: #007AFF; color: #fff; border: none; cursor: pointer; }
                                select { padding: 10px; background: #111; color: #fff; border: 1px solid #444; }
                            </style>
                            <script>
                                let ws;
                                function getTime() {
                                    const d = new Date();
                                    return d.getHours().toString().padStart(2, '0') + ":" + 
                                           d.getMinutes().toString().padStart(2, '0') + ":" + 
                                           d.getSeconds().toString().padStart(2, '0') + "." + 
                                           d.getMilliseconds().toString().padStart(3, '0');
                                }

                                function appendLog(text) {
                                    if (!text.trim()) return; // Ignore empty lines
                                    const console = document.getElementById('console');
                                    const div = document.createElement('div');
                                    div.className = 'line';
                                    div.innerHTML = '<span class="time">' + getTime() + '</span>' + text;
                                    console.appendChild(div);
                                    console.scrollTop = console.scrollHeight;
                                }

                                function connectWs() {
                                    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                                    ws = new WebSocket(protocol + '//' + window.location.host + '/bluetooth-ws');
                                    ws.onmessage = (e) => appendLog('<span style="color: #0f0;">RX:</span> ' + e.data);
                                    ws.onclose = () => setTimeout(connectWs, 2000);
                                }
                                
                                function send() {
                                    const input = document.getElementById('input');
                                    const val = input.value;
                                    if (!val) return;
                                    
                                    appendLog('<span style="color: #007AFF;">TX:</span> ' + val);
                                    
                                    if (ws && ws.readyState === WebSocket.OPEN) {
                                        ws.send(val);
                                    } else {
                                        fetch('/bluetooth-send', { method: 'POST', body: val });
                                    }
                                    input.value = '';
                                }

                                function connect() {
                                    const addr = document.getElementById('device').value;
                                    fetch('/bluetooth-connect', { method: 'POST', body: addr })
                                        .then(r => r.text()).then(m => appendLog("SYSTEM: " + m));
                                }
                                window.onload = connectWs;
                            </script>
                        </head>
                        <body>
                            <h1>Bluetooth Control</h1>
                            <select id="device">$deviceOptions</select>
                            <button onclick="connect()">Connect</button>
                            <div id="console"></div>
                            <input type="text" id="input" placeholder="Enter command..." onkeydown="if(event.key==='Enter') send()">
                            <button onclick="send()">Send</button>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                webSocket("/bluetooth-ws") {
                    val sendJob = launch {
                        try {
                            bluetoothManager.dataFlow.collect {
                                send(it)
                            }
                        } catch (e: Exception) {
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

                register("/uart", "UART Terminal") {
                    val html = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { font-family: 'Courier New', monospace; background: #000; color: #0f0; padding: 20px; }
                                #console { border: 1px solid #333; height: 400px; overflow-y: scroll; padding: 10px; margin-bottom: 10px; }
                                .line { margin-bottom: 2px; border-bottom: 1px solid #111; }
                                .time { color: #888; font-size: 0.8em; margin-right: 10px; }
                                input { width: 70%; background: #111; color: #fff; border: 1px solid #444; padding: 10px; }
                                button { padding: 10px; background: #007AFF; color: #fff; border: none; cursor: pointer; }
                            </style>
                            <script>
                                let ws;
                                function getTime() {
                                    const d = new Date();
                                    return d.getHours().toString().padStart(2, '0') + ":" + 
                                           d.getMinutes().toString().padStart(2, '0') + ":" + 
                                           d.getSeconds().toString().padStart(2, '0') + "." + 
                                           d.getMilliseconds().toString().padStart(3, '0');
                                }

                                function appendLog(text) {
                                    const console = document.getElementById('console');
                                    const div = document.createElement('div');
                                    div.className = 'line';
                                    div.innerHTML = '<span class="time">' + getTime() + '</span>' + text;
                                    console.appendChild(div);
                                    console.scrollTop = console.scrollHeight;
                                }

                                function connectWs() {
                                    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                                    ws = new WebSocket(protocol + '//' + window.location.host + '/uart-ws');
                                    ws.onmessage = (e) => appendLog('<span style="color: #0f0;">RX:</span> ' + e.data);
                                    ws.onclose = () => setTimeout(connectWs, 2000);
                                }
                                
                                function send() {
                                    const input = document.getElementById('input');
                                    const val = input.value;
                                    if (!val) return;
                                    
                                    appendLog('<span style="color: #007AFF;">TX:</span> ' + val);
                                    
                                    if (ws && ws.readyState === WebSocket.OPEN) {
                                        ws.send(val);
                                    } else {
                                        fetch('/uart-send', { method: 'POST', body: val });
                                    }
                                    input.value = '';
                                }
                                window.onload = connectWs;
                            </script>
                        </head>
                        <body>
                            <h1>UART Control</h1>
                            <div id="console"></div>
                            <input type="text" id="input" placeholder="Enter command..." onkeydown="if(event.key==='Enter') send()">
                            <button onclick="send()">Send</button>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                webSocket("/uart-ws") {
                    val job = launch {
                        uartManager.dataFlow.collect { data ->
                            send(data)
                        }
                    }
                    try {
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                uartManager.send(frame.readText())
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
            }
        }.start(wait = false)
        registerService(port)
    }

    private fun registerService(port: Int) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

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

        nsdManager?.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener
        )
    }

    fun stop() {
        multicastLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        server?.stop(1000, 2000)
        nsdManager?.unregisterService(registrationListener)
        registrationListener = null
    }
}