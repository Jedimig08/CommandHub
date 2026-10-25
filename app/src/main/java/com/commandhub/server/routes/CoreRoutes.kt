package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.LogRequest
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
data class ProcessingUrlRequest(val url: String = "")

fun Route.configureCoreRoutes(
    assetLoader: WebAssetLoader,
    layoutProvider: () -> String,
    getProcessingUrl: () -> String = { "" },
    setProcessingUrl: (String) -> Unit = {},
    publishDomain: () -> Unit = {},
    setKeepScreenOnEnabled: (Boolean) -> Unit = {},
    updateSensorDelayMode: (Boolean) -> Unit = {},
    onLogReceived: (String, String) -> Unit,
    registeredEndpoints: List<Pair<String, String>>,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    get("/api/settings/processing-url") {
        call.respond(mapOf("url" to getProcessingUrl()))
    }

    post("/api/settings/processing-url") {
        try {
            val req = runCatching { call.receive<ProcessingUrlRequest>() }.getOrNull()
            val urlStr = req?.url?.ifEmpty { null } ?: call.request.queryParameters["url"] ?: ""
            if (urlStr.isNotEmpty()) {
                setProcessingUrl(urlStr)
            }
            call.respond(mapOf("status" to "ok", "url" to getProcessingUrl()))
        } catch (_: Exception) {
            call.respond(mapOf("status" to "ok", "url" to getProcessingUrl()))
        }
    }

    post("/api/settings/publish-domain") {
        publishDomain()
        call.respond(mapOf("status" to "published"))
    }

    post("/api/settings/screen-on") {
        val enabled = call.request.queryParameters["enabled"]?.toBooleanStrictOrNull() ?: true
        setKeepScreenOnEnabled(enabled)
        call.respond(mapOf("status" to "ok", "enabled" to enabled))
    }

    post("/api/settings/sensors-speed") {
        val fastest = call.request.queryParameters["fastest"]?.toBooleanStrictOrNull() ?: true
        updateSensorDelayMode(fastest)
        call.respond(mapOf("status" to "ok", "fastest" to fastest))
    }

    register("/layout", "Layout Config (JSON)") {
        call.respondText(layoutProvider(), ContentType.Application.Json)
    }

    register("/log", "Dashboard Logger") {
        val html = assetLoader.load("log.html")
        call.respondText(html, ContentType.Text.Html)
    }

    post("/api/log") {
        try {
            val request = call.receive<LogRequest>()
            onLogReceived(request.id, request.text)
            call.respond(HttpStatusCode.OK)
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    get("/") {
        val listItems = registeredEndpoints.joinToString("") { (path, name) ->
            "<li><a href=\"$path\">$name</a></li>"
        }
        val html = assetLoader.load("index.html", mapOf("listItems" to listItems))
        call.respondText(html, ContentType.Text.Html)
    }
}
