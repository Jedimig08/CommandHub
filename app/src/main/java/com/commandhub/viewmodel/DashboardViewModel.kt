package com.commandhub.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.commandhub.data.CommandHubContainer

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val container = CommandHubContainer.getInstance(application)
    val uartManager = container.uartManager
    val bluetoothManager = container.bluetoothManager
    val cameraManager = container.cameraManager
    val sensorManager = container.sensorManager
    val tcpManager = container.tcpManager
    val hardwareManager = container.hardwareManager
    val privacyManager = container.privacyManager
    val authManager = container.authManager

    private val server = container.server

    var authToken by mutableStateOf(authManager.getToken())
        private set

    fun rotateAuthToken() {
        mainHandler.post {
            authToken = authManager.rotateToken()
        }
    }

    private val prefs = application.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", true))
        private set

    var processingUiUrl by mutableStateOf(prefs.getString("processing_ui_url", "http://127.0.0.1:5000/") ?: "http://127.0.0.1:5000/")
        private set

    fun updateProcessingUiUrl(url: String) {
        mainHandler.post {
            processingUiUrl = url
            prefs.edit { putString("processing_ui_url", url) }
        }
    }

    var sensorDelayFastest by mutableStateOf(prefs.getBoolean("sensor_delay_fastest", false))
        private set

    fun updateSensorDelayMode(fastest: Boolean) {
        mainHandler.post {
            sensorDelayFastest = fastest
            prefs.edit { putBoolean("sensor_delay_fastest", fastest) }
            sensorManager.setSensorDelayMode(
                if (fastest) android.hardware.SensorManager.SENSOR_DELAY_FASTEST
                else android.hardware.SensorManager.SENSOR_DELAY_UI,
            )
        }
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        mainHandler.post {
            keepScreenOn = enabled
            prefs.edit { putBoolean("keep_screen_on", enabled) }
        }
    }

    fun publishDomain() {
        mainHandler.post {
            server.registerService()
        }
    }

    init {
        // Server and managers managed via CommandHubContainer & CommandHubService
        if (sensorDelayFastest) {
            sensorManager.setSensorDelayMode(android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Activities can clear, but background service keeps server running persistently
    }
}
