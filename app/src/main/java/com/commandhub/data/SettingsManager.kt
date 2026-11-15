package com.commandhub.data

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.commandhub.viewmodel.SensorManager

class SettingsManager(context: Context, private val sensorManager: SensorManager) {
    private val prefs = context.applicationContext.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", true))
        private set

    var processingUiUrl by mutableStateOf(prefs.getString("processing_ui_url", "http://127.0.0.1:5000/") ?: "http://127.0.0.1:5000/")
        private set

    var sensorDelayFastest by mutableStateOf(prefs.getBoolean("sensor_delay_fastest", false))
        private set

    init {
        if (sensorDelayFastest) {
            sensorManager.setSensorDelayMode(android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        keepScreenOn = enabled
        prefs.edit { putBoolean("keep_screen_on", enabled) }
    }

    fun updateProcessingUiUrl(url: String) {
        processingUiUrl = url
        prefs.edit { putString("processing_ui_url", url) }
    }

    fun updateSensorDelayMode(fastest: Boolean) {
        sensorDelayFastest = fastest
        prefs.edit { putBoolean("sensor_delay_fastest", fastest) }
        sensorManager.setSensorDelayMode(
            if (fastest) android.hardware.SensorManager.SENSOR_DELAY_FASTEST
            else android.hardware.SensorManager.SENSOR_DELAY_UI,
        )
    }
}
