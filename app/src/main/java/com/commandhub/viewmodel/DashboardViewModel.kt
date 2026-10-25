package com.commandhub.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    val uartManager = UartManager(application)
    val bluetoothManager = BluetoothClassicManager(application)
    val cameraManager = CameraManager(application)
    val sensorManager = SensorManager(application)
    val tcpManager = TcpManager()
    val hardwareManager = HardwareManager(application)

    private val prefs = application.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", true))
        private set

    var processingUiUrl by mutableStateOf(prefs.getString("processing_ui_url", "http://127.0.0.1:5000/") ?: "http://127.0.0.1:5000/")
        private set

    fun updateProcessingUiUrl(url: String) {
        processingUiUrl = url
        prefs.edit { putString("processing_ui_url", url) }
    }

    var sensorDelayFastest by mutableStateOf(false)
        private set

    fun updateSensorDelayMode(fastest: Boolean) {
        sensorDelayFastest = fastest
        sensorManager.setSensorDelayMode(
            if (fastest) android.hardware.SensorManager.SENSOR_DELAY_FASTEST
            else android.hardware.SensorManager.SENSOR_DELAY_UI,
        )
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        keepScreenOn = enabled
        prefs.edit { putBoolean("keep_screen_on", enabled) }
    }

    fun publishDomain() {
        server.registerService()
    }

    private val server = DashboardServer(
        context = application,
        port = 8080,
        layoutProvider = { "{}" },
        getProcessingUrl = { processingUiUrl },
        setProcessingUrl = { updateProcessingUiUrl(it) },
        publishDomain = { publishDomain() },
        getKeepScreenOn = { keepScreenOn },
        setKeepScreenOnEnabled = { setKeepScreenOnEnabled(it) },
        getSensorDelayFastest = { sensorDelayFastest },
        updateSensorDelayMode = { updateSensorDelayMode(it) },
        uartManager = uartManager,
        bluetoothManager = bluetoothManager,
        cameraManager = cameraManager,
        sensorManager = sensorManager,
        tcpManager = tcpManager,
        hardwareManager = hardwareManager,
    ) { _, _ -> }

    init {
        server.start()
        uartManager.connect()
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
        uartManager.disconnect()
        uartManager.unregister()
        bluetoothManager.disconnect()
        sensorManager.stopAll()
        cameraManager.stopStreaming()
        tcpManager.disconnect()
        hardwareManager.stop()
    }
}
