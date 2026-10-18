package com.commandhub.server.routes

import com.commandhub.viewmodel.CameraManager
import com.commandhub.viewmodel.FlashlightRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.configureFlashlightRoutes(
    cameraManager: CameraManager,
) {
    get("/api/flashlight") {
        call.respond(mapOf("enabled" to cameraManager.isFlashlightOn()))
    }

    post("/api/flashlight") {
        val request = runCatching { call.receive<FlashlightRequest>() }.getOrNull()
        val enable = request?.enabled
            ?: (call.request.queryParameters["enabled"] == "true")
        cameraManager.setFlashlight(enable)
        call.respond(mapOf("enabled" to cameraManager.isFlashlightOn()))
    }

    post("/api/flashlight/toggle") {
        val newState = !cameraManager.isFlashlightOn()
        cameraManager.setFlashlight(newState)
        call.respond(mapOf("enabled" to newState))
    }
}
