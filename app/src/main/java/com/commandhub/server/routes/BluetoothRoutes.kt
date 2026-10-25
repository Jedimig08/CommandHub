package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.BluetoothClassicManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private fun isValidBluetoothInput(text: String): Boolean {
    return (text.isNotBlank()) && (text.length <= 200) && (!text.startsWith("__"))
}

fun Route.configureBluetoothRoutes(
    assetLoader: WebAssetLoader,
    bluetoothManager: BluetoothClassicManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    register("/bluetooth", "Bluetooth Terminal") {
        val devices = bluetoothManager.getPairedDevices()
        val deviceOptions = devices.joinToString("") {
            "<option value=\"${it.address}\">${it.name} (${it.address})</option>"
        }

        val controls = """
            <select id="device" aria-label="Bluetooth Device Selector">
                $deviceOptions
            </select>

            <button onclick="
                const addr = document.getElementById('device').value;
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
            assetLoader.loadTerminalPage(
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
}
