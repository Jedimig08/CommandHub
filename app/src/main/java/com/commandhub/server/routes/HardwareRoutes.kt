package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.HardwareManager
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get

fun Route.configureHardwareRoutes(
    assetLoader: WebAssetLoader,
    hardwareManager: HardwareManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    register("/hardware", "Hardware Telemetry") {
        val html = assetLoader.load("hardware.html")
        call.respondText(html, ContentType.Text.Html)
    }

    get("/api/hardware") {
        call.respond(hardwareManager.telemetryFlow.value)
    }
}
