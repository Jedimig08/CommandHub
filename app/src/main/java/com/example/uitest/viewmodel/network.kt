package com.example.uitest.viewmodel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class DashboardServer(
    private val context: Context,
    private val port: Int = 8080,
    private val layoutProvider: () -> String,
    private val uartManager: UartManager
) {

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var nsdManager: NsdManager? = null

    private val serviceName = "android-drone"
    private val serviceType = "_http._tcp."

    // Tracks endpoints for the automatic index page
    private val registeredEndpoints = mutableListOf<Pair<String, String>>()

    fun start() {
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { json() }
            
            routing {
                // The root page that builds itself from registeredEndpoints
                get("/") {
                    val listItems = registeredEndpoints.joinToString("") { (path, name) ->
                        "<li><a href=\"$path\">$name</a></li>"
                    }
                    val html = """
                        <html>
                            <head>
                                <title>Drone Server</title>
                                <style>
                                    body { font-family: sans-serif; padding: 40px; line-height: 1.6; }
                                    h1 { color: #333; }
                                    li { margin: 10px 0; }
                                    a { color: #007AFF; text-decoration: none; font-weight: bold; }
                                    a:hover { text-decoration: underline; }
                                </style>
                            </head>
                            <body>
                                <h1>Drone Dashboard Server</h1>
                                <p>The following pages are available:</p>
                                <ul>$listItems</ul>
                            </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                // Helper to register and track routes
                fun Route.register(path: String, name: String, body: suspend RoutingContext.() -> Unit) {
                    registeredEndpoints.add(path to name)
                    get(path) { body() }
                }

                // Register your dynamic pages here
                register("/layout", "Dashboard Layout (JSON)") {
                    call.respondText(layoutProvider(), ContentType.Application.Json)
                }

                register("/status", "Server Status") {
                    call.respondText("Server is active and broadcasting on mDNS as $serviceName.local", ContentType.Text.Plain)
                }

                register("/uart", "UART Terminal (ESP32)") {
                    val html = """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { font-family: monospace; background: #1a1a1a; color: #00ff00; padding: 20px; }
                                #console { border: 1px solid #444; height: 300px; overflow-y: scroll; padding: 10px; margin-bottom: 10px; white-space: pre-wrap; }
                                input { width: 60%; background: #333; color: #fff; border: 1px solid #555; padding: 10px; }
                                select { padding: 10px; background: #333; color: #fff; border: 1px solid #555; }
                                button { padding: 10px; background: #007AFF; color: white; border: none; cursor: pointer; }
                                .controls { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
                            </style>
                            <script>
                                function refresh() {
                                    fetch('/uart-data').then(r => r.text()).then(t => {
                                        if(t) {
                                            document.getElementById('console').innerHTML += t;
                                            document.getElementById('console').scrollTop = document.getElementById('console').scrollHeight;
                                        }
                                    });
                                }
                                setInterval(refresh, 1000);
                                
                                function send() {
                                    const msg = document.getElementById('input').value;
                                    fetch('/uart-send', { method: 'POST', body: msg });
                                    document.getElementById('input').value = '';
                                }

                                function updateBaud() {
                                    const baud = document.getElementById('baud').value;
                                    fetch('/uart-baud', { method: 'POST', body: baud })
                                        .then(r => r.text())
                                        .then(msg => alert(msg));
                                }
                            </script>
                        </head>
                        <body>
                            <h1>ESP32 UART Terminal</h1>
                            
                            <div class="controls">
                                <label for="baud">Baud Rate:</label>
                                <select id="baud">
                                    <option value="9600">9600</option>
                                    <option value="19200">19200</option>
                                    <option value="38400">38400</option>
                                    <option value="57600">57600</option>
                                    <option value="115200" selected>115200</option>
                                    <option value="230400">230400</option>
                                </select>
                                <button onclick="updateBaud()">Update</button>
                            </div>

                            <div id="console">--- Connected to UART ---<br></div>
                            <input type="text" id="input" placeholder="Type a message..." onkeydown="if(event.key==='Enter') send()">
                            <button onclick="send()">Send</button>
                        </body>
                        </html>
                    """.trimIndent()
                    call.respondText(html, ContentType.Text.Html)
                }

                // Internal data routes (not registered for the index)
                get("/uart-data") {
                    call.respondText(uartManager.getBuffer())
                }

                post("/uart-send") {
                    val text = call.receiveText()
                    uartManager.send(text) // Removed the extra + "\n"
                    call.respond(HttpStatusCode.OK)
                }

                post("/uart-baud") {
                    val baud = call.receiveText().toIntOrNull() ?: 115200
                    val result = uartManager.connect(baud)
                    call.respondText(result)
                }
            }
        }.start(wait = false)

        registerService(port)
    }

    private fun registerService(port: Int) {
        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@DashboardServer.serviceName
            this.serviceType = this@DashboardServer.serviceType
            this.port = port
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                println("mDNS: Service registered successfully: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        })
    }

    fun stop() {
        nsdManager?.unregisterService(object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        })
        server?.stop(1000, 2000)
    }
}
