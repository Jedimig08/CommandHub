package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.SensorManager
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.configureSensorRoutes(
    assetLoader: WebAssetLoader,
    sensorManager: SensorManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
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

        val html = assetLoader.load("sensors_hub.html", mapOf("sensorCards" to sensorCards))
        call.respondText(html, ContentType.Text.Html)
    }

    get("/sensors/{type}") {
        val sensorType = call.parameters["type"]?.toIntOrNull() ?: return@get
        val sensors = sensorManager.getAvailableSensors()
        val info = sensors.find { it.type == sensorType }

        val html = assetLoader.load(
            "sensor_viewer.html",
            mapOf(
                "sensorName" to (info?.name ?: "Unknown Sensor"),
                "sensorVendor" to (info?.vendor ?: ""),
                "sensorType" to sensorType.toString(),
            ),
        )
        call.respondText(html, ContentType.Text.Html)
    }

    webSocket("/sensors-ws/{type}") {
        val sensorType = call.parameters["type"]?.toIntOrNull() ?: return@webSocket
        val job = launch {
            sensorManager.getSensorFlow(sensorType).collect { data ->
                send(Json.encodeToString(data))
            }
        }
        try {
            incoming.consumeEach { }
        } finally {
            job.cancel()
            sensorManager.stopSensor(sensorType)
        }
    }

    get("/api/sensors") {
        call.respond(sensorManager.getAvailableSensors())
    }

    get("/api/sensors/{type}") {
        val type = call.parameters["type"]?.toIntOrNull() ?: return@get
        val flow = sensorManager.getSensorFlow(type)
        val latestValue = flow.replayCache.firstOrNull() ?: "waiting for data"
        call.respond(mapOf("id" to type, "value" to latestValue))
    }
}
