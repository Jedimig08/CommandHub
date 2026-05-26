package com.example.uitest.viewmodel

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager

class UartManager(private val context: Context) : SerialInputOutputManager.Listener {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    // Buffer to store incoming data for the web server
    private val bufferLock = Any()
    private val incomingBuffer = StringBuilder()

    // Callback for when new data arrives
    var onDataReceived: ((String) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            if (usbManager.hasPermission(it)) {
                                connect() // Permission verified, try connecting
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun connect(baudRate: Int = 115200): String {
        disconnect() // Clean up any existing connection first
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return "No USB devices found"

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 
                PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, intent)
            return "Requesting USB permission..."
        }

        return try {
            val connection = usbManager.openDevice(device) ?: return "Opening USB device failed"
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            serialPort = port
            val manager = SerialInputOutputManager(port, this)
            ioManager = manager
            manager.start()

            "Connected to ${driver.device.deviceName}"
        } catch (e: Exception) {
            Log.e("UART", "Connection failed", e)
            "Connection failed: ${e.message}"
        }
    }

    fun send(data: String) {
        try {
            // Adding newline as suggested for ESP32 parsing
            serialPort?.write((data + "\n").toByteArray(Charsets.UTF_8), 2000)
        } catch (e: Exception) {
            Log.e("UART", "Write failed", e)
        }
    }

    fun getBuffer(): String {
        synchronized(bufferLock) {
            val data = incomingBuffer.toString()
            incomingBuffer.clear()
            return data
        }
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            val message = it.toString(Charsets.UTF_8)
            synchronized(bufferLock) {
                incomingBuffer.append(message)
            }
            onDataReceived?.invoke(message)
        }
    }

    override fun onRunError(e: Exception?) {
        Log.e("UART", "UART Run Error", e)
    }

    fun disconnect() {
        try {
            ioManager?.stop()
            serialPort?.close()
        } catch (e: Exception) {
            Log.e("UART", "Disconnect failed", e)
        } finally {
            ioManager = null
            serialPort = null
        }
    }
    
    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e("UART", "Unregister failed", e)
        }
    }
}
