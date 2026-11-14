package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import io.ktor.http.ContentType
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

@Serializable
data class LoginRequest(val token: String = "")

@Serializable
data class SettingsStateResponse(
    val screenOn: Boolean,
    val sensorFastest: Boolean,
    val processingUrl: String,
)

@Serializable
data class SettingsStatusResponse(
    val status: String = "ok",
    val enabled: Boolean = false,
    val fastest: Boolean = false,
    val url: String = "",
)

fun Route.configureCoreRoutes(
    assetLoader: WebAssetLoader,
    authManager: com.commandhub.data.AuthManager,
    getProcessingUrl: () -> String = { "" },
    setProcessingUrl: (String) -> Unit = {},
    publishDomain: () -> Unit = {},
    getKeepScreenOn: () -> Boolean = { true },
    setKeepScreenOnEnabled: (Boolean) -> Unit = {},
    getSensorDelayFastest: () -> Boolean = { false },
    updateSensorDelayMode: (Boolean) -> Unit = {},
    registeredEndpoints: List<Pair<String, String>>,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    get("/login") {
        val html = assetLoader.load("login.html", emptyMap())
        call.respondText(html, ContentType.Text.Html)
    }

    post("/login") {
        val remoteHost = call.request.local.remoteHost
        if (!authManager.checkRateLimit(remoteHost)) {
            call.respond(io.ktor.http.HttpStatusCode.TooManyRequests, mapOf("error" to "Too many failed attempts. Locked out temporarily."))
            return@post
        }
        try {
            val req = call.receive<LoginRequest>()
            if (authManager.validateBearerToken(req.token)) {
                authManager.clearFailedAttempts(remoteHost)
                val sessionId = authManager.createSession()
                call.response.cookies.append(
                    io.ktor.http.Cookie(
                        name = "session_id",
                        value = sessionId,
                        httpOnly = true,
                        secure = false,
                        extensions = mapOf("SameSite" to "Strict"),
                        path = "/"
                    )
                )
                call.respond(mapOf("status" to "success", "message" to "Authenticated successfully"))
            } else {
                authManager.recordFailedAttempt(remoteHost)
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
            }
        } catch (_: Exception) {
            authManager.recordFailedAttempt(remoteHost)
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to "Invalid request"))
        }
    }

    post("/pair") {
        val remoteHost = call.request.local.remoteHost
        if (!authManager.checkRateLimit(remoteHost)) {
            call.respond(io.ktor.http.HttpStatusCode.TooManyRequests, mapOf("error" to "Too many attempts. Locked out temporarily."))
            return@post
        }
        try {
            val req = call.receive<LoginRequest>()
            if (authManager.validateBearerToken(req.token)) {
                authManager.clearFailedAttempts(remoteHost)
                val sessionId = authManager.createSession()
                call.response.cookies.append(
                    io.ktor.http.Cookie(
                        name = "session_id",
                        value = sessionId,
                        httpOnly = true,
                        secure = false,
                        extensions = mapOf("SameSite" to "Strict"),
                        path = "/"
                    )
                )
                call.respond(mapOf("status" to "success", "paired" to true))
            } else {
                authManager.recordFailedAttempt(remoteHost)
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Invalid pairing token"))
            }
        } catch (_: Exception) {
            authManager.recordFailedAttempt(remoteHost)
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to "Invalid request"))
        }
    }
    get("/api/settings/state") {
        call.respond(
            SettingsStateResponse(
                screenOn = getKeepScreenOn(),
                sensorFastest = getSensorDelayFastest(),
                processingUrl = getProcessingUrl(),
            ),
        )
    }

    get("/api/settings/processing-url") {
        call.respond(SettingsStatusResponse(url = getProcessingUrl()))
    }

    post("/api/settings/processing-url") {
        try {
            val req = runCatching { call.receive<ProcessingUrlRequest>() }.getOrNull()
            val urlStr = req?.url?.ifEmpty { null } ?: call.request.queryParameters["url"] ?: ""
            if (urlStr.isNotEmpty()) {
                setProcessingUrl(urlStr)
            }
            call.respond(SettingsStatusResponse(url = getProcessingUrl()))
        } catch (_: Exception) {
            call.respond(SettingsStatusResponse(url = getProcessingUrl()))
        }
    }

    post("/api/settings/publish-domain") {
        publishDomain()
        call.respond(SettingsStatusResponse(status = "published"))
    }

    post("/api/settings/screen-on/toggle") {
        val newState = !getKeepScreenOn()
        setKeepScreenOnEnabled(newState)
        call.respond(SettingsStatusResponse(enabled = newState))
    }

    post("/api/settings/sensors-speed/toggle") {
        val newFastest = !getSensorDelayFastest()
        updateSensorDelayMode(newFastest)
        call.respond(SettingsStatusResponse(fastest = newFastest))
    }

    get("/") {
        val listItems = registeredEndpoints.joinToString("") { (path, name) ->
            "<li><a href=\"$path\">$name</a></li>"
        }
        val html = assetLoader.load("index.html", mapOf("listItems" to listItems))
        call.respondText(html, ContentType.Text.Html)
    }
}
