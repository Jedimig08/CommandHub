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
        val req = try {
            call.receive<PrivacyUpdateRequest>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid privacy update request"))
            return@post
        }

        val updated = when {
            req.feature != null && req.enabled != null ->
                privacyManager.setFeatureEnabled(req.feature, req.enabled)
            req.accessMode != null ->
                privacyManager.setAccessMode(req.accessMode)
            else ->
                privacyManager.getState()
        }
        call.respond(updated)
    }
}
