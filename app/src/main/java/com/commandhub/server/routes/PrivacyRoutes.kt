package com.commandhub.server.routes

import com.commandhub.data.AccessMode
import com.commandhub.data.PrivacyManager
import com.commandhub.server.WebAssetLoader
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class PrivacyUpdateRequest(
    val accessMode: AccessMode? = null,
    val feature: String? = null,
    val enabled: Boolean? = null,
    val cameraEnabled: Boolean? = null,
    val sensorsEnabled: Boolean? = null,
    val hardwareEnabled: Boolean? = null,
    val bluetoothEnabled: Boolean? = null,
    val uartEnabled: Boolean? = null,
    val tcpEnabled: Boolean? = null,
    val flashlightEnabled: Boolean? = null,
    val logEnabled: Boolean? = null,
    val microphoneEnabled: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val fileSystemEnabled: Boolean? = null,
)

fun Route.configurePrivacyRoutes(
    assetLoader: WebAssetLoader,
    privacyManager: PrivacyManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    register("/privacy", "Privacy & Access Control") {
        val html = assetLoader.load("privacy.html")
        call.respondText(html, ContentType.Text.Html)
    }

    get("/api/privacy/state") {
        call.respond(privacyManager.getState())
    }

    post("/api/privacy/update") {
        try {
            val req = call.receive<PrivacyUpdateRequest>()
            if (req.feature != null && req.enabled != null) {
                privacyManager.setFeatureEnabled(req.feature, req.enabled)
            }
            if (req.accessMode != null) {
                privacyManager.setAccessMode(req.accessMode)
            }
            if (req.feature == null && req.accessMode == null) {
                val current = privacyManager.getState()
                val updated = current.copy(
                    cameraEnabled = req.cameraEnabled ?: current.cameraEnabled,
                    sensorsEnabled = req.sensorsEnabled ?: current.sensorsEnabled,
                    hardwareEnabled = req.hardwareEnabled ?: current.hardwareEnabled,
                    bluetoothEnabled = req.bluetoothEnabled ?: current.bluetoothEnabled,
                    uartEnabled = req.uartEnabled ?: current.uartEnabled,
                    tcpEnabled = req.tcpEnabled ?: current.tcpEnabled,
                    flashlightEnabled = req.flashlightEnabled ?: current.flashlightEnabled,
                    logEnabled = req.logEnabled ?: current.logEnabled,
                    microphoneEnabled = req.microphoneEnabled ?: current.microphoneEnabled,
                    locationEnabled = req.locationEnabled ?: current.locationEnabled,
                    fileSystemEnabled = req.fileSystemEnabled ?: current.fileSystemEnabled,
                )
                privacyManager.updateState(updated)
            }
            call.respond(privacyManager.getState())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid privacy update request: ${e.message}"))
        }
    }
}
