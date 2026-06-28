package com.example.uitest.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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


@RequiresApi(Build.VERSION_CODES.O)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    val uartManager = UartManager(application)
    val bluetoothManager = BluetoothClassicManager(application)
    val cameraManager = CameraManager(application)
    val sensorManager = SensorManager(application)

    private val server = DashboardServer(
        context = application,
        port = 8080,
        layoutProvider = {
            val currentLayout = LayoutConfig(
                columns = columns,
                presets = statePresets.toLayoutPresets()
            )
            Json.encodeToString(currentLayout)
        },
        uartManager = uartManager,
        bluetoothManager = bluetoothManager,
        cameraManager = cameraManager,
        sensorManager = sensorManager,
        onLogReceived = { id, text ->
            updateLogById(id.toIntOrNull() ?: -1, text)
        }
    )

    init {
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
    private val repo = LayoutRepository(application)

    var statePresets by mutableStateOf<List<SnapshotStateList<ModuleConfig>>>(emptyList())
        private set

    var columns by mutableIntStateOf(4)

    fun loadLayout() {
        val layout = repo.loadLayout()
        statePresets = layout.toStatePresets()
        columns = layout.columns
    }

    fun saveLayout() {
        val layout = LayoutConfig(
            columns = columns,
            presets = statePresets.toLayoutPresets()
        )

        repo.saveLayout(layout)
    }

    fun importLayout(uri: Uri) {
        repo.importLayout(uri)
        loadLayout()
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
        aspRatio = this.aspRatio.toFloat()
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
        aspRatio = aspRatio.toDouble()
    )
}
