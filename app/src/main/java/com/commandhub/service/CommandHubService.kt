package com.commandhub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.commandhub.data.CommandHubContainer

class CommandHubService : Service() {
    private var container: CommandHubContainer? = null

    companion object {
        const val CHANNEL_ID = "CommandHubServiceChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CommandHub Server Active")
            .setContentText("Listening on port 8080 for remote clients (Secured)")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val activeContainer = CommandHubContainer.getInstance(application)
        container = activeContainer
        activeContainer.server.start()
        activeContainer.uartManager.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        container?.let { activeContainer ->
            activeContainer.server.stop()
            activeContainer.uartManager.disconnect()
            activeContainer.bluetoothManager.disconnect()
            activeContainer.sensorManager.stopAll()
            activeContainer.cameraManager.stopStreaming()
            activeContainer.tcpManager.disconnect()
            activeContainer.hardwareManager.stop()
        }
        container = null
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
