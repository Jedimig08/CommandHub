package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.TcpConnectRequest
import com.commandhub.viewmodel.TcpManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

fun Route.configureTcpRoutes(
    assetLoader: WebAssetLoader,
    tcpManager: TcpManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    register("/tcp", "TCP Console") {
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
            assetLoader.loadTerminalPage(
                title = "TCP Console",
                wsEndpoint = "/tcp-ws",
                sendEndpoint = "/tcp-send",
                extraControls = controls,
            ),
            ContentType.Text.Html,
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
        try {
            val request = call.receive<TcpConnectRequest>()
            val result = tcpManager.connect(request.ip, request.port)
            call.respondText(result)
        } catch (_: Exception) {
            call.respondText("Invalid connection parameters", status = HttpStatusCode.BadRequest)
        }
    }
}
