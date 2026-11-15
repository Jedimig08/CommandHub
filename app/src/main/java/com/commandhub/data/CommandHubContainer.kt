package com.commandhub.data

import android.content.Context
import com.commandhub.viewmodel.BluetoothClassicManager
import com.commandhub.viewmodel.CameraManager
import com.commandhub.viewmodel.DashboardServer
import com.commandhub.viewmodel.HardwareManager
import com.commandhub.viewmodel.SensorManager
import com.commandhub.viewmodel.TcpManager
import com.commandhub.viewmodel.UartManager

class CommandHubContainer(context: Context) {
    private val appContext = context.applicationContext
    val authManager = AuthManager(appContext)
    val privacyManager = PrivacyManager(appContext)
    val uartManager = UartManager(appContext)
    val bluetoothManager = BluetoothClassicManager(appContext)
    val cameraManager = CameraManager(appContext)
    val sensorManager = SensorManager(appContext)
    val tcpManager = TcpManager()
    val hardwareManager = HardwareManager(appContext)
    val settingsManager = SettingsManager(appContext, sensorManager)

    val server: DashboardServer

    init {
        server = DashboardServer(
            context = appContext,
            port = 8080,
            uartManager = uartManager,
            bluetoothManager = bluetoothManager,
            cameraManager = cameraManager,
            sensorManager = sensorManager,
            tcpManager = tcpManager,
            hardwareManager = hardwareManager,
            privacyManager = privacyManager,
            authManager = authManager,
            getProcessingUrl = { settingsManager.processingUiUrl },
            setProcessingUrl = { settingsManager.updateProcessingUiUrl(it) },
            publishDomain = { server.registerService() },
            getKeepScreenOn = { settingsManager.keepScreenOn },
            setKeepScreenOnEnabled = { settingsManager.setKeepScreenOnEnabled(it) },
            getSensorDelayFastest = { settingsManager.sensorDelayFastest },
            updateSensorDelayMode = { settingsManager.updateSensorDelayMode(it) },
        )
    }

    companion object {
        @Volatile
        private var instance: CommandHubContainer? = null

        fun getInstance(context: Context): CommandHubContainer {
            return instance ?: synchronized(this) {
                instance ?: CommandHubContainer(context).also { instance = it }
            }
        }
    }
}
