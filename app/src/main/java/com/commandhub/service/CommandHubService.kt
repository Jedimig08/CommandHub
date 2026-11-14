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
import com.commandhub.data.CommandHubContainer

class CommandHubService : Service() {
    private lateinit var container: CommandHubContainer

    companion object {
        const val CHANNEL_ID = "CommandHubServiceChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        container = CommandHubContainer.getInstance(application)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CommandHub Server Active")
            .setContentText("Listening on port 8080 for remote clients (Secured)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        container.server.start()
        container.uartManager.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        container.server.stop()
        container.uartManager.disconnect()
        container.uartManager.unregister()
        container.bluetoothManager.disconnect()
        container.sensorManager.stopAll()
        container.cameraManager.stopStreaming()
        container.tcpManager.disconnect()
        container.hardwareManager.stop()
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
