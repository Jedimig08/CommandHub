package com.commandhub.viewmodel

import com.commandhub.hardware.ChannelType
import com.commandhub.hardware.HardwareDeviceChannel
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class TcpManager : HardwareDeviceChannel {

    override val type: ChannelType = ChannelType.TCP
    override val isConnected: Boolean
        get() = socket != null && socket?.isConnected == true && isRunning

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    private val _dataFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val dataFlow: SharedFlow<String> = _dataFlow.asSharedFlow()

    override fun connect(target: String): String {
        val parts = target.split(":")
        val ip = parts.getOrNull(0) ?: "127.0.0.1"
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8888
        return connect(ip, port)
    }

    fun connect(ip: String, port: Int): String {
        return try {
            disconnect()
            var result = ""
            val t = thread {
                try {
                    val s = Socket(ip, port)
                    s.tcpNoDelay = true
                    socket = s
                    outputStream = s.getOutputStream()
                    inputStream = s.getInputStream()
                    startReading()
                    result = "Connected to $ip:$port"
                } catch (e: Exception) {
                    Log.e("WifiManager", "Connection failed", e)
                    result = "Error: ${e.message}"
                }
            }
            t.join(5000)
            result.ifEmpty { "Connection timed out" }
        } catch (e: Exception) {
            "Connection logic failed: ${e.message}"
        }
    }

    private fun startReading() {
        isRunning = true
        thread(start = true, name = "WifiReadThread") {
            val buffer = ByteArray(4096)
            while (isRunning) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes, Charsets.UTF_8)
                        _dataFlow.tryEmit(message)
                    } else if (bytes == -1) {
                        isRunning = false
                        break
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.e("WifiManager", "Read error", e)
                    isRunning = false
                    break
                }
            }
            disconnect()
        }
    }

    override fun send(data: String) {
        thread {
            try {
                val bytes = (data + "\n").toByteArray(Charsets.UTF_8)
                outputStream?.write(bytes)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e("WifiManager", "Write failed", e)
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e("WifiManager", "Disconnect error", e)
        } finally {
            inputStream = null
            outputStream = null
            socket = null
        }
    }
}
