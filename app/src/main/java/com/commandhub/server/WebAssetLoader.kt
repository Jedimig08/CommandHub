package com.commandhub.server

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class WebAssetLoader(private val context: Context) {
    private val cache = ConcurrentHashMap<String, String>()

    fun load(assetName: String, replacements: Map<String, String> = emptyMap()): String {
        val template = cache.getOrPut(assetName) {
            context.assets.open("web/$assetName").bufferedReader().use { it.readText() }
        }
        if (replacements.isEmpty()) return template

        var result = template
        for ((key, value) in replacements) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    fun loadTerminalPage(
        title: String,
        wsEndpoint: String,
        sendEndpoint: String,
        extraControls: String = ""
    ): String {
        return load(
            assetName = "terminal.html",
            replacements = mapOf(
                "title" to title,
                "wsEndpoint" to wsEndpoint,
                "sendEndpoint" to sendEndpoint,
                "extraControls" to extraControls
            )
        )
    }
}
