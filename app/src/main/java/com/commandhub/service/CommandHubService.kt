package com.commandhub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.commandhub.R
import com.commandhub.viewmodel.BluetoothClassicManager
import com.commandhub.viewmodel.CameraManager
import com.commandhub.viewmodel.DashboardServer
import com.commandhub.viewmodel.HardwareManager
import com.commandhub.viewmodel.SensorManager
import com.commandhub.viewmodel.TcpManager
import com.commandhub.viewmodel.UartManager

class CommandHubService : Service() {
    private lateinit var server: DashboardServer
    private lateinit var uartManager: UartManager
    private lateinit var bluetoothManager: BluetoothClassicManager
    private lateinit var cameraManager: CameraManager
    private lateinit var sensorManager: SensorManager
    private lateinit var tcpManager: TcpManager
    private lateinit var hardwareManager: HardwareManager

    companion object {
        const val CHANNEL_ID = "CommandHubServiceChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        uartManager = UartManager(application)
        bluetoothManager = BluetoothClassicManager(application)
        cameraManager = CameraManager(application)
        sensorManager = SensorManager(application)
        tcpManager = TcpManager()
        hardwareManager = HardwareManager(application)

        server = DashboardServer(
            context = application,
            port = 8080,
            layoutProvider = { "{}" },
            uartManager = uartManager,
            bluetoothManager = bluetoothManager,
            cameraManager = cameraManager,
            sensorManager = sensorManager,
            tcpManager = tcpManager,
            hardwareManager = hardwareManager,
        ) { _, _ -> }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CommandHub Server Active")
            .setContentText("Listening on port 8080 for remote clients")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        server.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
        uartManager.disconnect()
        uartManager.unregister()
        bluetoothManager.disconnect()
        sensorManager.stopAll()
        cameraManager.stopStreaming()
        tcpManager.disconnect()
        hardwareManager.stop()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "CommandHub Background Server Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}
