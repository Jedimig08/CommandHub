package com.example.uitest.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Widget(
    val id: Int,
    val type: String,
    val spanX: Int,
    val aspRatio: Double
)

@Serializable
data class LayoutConfig(
    val columns: Int,
    val presets: Map<String, List<Widget>>
)

class LayoutRepository(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val file: File
        get() = File(context.filesDir, "layout.json")

    /** Copy the default layout from assets to internal storage if it doesn't exist */
    private fun ensureFileExists() {
        if (!file.exists()) {
            context.assets.open("layout.json").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    fun importLayout(uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    /** Load LayoutConfig from internal storage */
    fun loadLayout(): LayoutConfig {
        ensureFileExists()
        return try {
            val jsonString = file.readText()
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            // If parsing fails (e.g. ID changed from String to Int), reset to default from assets
            context.assets.open("layout.json").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val jsonString = file.readText()
            json.decodeFromString(jsonString)
        }
    }

    /** Save LayoutConfig back to internal storage */
    fun saveLayout(layout: LayoutConfig) {
        val jsonString = json.encodeToString(layout)
        file.writeText(jsonString)
    }

}

