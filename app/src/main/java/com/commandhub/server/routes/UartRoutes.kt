package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.UartManager
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
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

fun Route.configureUartRoutes(
    assetLoader: WebAssetLoader,
    uartManager: UartManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit
) {
    register("/uart", "UART Terminal") {
        call.respondText(
            assetLoader.loadTerminalPage(
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
}
