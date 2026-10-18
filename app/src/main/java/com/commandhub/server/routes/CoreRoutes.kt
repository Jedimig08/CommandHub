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

fun Route.configureCoreRoutes(
    assetLoader: WebAssetLoader,
    layoutProvider: () -> String,
    onLogReceived: (String, String) -> Unit,
    registeredEndpoints: List<Pair<String, String>>,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
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
