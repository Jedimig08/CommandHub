package com.commandhub.viewmodel

import android.app.Application
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
    val settingsManager = container.settingsManager

    private val server = container.server

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    val keepScreenOn: Boolean
        get() = settingsManager.keepScreenOn

    val processingUiUrl: String
        get() = settingsManager.processingUiUrl

    val sensorDelayFastest: Boolean
        get() = settingsManager.sensorDelayFastest

    fun updateProcessingUiUrl(url: String) {
        mainHandler.post {
            settingsManager.updateProcessingUiUrl(url)
        }
    }

    fun updateSensorDelayMode(fastest: Boolean) {
        mainHandler.post {
            settingsManager.updateSensorDelayMode(fastest)
        }
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        mainHandler.post {
            settingsManager.setKeepScreenOnEnabled(enabled)
        }
    }

    fun publishDomain() {
        mainHandler.post {
            server.registerService()
        }
    }

    init {
        // Server and managers managed via CommandHubContainer & CommandHubService
    }

    override fun onCleared() {
        super.onCleared()
        // Activities can clear, but background service keeps server running persistently
    }
}
