package com.example.uitest.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PairedDeviceInfo(val name: String, val address: String)

@SuppressLint("MissingPermission")
class BluetoothClassicManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Flow for real-time WebSocket push
    private val _dataFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val dataFlow = _dataFlow.asSharedFlow()

    private val bufferLock = Any()
    private val incomingBuffer = StringBuilder()

    var onDataReceived: ((String) -> Unit)? = null

    fun getPairedDevices(): List<PairedDeviceInfo> {
        return try {
            bluetoothAdapter?.bondedDevices?.map { 
                PairedDeviceInfo(it.name ?: "Unknown", it.address)
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BT_CLASSIC", "Permission denied for accessing paired devices", e)
            emptyList()
        }
    }

    fun connect(address: String): String {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return "Device not found"
        
        return try {
            disconnect()
            // Use Insecure socket for lower latency on older hardware
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            
            outputStream = socket?.outputStream
            inputStream = socket?.inputStream
            
            startReading()
            "Connected to ${device.name ?: address}"
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
