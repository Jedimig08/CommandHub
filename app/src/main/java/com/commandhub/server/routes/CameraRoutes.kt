package com.commandhub.server.routes

import com.commandhub.server.WebAssetLoader
import com.commandhub.viewmodel.CameraManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate

private val MJPEG_PREFIX = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".toByteArray(Charsets.UTF_8)
private val MJPEG_HEADER_END = "\r\n\r\n".toByteArray(Charsets.UTF_8)
private val MJPEG_FRAME_END = "\r\n".toByteArray(Charsets.UTF_8)

private suspend fun ByteWriteChannel.streamCamera(flow: Flow<ByteArray>, boundary: String) {
    val prefix = if (boundary == "frame") MJPEG_PREFIX else "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ".toByteArray(Charsets.UTF_8)
    try {
        flow.conflate().collect { jpegBytes ->
            writeFully(prefix)
            writeStringUtf8(jpegBytes.size.toString())
            writeFully(MJPEG_HEADER_END)
            writeFully(jpegBytes)
            writeFully(MJPEG_FRAME_END)
            flush()
        }
    } catch (_: Exception) {
        // Client disconnected
    }
}

fun Route.configureCameraRoutes(
    assetLoader: WebAssetLoader,
    cameraManager: CameraManager,
    register: (path: String, name: String, body: suspend RoutingContext.() -> Unit) -> Unit,
) {
    register("/camera", "Camera Dashboard") {
        if (!cameraManager.isReady()) {
            call.respondText(
                """
                <html><head><script>setTimeout(() => location.reload(), 2000);</script></head>
                <body style="font-family:sans-serif; text-align:center; padding-top:100px;">
                    <h1>Camera system loading...</h1>
                    <p>Initializing CameraX provider. Please wait.</p>
                </body></html>
                """.trimIndent(),
                ContentType.Text.Html,
            )
            return@register
        }

        val device = cameraManager.getDeviceName()
        val cameras = cameraManager.getCameraInfos()

        val cameraRows = cameras.joinToString("") { info ->
            val resLinks = info.supportedResolutions.asSequence().take(8).joinToString(" ") { res ->
                "<a href='/stream/${info.id}?res=${res.width}x${res.height}' style='color:#007AFF; text-decoration:none; font-size:0.85em; margin-right:5px;'>[$res]</a>"
            }
            """
            <tr>
                <td><strong>${info.id}</strong></td>
                <td>${info.facing}</td>
                <td>${info.type}</td>
                <td>
                    <a href='/camera/${info.id}' style="background:#007AFF; color:white; padding:4px 10px; border-radius:4px; text-decoration:none; font-weight:bold;">Viewer</a>
                    <div style="margin-top:8px; color:#666;">Streams: $resLinks</div>
                </td>
            </tr>
            """
        }

        val html = assetLoader.load(
            "camera_hub.html",
            mapOf("device" to device, "cameraRows" to cameraRows),
        )
        call.respondText(html, ContentType.Text.Html)
    }

    get("/camera/{id}") {
        val cameraId = call.parameters["id"] ?: ""
        val isMinimal = call.request.queryParameters["minimal"] == "true"
        val initialRes = call.request.queryParameters["res"] ?: "640x480"
        val initialFps = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
        val initialQuality = call.request.queryParameters["quality"]?.toIntOrNull() ?: 50
        val cameras = cameraManager.getCameraInfos()
        val currentCamera = cameras.find { it.id == cameraId }

        val otherCamerasLinks = cameras.asSequence().filter { it.id != cameraId }.joinToString(" | ") {
            "<a href='/camera/${it.id}'>Camera ${it.id}</a>"
        }

        val resOptions = currentCamera?.supportedResolutions?.joinToString("") {
            "<button onclick=\"changeRes('${it.width}x${it.height}')\">${it.width}x${it.height}</button>"
        } ?: "No resolutions found"

        val fpsList = listOf(5, 10, 15, 20, 25, 30, 60)
        val fpsOptions = fpsList.joinToString("") { fps ->
            "<button onclick=\"changeFps($fps)\">$fps FPS</button>"
        }

        val qualityList = listOf(20, 35, 50, 75, 90)
        val qualityOptions = qualityList.joinToString("") { q ->
            "<button onclick=\"changeQuality($q)\">$q%</button>"
        }

        val html = assetLoader.load(
            "camera_viewer.html",
            mapOf(
                "cameraId" to cameraId,
                "headerDisplay" to if (isMinimal) "none" else "block",
                "canvasMarginTop" to if (isMinimal) "0" else "10px",
                "pickerPosition" to if (isMinimal) "fixed" else "static",
                "initialRes" to initialRes,
                "initialFps" to initialFps.toString(),
                "initialQuality" to initialQuality.toString(),
                "otherCamerasLinks" to otherCamerasLinks,
                "resOptions" to resOptions,
                "fpsOptions" to fpsOptions,
                "qualityOptions" to qualityOptions,
                "toggleMinimal" to (!isMinimal).toString(),
            ),
        )
        call.respondText(html, ContentType.Text.Html)
    }

    webSocket("/h264-ws/{id}") {
        val cameraId = call.parameters["id"] ?: return@webSocket
        val resParam = call.request.queryParameters["res"] ?: "640x480"
        val fpsParam = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
        val bitrateParam = call.request.queryParameters["bitrate"]?.toIntOrNull() ?: 2_000_000
        val parts = resParam.split("x")
        val width = parts.getOrNull(0)?.toIntOrNull() ?: 640
        val height = parts.getOrNull(1)?.toIntOrNull() ?: 480

        val flow = cameraManager.getH264Flow(cameraId, width, height, fpsParam, bitrateParam)

        cameraManager.getH264SpsPps()?.let { spsPps ->
            try {
                send(Frame.Binary(fin = true, data = spsPps))
            } catch (_: Exception) {}
        }

        try {
            flow.conflate().collect { nalBytes ->
                send(Frame.Binary(fin = true, data = nalBytes))
            }
        } catch (_: Exception) {
            // Disconnected
        }
    }

    webSocket("/camera-ws/{id}") {
        val cameraId = call.parameters["id"] ?: return@webSocket
        val resParam = call.request.queryParameters["res"] ?: "640x480"
        val fpsParam = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
        val qualityParam = call.request.queryParameters["quality"]?.toIntOrNull() ?: 50
        val grayscaleParam = call.request.queryParameters["grayscale"]?.toBooleanStrictOrNull() ?: false
        val parts = resParam.split("x")
        val width = parts.getOrNull(0)?.toIntOrNull() ?: 640
        val height = parts.getOrNull(1)?.toIntOrNull() ?: 480

        val flow = cameraManager.getFlow(cameraId, width, height, fpsParam, qualityParam, grayscaleParam)
        try {
            flow.conflate().collect { jpegBytes ->
                send(Frame.Binary(fin = true, data = jpegBytes))
            }
        } catch (_: Exception) {
            // Disconnected
        }
    }

    get("/stream/{id}") {
        val cameraId = call.parameters["id"] ?: return@get
        val resParam = call.request.queryParameters["res"] ?: "640x480"
        val fpsParam = call.request.queryParameters["fps"]?.toIntOrNull() ?: 30
        val qualityParam = call.request.queryParameters["quality"]?.toIntOrNull() ?: 50
        val grayscaleParam = call.request.queryParameters["grayscale"]?.toBooleanStrictOrNull() ?: false
        val parts = resParam.split("x")
        val width = parts.getOrNull(0)?.toIntOrNull() ?: 640
        val height = parts.getOrNull(1)?.toIntOrNull() ?: 480

        val boundary = "frame"
        call.respondBytesWriter(contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")) {
            streamCamera(cameraManager.getFlow(cameraId, width, height, fpsParam, qualityParam, grayscaleParam), boundary)
        }
    }

    get("/api/cameras") {
        if (!cameraManager.isReady()) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Camera provider not ready"))
        } else {
            call.respond(cameraManager.getCameraInfos())
        }
    }

    get("/api/camera/stats") {
        call.respond(cameraManager.getCameraStats())
    }

    post("/api/camera/control") {
        val params = call.request.queryParameters
        val zoom = params["zoom"]?.toFloatOrNull()
        val exposure = params["exposure"]?.toIntOrNull()
        val torch = params["torch"]?.toBooleanStrictOrNull()

        var updated = false
        if (zoom != null) {
            updated = cameraManager.setZoomRatio(zoom) || updated
        }
        if (exposure != null) {
            updated = cameraManager.setExposureCompensation(exposure) || updated
        }
        if (torch != null) {
            cameraManager.setFlashlight(torch)
            updated = true
        }

        call.respond(mapOf("success" to updated, "stats" to cameraManager.getCameraStats()))
    }
}
