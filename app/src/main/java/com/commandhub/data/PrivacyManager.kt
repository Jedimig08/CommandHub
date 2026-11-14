package com.commandhub.data

import android.content.Context
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
    val logEnabled: Boolean = true,
    val microphoneEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val fileSystemEnabled: Boolean = true,
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
            logEnabled = prefs.getBoolean("log_enabled", true),
            microphoneEnabled = prefs.getBoolean("microphone_enabled", true),
            locationEnabled = prefs.getBoolean("location_enabled", true),
            fileSystemEnabled = prefs.getBoolean("file_system_enabled", true),
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
            putBoolean("log_enabled", newState.logEnabled)
            putBoolean("microphone_enabled", newState.microphoneEnabled)
            putBoolean("location_enabled", newState.locationEnabled)
            putBoolean("file_system_enabled", newState.fileSystemEnabled)
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
            "log" -> currentState.copy(logEnabled = enabled)
            "microphone" -> currentState.copy(microphoneEnabled = enabled)
            "location" -> currentState.copy(locationEnabled = enabled)
            "filesystem", "file_system" -> currentState.copy(fileSystemEnabled = enabled)
            else -> currentState
        }
        updateState(updated)
        return updated
    }

    private fun notifyStateChanged(state: PrivacyState) {
        onPrivacyStateChanged?.invoke(state)
    }

    fun isLoopbackAddress(host: String?): Boolean {
        if (host == null) return false
        val cleanHost = host.trim()
        return cleanHost == "127.0.0.1" ||
                cleanHost == "localhost" ||
                cleanHost == "::1" ||
                cleanHost == "0:0:0:0:0:0:0:1"
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
            cleanHost == "0:0:0:0:0:0:0:1"
}

private fun isPathMatch(normPath: String, vararg targets: String): Boolean {
    return targets.any { target ->
        val t = target.lowercase()
        normPath == t || normPath.startsWith("$t/") || normPath.startsWith("$t-") || normPath.startsWith("$t?")
    }
}

fun PrivacyState.evaluateAccess(path: String, remoteHost: String): String? {
    val isLocal = isLoopbackAddress(remoteHost)

    // Privacy management routes are accessible locally even if offline, so user can re-configure settings
    if (path == "/privacy" || path.startsWith("/api/privacy")) {
        if (!isLocal && this.accessMode != AccessMode.ONLINE) {
            return "Privacy settings page is restricted to local on-device access while offline or local-only."
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
        return "Bluetooth SPP terminal has been disabled by Privacy Settings."
    }

    // UART features: ONLY block uart endpoints
    if (isPathMatch(normPath, "/uart", "/uart-send", "/uart-ws", "/api/uart") && !this.uartEnabled) {
        return "UART terminal has been disabled by Privacy Settings."
    }

    // TCP features: ONLY block tcp endpoints
    if (isPathMatch(normPath, "/tcp", "/tcp-send", "/tcp-connect", "/tcp-ws", "/api/tcp") && !this.tcpEnabled) {
        return "TCP console has been disabled by Privacy Settings."
    }

    // Flashlight features: ONLY block flashlight endpoints
    if (isPathMatch(normPath, "/api/flashlight") && !this.flashlightEnabled) {
        return "Flashlight control has been disabled by Privacy Settings."
    }

    // Log features: ONLY block log endpoints
    if (isPathMatch(normPath, "/log", "/api/log") && !this.logEnabled) {
        return "Log viewer has been disabled by Privacy Settings."
    }

    // Layout features: ONLY block layout endpoints
    if (isPathMatch(normPath, "/layout") && !this.fileSystemEnabled) {
        return "File layout access has been disabled by Privacy Settings."
    }

    return null
}
