package com.example.uitest.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class HardwareTelemetry(
    val batteryLevelPct: Int = 0,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageV: Float = 0f,
    val currentNowMa: Float = 0f,
    val powerWatts: Float = 0f,
    val isCharging: Boolean = false,
    val chargingSource: String = "Unknown",
    val thermalStatus: String = "Normal",
    val cpuUsagePct: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
)

class HardwareManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val _telemetryFlow = MutableStateFlow(HardwareTelemetry())
    val telemetryFlow: StateFlow<HardwareTelemetry> = _telemetryFlow.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?

    init {
        scope.launch {
            while (true) {
                updateTelemetry()
                delay(1000L.milliseconds)
            }
        }
    }

    private fun updateTelemetry() {
        try {
            // Battery Intent
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)

            var level = 0
            var tempCelsius = 0f
            var voltageV = 0f
            var isCharging = false
            var chargingSource = "Unknown"

            batteryStatus?.let { intent ->
                val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && scale > 0) {
                    level = (rawLevel * 100) / scale
                }

                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                tempCelsius = rawTemp / 10f

                val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                voltageV = rawVoltage / 1000f

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                chargingSource = when (plugType) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> if (isCharging) "Connected" else "Discharging"
                }
            }

            // Current & Power (microamps -> milliamps -> watts)
            var currentNowMa = 0f
            if (batteryManager != null) {
                val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toFloat()
                // BATTERY_PROPERTY_CURRENT_NOW returns microamps. Some devices return negative for discharge.
                currentNowMa = currentUa / 1000f
            }
            val powerWatts = (voltageV * abs(currentNowMa)) / 1000f

            // Thermal Status (API 29+)
            val thermalStatusStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normal"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                    else -> "Unknown"
                }
            } else {
                if (tempCelsius > 42f) "Warm" else "Normal"
            }

            // RAM Usage
            val runtime = Runtime.getRuntime()
            val totalMemoryBytes = runtime.totalMemory()
            val freeMemoryBytes = runtime.freeMemory()
            val usedMemoryBytes = totalMemoryBytes - freeMemoryBytes
            val ramTotalMb = runtime.maxMemory() / (1024 * 1024)
            val ramUsedMb = usedMemoryBytes / (1024 * 1024)

            // CPU Usage calculation from /proc/stat
            val cpuUsage = readCpuUsage()

            _telemetryFlow.value = HardwareTelemetry(
                batteryLevelPct = level,
                batteryTempCelsius = tempCelsius,
                batteryVoltageV = voltageV,
                currentNowMa = currentNowMa,
                powerWatts = powerWatts,
                isCharging = isCharging,
                chargingSource = chargingSource,
                thermalStatus = thermalStatusStr,
                cpuUsagePct = cpuUsage,
                ramUsedMb = ramUsedMb,
                ramTotalMb = ramTotalMb,
            )
        } catch (e: Exception) {
            Log.e("HardwareManager", "Error updating telemetry", e)
        }
    }

    private var lastAppCpuTime = 0L
    private var lastWallTime = 0L

    private fun readCpuUsage(): Float {
        try {
            val nowWall = System.currentTimeMillis()
            val nowCpu = Process.getElapsedCpuTime()

            if (lastWallTime == 0L) {
                lastWallTime = nowWall
                lastAppCpuTime = nowCpu
                return 0f
            }

            val wallDiff = nowWall - lastWallTime
            val cpuDiff = nowCpu - lastAppCpuTime

            lastWallTime = nowWall
            lastAppCpuTime = nowCpu

            if (wallDiff > 0) {
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                val usage = ((cpuDiff.toFloat() / wallDiff.toFloat()) * 100f) / cores
                return usage.coerceIn(0f, 100f)
            }
        } catch (e: Exception) {
            Log.e("HardwareManager", "Error reading CPU usage", e)
        }
        return 0f
    }

    fun stop() {
        // Coroutine scope job can be cancelled if needed
    }
}
