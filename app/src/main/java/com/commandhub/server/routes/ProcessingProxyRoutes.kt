package com.commandhub.server.routes

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.formUrlEncode
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.net.HttpURLConnection
import java.net.URL

private val PROXY_PREFIX = "/processing-ui"

private val HOP_BY_HOP_HEADERS = setOf(
    "host",
    "connection",
    "content-length",
    "transfer-encoding",
    "accept-encoding",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "upgrade",
)

fun Route.configureProcessingProxyRoutes(
    context: Context,
    getProcessingUrl: () -> String,
) {
    route(PROXY_PREFIX) {
        get { proxyRequest(context, getProcessingUrl) }
        post { proxyRequest(context, getProcessingUrl) }
        get("{path...}") { proxyRequest(context, getProcessingUrl) }
        post("{path...}") { proxyRequest(context, getProcessingUrl) }
    }
}

private suspend fun RoutingContext.proxyRequest(
    context: Context,
    getProcessingUrl: () -> String,
) {
    val baseUrl = getProcessingUrl()
    if (baseUrl.isBlank() || !isAllowedProcessingUrl(baseUrl, context)) {
        call.respondText(
            "No valid processing server URL configured.",
            ContentType.Text.Plain,
            HttpStatusCode.BadGateway,
        )
        return
    }

    try {
        val parsedBase = URL(baseUrl)
        val tail = call.parameters.getAll("path")?.joinToString("/")?.trimStart('/').orEmpty()
        val basePath = parsedBase.path.trimEnd('/')
        val targetPath = if (tail.isEmpty()) {
            basePath.ifEmpty { "/" }
        } else {
            "$basePath/$tail"
        }
        val query = call.request.queryParameters.formUrlEncode()
        val target = buildString {
            append(parsedBase.protocol).append("://").append(parsedBase.host)
            if (parsedBase.port != -1) append(':').append(parsedBase.port)
            append(targetPath)
            if (query.isNotEmpty()) append('?').append(query)
        }

        val method = call.request.local.method.value
        val connection = URL(target).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        connection.doInput = true

        call.request.headers.forEach { name, values ->
            if (name.lowercase() !in HOP_BY_HOP_HEADERS) {
                values.forEach { connection.setRequestProperty(name, it) }
            }
        }
        connection.setRequestProperty("X-Forwarded-Prefix", PROXY_PREFIX)
        connection.setRequestProperty("X-Forwarded-Host", call.request.headers["Host"] ?: "localhost")

        if (method in setOf("POST", "PUT", "PATCH")) {
            connection.doOutput = true
            call.receiveStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
        }

        val status = HttpStatusCode.fromValue(connection.responseCode)
        val contentType = connection.contentType?.let { raw ->
            runCatching { ContentType.parse(raw) }.getOrNull()
        } ?: ContentType.Application.OctetStream

        val stream = if (connection.responseCode >= 400) {
            connection.errorStream ?: connection.inputStream
        } else {
            connection.inputStream
        }
        var bytes = stream.use { it.readBytes() }

        val location = connection.getHeaderField(HttpHeaders.Location)
        if (status.value in 300..399 && !location.isNullOrBlank()) {
            val upstreamOrigin = "${parsedBase.protocol}://${parsedBase.host}" +
                if (parsedBase.port != -1) ":${parsedBase.port}" else ""
            val proxiedLocation = when {
                location.startsWith(upstreamOrigin, ignoreCase = true) ->
                    PROXY_PREFIX + location.removePrefix(upstreamOrigin)
                location.startsWith("/") -> PROXY_PREFIX + location
                else -> location
            }
            call.response.header(HttpHeaders.Location, proxiedLocation)
        }

        if (contentType.match("text/html") && bytes.isNotEmpty()) {
            val html = bytes.toString(Charsets.UTF_8)
            if (!html.contains("<base", ignoreCase = true)) {
                val headMatch = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
                val insertAt = headMatch?.range?.last?.plus(1) ?: 0
                val withBase = html.substring(0, insertAt) +
                    "<base href=\"$PROXY_PREFIX/\">" +
                    html.substring(insertAt)
                bytes = withBase.toByteArray(Charsets.UTF_8)
            }
        }

        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(bytes, contentType, status)
    } catch (e: Exception) {
        call.respondText(
            "Processing proxy error: ${e.message ?: "unknown error"}",
            ContentType.Text.Plain,
            HttpStatusCode.BadGateway,
        )
    }
}
