package com.example.uitest.viewmodel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class SensorData(
    val type: Int,
    val timestamp: Long,
    val values: List<Float>
)

@Serializable
data class SensorInfo(
    val id: Int,
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String
)

class SensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager
    private val sensorFlows = mutableMapOf<Int, MutableSharedFlow<SensorData>>()
    private val listeners = mutableMapOf<Int, SensorEventListener>()

    fun getAvailableSensors(): List<SensorInfo> {
        return sensorManager.getSensorList(Sensor.TYPE_ALL).map {
            SensorInfo(it.type, it.name, it.vendor, it.type, it.stringType)
        }
    }

    fun getSensorFlow(sensorType: Int): SharedFlow<SensorData> {
        val flow = sensorFlows.getOrPut(sensorType) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

        if (!listeners.containsKey(sensorType)) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            val data = SensorData(
                                type = it.sensor.type,
                                timestamp = it.timestamp,
                                values = it.values.toList()
                            )
                            flow.tryEmit(data)
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, AndroidSensorManager.SENSOR_DELAY_FASTEST)
                listeners[sensorType] = listener
            }
        }
        return flow
    }

    fun stopSensor(sensorType: Int) {
        listeners.remove(sensorType)?.let {
            sensorManager.unregisterListener(it)
        }
    }

    fun stopAll() {
        listeners.forEach { (_, listener) ->
            sensorManager.unregisterListener(listener)
        }
        listeners.clear()
    }
}
