package com.example.uitest.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

data class ModuleConfig(
    val id: Int,
    val type: String,
    val spanX: Int,
    val aspRatio: Float,
    val color: Color = Color.DarkGray,
    val data: ModuleData? = null
)

data class ModuleData(
    val data: String?
)

@Serializable
data class TermuxMessage(
    val type: String, // "LAYOUT" or "LOG"
    val content: String // The actual JSON string or raw text
)