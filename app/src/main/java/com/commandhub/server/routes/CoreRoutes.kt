package com.commandhub.server.routes

import android.content.Context
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
data class SettingsStateResponse(
    val screenOn: Boolean,
    val sensorFastest: Boolean,
    val processingUrl: String,
    val serverIp: String,
    val serverUrl: String,
)

@Serializable
data class SettingsStatusResponse(
    val status: String = "ok",
    val enabled: Boolean = false,
    val fastest: Boolean = false,
    val url: String = "",
)

fun getDeviceIpAddress(context: Context): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        var fallbackIp: String? = null
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (!intf.isUp || intf.isLoopback) continue
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    val host = addr.hostAddress
                    if (!host.isNullOrEmpty() && !host.startsWith("169.254")) {
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))) {
                            return host
                        }
                        if (fallbackIp == null) {
                            fallbackIp = host
                        }
                    }
                }
            }
        }
        if (fallbackIp != null) return fallbackIp
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "127.0.0.1"
}

fun Route.configureCoreRoutes(
    assetLoader: WebAssetLoader,
    context: Context,
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
    get("/api/settings/state") {
        val ip = getDeviceIpAddress(context)
        call.respond(
            SettingsStateResponse(
                screenOn = getKeepScreenOn(),
                sensorFastest = getSensorDelayFastest(),
                processingUrl = getProcessingUrl(),
                serverIp = ip,
                serverUrl = "http://$ip:8080",
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
