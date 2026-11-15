package com.commandhub.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PairedDeviceInfo(val name: String, val address: String)

class BluetoothClassicManager(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Flow for real-time WebSocket push
    private val _dataFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val dataFlow: SharedFlow<String> = _dataFlow.asSharedFlow()

    private val bufferLock = Any()
    private val incomingBuffer = StringBuilder()

    var onDataReceived: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<PairedDeviceInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BT_CLASSIC", "BLUETOOTH_CONNECT permission not granted")
            return emptyList()
        }
        if (bluetoothAdapter == null) {
            Log.e("BT_CLASSIC", "Bluetooth adapter not available")
            return emptyList()
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.w("BT_CLASSIC", "Bluetooth adapter is disabled")
            return emptyList()
        }
        return try {
            bluetoothAdapter.bondedDevices?.map {
                PairedDeviceInfo(it.name ?: "Unknown", it.address)
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BT_CLASSIC", "Permission denied for accessing paired devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return "Connection failed: BLUETOOTH_CONNECT permission not granted"
        }
        if (bluetoothAdapter == null) {
            return "Bluetooth adapter not available"
        }
        if (!bluetoothAdapter.isEnabled) {
            return "Connection failed: Bluetooth is disabled"
        }
        val device = bluetoothAdapter.getRemoteDevice(address) ?: return "Device not found"
        
        return try {
            disconnect()
            // Use Insecure socket for lower latency on older hardware
            socket = device.createInsecureRfcommSocketToServiceRecord(sppUuid)
            socket?.connect()
            
            outputStream = socket?.outputStream
            inputStream = socket?.inputStream
            
            startReading()
            "Connected to ${device.name ?: address}"
        } catch (e: SecurityException) {
            Log.e("BT_CLASSIC", "SecurityException during connection", e)
            "Connection failed: Security exception - permission denied (${e.message})"
        } catch (e: IOException) {
            Log.e("BT_CLASSIC", "Connection failed", e)
            "Connection failed: ${e.message}"
        }
    }

    private fun startReading() {
        isRunning = true
        thread(start = true, name = "BT_ReadThread", priority = Thread.MAX_PRIORITY) {
            val buffer = ByteArray(2048)
            while (isRunning) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes, Charsets.UTF_8)
                        Log.d("BT_SPEED", "RX from ESP32: $message")
                        synchronized(bufferLock) {
                            if (incomingBuffer.length > 100_000) {
                                incomingBuffer.delete(0, 50_000)
                            }
                            incomingBuffer.append(message)
                        }
                        _dataFlow.tryEmit(message)
                        onDataReceived?.invoke(message)
                    }
                } catch (e: IOException) {
                    if (isRunning) {
                        Log.e("BT_CLASSIC", "Read error", e)
                        isRunning = false
                    }
                    break
                }
            }
        }
    }

    fun send(data: String) {
        try {
            Log.d("BT_SPEED", "TX to ESP32: $data")
            val bytes = (data + "\n").toByteArray(Charsets.UTF_8)
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e("BT_CLASSIC", "Write failed", e)
        }
    }

    @Suppress("Unused")
    fun getBuffer(): String {
        synchronized(bufferLock) {
            val data = incomingBuffer.toString()
            incomingBuffer.clear()
            return data
        }
    }

    fun disconnect() {
        isRunning = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e("BT_CLASSIC", "Disconnect error", e)
        } finally {
            inputStream = null
            outputStream = null
            socket = null
        }
    }
}
