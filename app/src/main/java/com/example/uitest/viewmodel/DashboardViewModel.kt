package com.example.uitest.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import com.example.uitest.data.LayoutConfig
import com.example.uitest.data.LayoutRepository
import com.example.uitest.data.ModuleConfig
import com.example.uitest.data.ModuleData
import com.example.uitest.data.Widget
import kotlinx.serialization.json.Json


class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    val uartManager = UartManager(application)
    val bluetoothManager = BluetoothClassicManager(application)
    val cameraManager = CameraManager(application)
    val sensorManager = SensorManager(application)
    val tcpManager = TcpManager()
    val hardwareManager = HardwareManager(application)

    private val repo = LayoutRepository(application)
    private val prefs = application.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", true))
        private set

    var isModulesActive by mutableStateOf(value = false)
        private set

    var sensorDelayFastest by mutableStateOf(value = false)
        private set

    fun toggleModulesActive() {
        isModulesActive = !isModulesActive
    }

    fun updateSensorDelayMode(fastest: Boolean) {
        sensorDelayFastest = fastest
        sensorManager.setSensorDelayMode(
            if (fastest) android.hardware.SensorManager.SENSOR_DELAY_FASTEST
            else android.hardware.SensorManager.SENSOR_DELAY_UI,
        )
    }

    var statePresets by mutableStateOf<List<SnapshotStateList<ModuleConfig>>>(emptyList())
        private set

    var columns by mutableIntStateOf(4)

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        keepScreenOn = enabled
        prefs.edit {
            putBoolean("keep_screen_on", enabled)
        }
    }

    fun publishDomain() {
        server.registerService()
    }

    private val server = DashboardServer(
        context = application,
        port = 8080,
        layoutProvider = {
            val currentLayout = LayoutConfig(
                columns = columns,
                presets = statePresets.toLayoutPresets(),
            )
            Json.encodeToString(currentLayout)
        },
        uartManager = uartManager,
        bluetoothManager = bluetoothManager,
        cameraManager = cameraManager,
        sensorManager = sensorManager,
        tcpManager = tcpManager,
        hardwareManager = hardwareManager,
    ) { id, text ->
        updateLogById(id.toIntOrNull() ?: -1, text)
    }

    init {
        loadLayout()
        server.start()
        // Automatically try to connect to UART on start
        uartManager.connect()
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
        uartManager.disconnect()
        uartManager.unregister()
        bluetoothManager.disconnect()
        sensorManager.stopAll()
        cameraManager.stopStreaming()
        tcpManager.disconnect()
        hardwareManager.stop()
    }

    fun updateLogById(id: Int, newText: String) {
        statePresets.forEach { preset ->
            val index = preset.indexOfFirst { it.id == id }
            if (index != -1) {
                val module = preset[index]
                // Update module data - this triggers recomposition because it's a SnapshotStateList
                preset[index] = module.copy(data = ModuleData(data = newText))
            }
        }
    }

    fun loadLayout() {
        val layout = repo.loadLayout()
        statePresets = layout.toStatePresets()
        columns = layout.columns
    }

    fun saveLayout() {
        val layout = LayoutConfig(
            columns = columns,
            presets = statePresets.toLayoutPresets(),
        )

        repo.saveLayout(layout)
    }

    fun importLayout(uri: Uri) {
        repo.importLayout(uri)
        loadLayout()
    }

    fun addLayoutPreset() {
        val newPreset = mutableStateListOf(
            ModuleConfig(
                id = 0,
                type = "LOG",
                spanX = 1,
                aspRatio = 1f,
            ),
        )
        statePresets += listOf(newPreset)
        saveLayout()
    }

    fun removeLayoutPreset(index: Int) {
        if ((statePresets.size > 1) && (index in statePresets.indices)) {
            val newList = statePresets.toMutableList()
            newList.removeAt(index)
            statePresets = newList
            saveLayout()
        }
    }

    fun updateColumns(newColumns: Int) {
        val coerced = newColumns.coerceIn(1, 8)
        columns = coerced
        saveLayout()
    }

    fun addModuleToPreset(presetIndex: Int) {
        if (presetIndex in statePresets.indices) {
            val modules = statePresets[presetIndex]
            modules.add(
                ModuleConfig(
                    id = modules.size,
                    type = "LOG",
                    spanX = 1,
                    aspRatio = 1f,
                ),
            )
            saveLayout()
        }
    }

    fun removeLastModuleFromPreset(presetIndex: Int) {
        if (presetIndex in statePresets.indices) {
            val modules = statePresets[presetIndex]
            if (modules.isNotEmpty()) {
                modules.removeAt(modules.lastIndex)
                saveLayout()
            }
        }
    }
}

fun LayoutConfig.toStatePresets(): List<SnapshotStateList<ModuleConfig>> {
    return this.presets.values.map { widgetList ->
        widgetList.mapIndexed { index, widget ->
            widget.toModuleConfig(index)
        }.toMutableStateList()
    }
}
fun Widget.toModuleConfig(index: Int): ModuleConfig {
    return ModuleConfig(
        id = index,
        type = this.type,
        spanX = this.spanX,
        aspRatio = this.aspRatio.toFloat(),
    )
}

fun List<SnapshotStateList<ModuleConfig>>.toLayoutPresets(): Map<String, List<Widget>> {
    return this.mapIndexed { index, modules ->
        "preset${index + 1}" to modules.mapIndexed { modIndex, config -> 
            config.toWidget(modIndex) 
        }
    }.toMap()
}

fun ModuleConfig.toWidget(index: Int): Widget {
    return Widget(
        id = index,
        type = type,
        spanX = spanX,
        aspRatio = aspRatio.toDouble(),
    )
}
