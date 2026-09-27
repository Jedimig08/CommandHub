package com.example.uitest.viewmodel

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class TcpManager {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    // Flow for real-time WebSocket push
    private val _dataFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val dataFlow = _dataFlow.asSharedFlow()

    fun connect(ip: String, port: Int): String {
        return try {
            disconnect()
            // Run connection on a thread to avoid blocking if called from UI (though server calls it)
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

    fun send(data: String) {
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

    fun disconnect() {
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
