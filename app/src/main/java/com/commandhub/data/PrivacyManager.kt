package com.commandhub.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.Serializable

@Serializable
enum class AccessMode {
    ONLINE,
    LOCAL_ONLY,
    OFFLINE,
}

@Serializable
data class PrivacyState(
    val accessMode: AccessMode = AccessMode.ONLINE,
    val cameraEnabled: Boolean = true,
    val sensorsEnabled: Boolean = true,
    val hardwareEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val uartEnabled: Boolean = true,
    val tcpEnabled: Boolean = true,
    val flashlightEnabled: Boolean = true,
)

class PrivacyManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    var onPrivacyStateChanged: ((PrivacyState) -> Unit)? = null

    @Volatile
    private var currentState: PrivacyState = loadState()

    init {
        notifyStateChanged(currentState)
    }

    private fun loadState(): PrivacyState {
        val modeStr = prefs.getString("access_mode", AccessMode.ONLINE.name) ?: AccessMode.ONLINE.name
        val mode = try { AccessMode.valueOf(modeStr) } catch (_: Exception) { AccessMode.ONLINE }

        return PrivacyState(
            accessMode = mode,
            cameraEnabled = prefs.getBoolean("camera_enabled", true),
            sensorsEnabled = prefs.getBoolean("sensors_enabled", true),
            hardwareEnabled = prefs.getBoolean("hardware_enabled", true),
            bluetoothEnabled = prefs.getBoolean("bluetooth_enabled", true),
            uartEnabled = prefs.getBoolean("uart_enabled", true),
            tcpEnabled = prefs.getBoolean("tcp_enabled", true),
            flashlightEnabled = prefs.getBoolean("flashlight_enabled", true),
        )
    }

    @Synchronized
    fun getState(): PrivacyState = currentState

    @Synchronized
    fun updateState(newState: PrivacyState) {
        currentState = newState
        prefs.edit {
            putString("access_mode", newState.accessMode.name)
            putBoolean("camera_enabled", newState.cameraEnabled)
            putBoolean("sensors_enabled", newState.sensorsEnabled)
            putBoolean("hardware_enabled", newState.hardwareEnabled)
            putBoolean("bluetooth_enabled", newState.bluetoothEnabled)
            putBoolean("uart_enabled", newState.uartEnabled)
            putBoolean("tcp_enabled", newState.tcpEnabled)
            putBoolean("flashlight_enabled", newState.flashlightEnabled)
        }
        notifyStateChanged(newState)
    }

    @Synchronized
    fun setAccessMode(mode: AccessMode): PrivacyState {
        val updated = currentState.copy(accessMode = mode)
        updateState(updated)
        return updated
    }

    @Synchronized
    fun setFeatureEnabled(featureName: String, enabled: Boolean): PrivacyState {
        val updated = when (featureName.lowercase()) {
            "camera" -> currentState.copy(cameraEnabled = enabled)
            "sensors" -> currentState.copy(sensorsEnabled = enabled)
            "hardware" -> currentState.copy(hardwareEnabled = enabled)
            "bluetooth" -> currentState.copy(bluetoothEnabled = enabled)
            "uart" -> currentState.copy(uartEnabled = enabled)
            "tcp" -> currentState.copy(tcpEnabled = enabled)
            "flashlight" -> currentState.copy(flashlightEnabled = enabled)
            else -> currentState
        }
        updateState(updated)
        return updated
    }

    private fun notifyStateChanged(state: PrivacyState) {
        // A failing listener must never abort the state change or the HTTP response.
        try {
            onPrivacyStateChanged?.invoke(state)
        } catch (e: Exception) {
            Log.e("PrivacyManager", "Privacy state listener failed", e)
        }
    }

    fun evaluateAccess(path: String, remoteHost: String): String? {
        return currentState.evaluateAccess(path, remoteHost)
    }
}

fun isLoopbackAddress(host: String?): Boolean {
    if (host == null) return false
    val cleanHost = host.trim()
    return cleanHost == "127.0.0.1" ||
            cleanHost == "localhost" ||
            cleanHost == "::1" ||
            cleanHost == "0:0:0:0:0:0:0:1" ||
            cleanHost.equals("::ffff:127.0.0.1", ignoreCase = true)
}

private fun isPathMatch(normPath: String, vararg targets: String): Boolean {
    return targets.any { target ->
        val t = target.lowercase()
        normPath == t || normPath.startsWith("$t/") || normPath.startsWith("$t-") || normPath.startsWith("$t?")
    }
}

fun PrivacyState.evaluateAccess(path: String, remoteHost: String): String? {
    val isLocal = isLoopbackAddress(remoteHost)

    // Privacy management is on-device only: never exposed to the network.
    if (path == "/privacy" || path.startsWith("/api/privacy")) {
        if (!isLocal) {
            return "Privacy settings are only available on the device at 127.0.0.1."
        }
        return null
    }

    // 1. Check Access Mode
    when (this.accessMode) {
        AccessMode.OFFLINE -> {
            if (!isLocal) {
                return "System is in OFFLINE mode. Remote network access is blocked."
            }
        }
        AccessMode.LOCAL_ONLY -> {
            if (!isLocal) {
                return "System is in LOCAL ONLY mode. Remote access from $remoteHost is forbidden."
            }
        }
        AccessMode.ONLINE -> {
            // Remote network access permitted
        }
    }

    // 2. Check Individual Feature Toggles (Surgical feature matching)
    val normPath = path.lowercase()

    // Camera features: ONLY block camera endpoints
    if (isPathMatch(normPath, "/camera", "/stream", "/camera-ws", "/h264-ws", "/api/camera", "/api/cameras") && !this.cameraEnabled) {
        return "Camera access and streaming have been disabled by Privacy Settings."
    }

    // Sensors features: ONLY block sensors endpoints
    if (isPathMatch(normPath, "/sensors", "/sensors-ws", "/api/sensors") && !this.sensorsEnabled) {
        return "Sensors access has been disabled by Privacy Settings."
    }

    // Hardware features: ONLY block hardware endpoints
    if (isPathMatch(normPath, "/hardware", "/api/hardware") && !this.hardwareEnabled) {
        return "Hardware telemetry has been disabled by Privacy Settings."
    }

    // Bluetooth features: ONLY block bluetooth endpoints
    if (isPathMatch(normPath, "/bluetooth", "/bluetooth-send", "/bluetooth-connect", "/bluetooth-ws", "/api/bluetooth") && !this.bluetoothEnabled) {
        return "Bluetooth Console has been disabled by Privacy Settings."
    }

    // UART features: ONLY block uart endpoints
    if (isPathMatch(normPath, "/uart", "/uart-send", "/uart-ws", "/api/uart") && !this.uartEnabled) {
        return "Serial Console has been disabled by Privacy Settings."
    }

    // TCP features: ONLY block tcp endpoints
    if (isPathMatch(normPath, "/tcp", "/tcp-send", "/tcp-connect", "/tcp-ws", "/api/tcp") && !this.tcpEnabled) {
        return "TCP Console has been disabled by Privacy Settings."
    }

    // Flashlight features: ONLY block flashlight endpoints
    if (isPathMatch(normPath, "/api/flashlight") && !this.flashlightEnabled) {
        return "Flashlight control has been disabled by Privacy Settings."
    }

    return null
}
