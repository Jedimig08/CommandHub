package com.commandhub.viewmodel

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
import com.commandhub.hardware.ChannelType
import com.commandhub.hardware.HardwareDeviceChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class UartManager(private val context: Context) : SerialInputOutputManager.Listener, HardwareDeviceChannel {

    override val type: ChannelType = ChannelType.UART
    override val isConnected: Boolean
        get() = serialPort != null && serialPort?.isOpen == true

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    private val actionUsbPermission = "com.android.example.USB_PERMISSION"

    private val _dataFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val dataFlow: SharedFlow<String> = _dataFlow.asSharedFlow()

    // Buffer for legacy HTTP polling
    private val bufferLock = Any()
    private val incomingBuffer = StringBuilder()

    // Callback for when new data arrives
    var onDataReceived: ((String) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (actionUsbPermission == intent.action) {
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
                                connect(currentBaudRate)
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(actionUsbPermission)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    var currentBaudRate: Int = 115200
        private set
    var rxBytesCount: Long = 0L
        private set
    var txBytesCount: Long = 0L
        private set

    override fun connect(target: String): String {
        val baudRate = target.toIntOrNull() ?: currentBaudRate
        return connect(baudRate)
    }

    fun connect(baudRate: Int = 115200): String {
        disconnect() // Clean up any existing connection first
        currentBaudRate = baudRate
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return "No USB devices found"

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 
                PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(context, 0, Intent(actionUsbPermission), flags)
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

            "Connected to ${driver.device.deviceName} @ $baudRate baud"
        } catch (e: Exception) {
            Log.e("UART", "Connection failed", e)
            "Connection failed: ${e.message}"
        }
    }

    override fun send(data: String) {
        try {
            val bytes = (data + "\n").toByteArray(Charsets.UTF_8)
            serialPort?.write(bytes, 2000)
            txBytesCount += bytes.size
        } catch (e: Exception) {
            Log.e("UART", "Write failed", e)
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

    override fun onNewData(data: ByteArray?) {
        data?.let {
            rxBytesCount += it.size
            val message = it.toString(Charsets.UTF_8)
            synchronized(bufferLock) {
                if (incomingBuffer.length > 100_000) {
                    incomingBuffer.delete(0, 50_000)
                }
                incomingBuffer.append(message)
            }
            _dataFlow.tryEmit(message)
            onDataReceived?.invoke(message)
        }
    }

    override fun onRunError(e: Exception?) {
        Log.e("UART", "UART Run Error", e)
    }

    override fun disconnect() {
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
