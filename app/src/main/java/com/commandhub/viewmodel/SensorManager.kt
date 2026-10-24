package com.commandhub.viewmodel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class SensorData(
    val type: Int,
    val timestamp: Long,
    val values: List<Float>,
)

@Serializable
data class ImuData(
    val timestampNanos: Long = 0L,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val magX: Float = 0f,
    val magY: Float = 0f,
    val magZ: Float = 0f,
    val quatX: Float = 0f,
    val quatY: Float = 0f,
    val quatZ: Float = 0f,
    val quatW: Float = 1f,
)

@Serializable
data class SensorInfo(
    val id: Int,
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String,
)

class SensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager
    private val sensorFlows = mutableMapOf<Int, MutableSharedFlow<SensorData>>()
    private val listeners = mutableMapOf<Int, SensorEventListener>()

    var currentDelayMode: Int = AndroidSensorManager.SENSOR_DELAY_UI
        private set

    fun setSensorDelayMode(delayMode: Int) {
        if (currentDelayMode == delayMode) return
        currentDelayMode = delayMode
        val activeTypes = listeners.keys.toList()
        activeTypes.forEach { type ->
            stopSensor(type)
            registerSensorListener(type)
        }
    }

    fun getAvailableSensors(): List<SensorInfo> {
        return sensorManager.getSensorList(Sensor.TYPE_ALL).map {
            SensorInfo(it.type, it.name, it.vendor, it.type, it.stringType)
        }
    }

    private fun registerSensorListener(sensorType: Int): MutableSharedFlow<SensorData> {
        val flow = sensorFlows.getOrPut(sensorType) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }

        if (!listeners.containsKey(sensorType)) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (flow.subscriptionCount.value == 0) return
                        event?.let {
                            val data = SensorData(
                                type = it.sensor.type,
                                timestamp = it.timestamp,
                                values = it.values.toList(),
                            )
                            flow.tryEmit(data)
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, currentDelayMode)
                listeners[sensorType] = listener
            }
        }
        return flow
    }

    fun getSensorFlow(sensorType: Int): SharedFlow<SensorData> {
        return registerSensorListener(sensorType)
    }

    private val imuFlow = MutableSharedFlow<ImuData>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var isImuRegistered = false

    @Volatile private var latestAccel = FloatArray(3)
    @Volatile private var latestGyro = FloatArray(3)
    @Volatile private var latestMag = FloatArray(3)
    @Volatile private var latestQuat = floatArrayOf(0f, 0f, 0f, 1f)

    fun getImuFlow(): SharedFlow<ImuData> {
        if (!isImuRegistered) {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            val rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    val values = event.values
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            latestAccel = values.clone()
                            val data = ImuData(
                                timestampNanos = event.timestamp,
                                accelX = latestAccel.getOrElse(0) { 0f },
                                accelY = latestAccel.getOrElse(1) { 0f },
                                accelZ = latestAccel.getOrElse(2) { 0f },
                                gyroX = latestGyro.getOrElse(0) { 0f },
                                gyroY = latestGyro.getOrElse(1) { 0f },
                                gyroZ = latestGyro.getOrElse(2) { 0f },
                                magX = latestMag.getOrElse(0) { 0f },
                                magY = latestMag.getOrElse(1) { 0f },
                                magZ = latestMag.getOrElse(2) { 0f },
                                quatX = latestQuat.getOrElse(0) { 0f },
                                quatY = latestQuat.getOrElse(1) { 0f },
                                quatZ = latestQuat.getOrElse(2) { 0f },
                                quatW = latestQuat.getOrElse(3) { 1f },
                            )
                            imuFlow.tryEmit(data)
                        }
                        Sensor.TYPE_GYROSCOPE -> { latestGyro = values.clone() }
                        Sensor.TYPE_MAGNETIC_FIELD -> { latestMag = values.clone() }
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            val q = FloatArray(4)
                            AndroidSensorManager.getQuaternionFromVector(q, values)
                            latestQuat = floatArrayOf(q[1], q[2], q[3], q[0])
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            accel?.let { sensorManager.registerListener(listener, it, currentDelayMode) }
            gyro?.let { sensorManager.registerListener(listener, it, currentDelayMode) }
            mag?.let { sensorManager.registerListener(listener, it, currentDelayMode) }
            rot?.let { sensorManager.registerListener(listener, it, currentDelayMode) }

            isImuRegistered = true
        }
        return imuFlow
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
