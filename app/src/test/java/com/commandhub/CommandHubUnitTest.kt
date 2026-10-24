package com.commandhub

import com.commandhub.viewmodel.CameraStats
import com.commandhub.viewmodel.HardwareTelemetry
import com.commandhub.viewmodel.ImuData
import com.commandhub.viewmodel.Resolution
import com.commandhub.viewmodel.SensorData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHubUnitTest {

    @Test
    fun testResolutionToString() {
        val res = Resolution(1920, 1080)
        assertEquals("1920x1080", res.toString())
    }

    @Test
    fun testCameraStatsSerialization() {
        val stats = CameraStats(
            activeCameraId = "0",
            resolution = "1280x720",
            targetFps = 30,
            currentFps = 29.5f,
            totalFramesCaptured = 100L,
            totalFramesDropped = 2L,
            totalBytesStreamed = 1024000L,
            bitrateBps = 2000000L,
            activeViewers = 1,
            zoomRatio = 2.0f,
            torchEnabled = true,
        )

        val jsonString = Json.encodeToString(stats)
        assertTrue(jsonString.contains("\"activeCameraId\":\"0\""))
        assertTrue(jsonString.contains("\"torchEnabled\":true"))

        val decoded = Json.decodeFromString<CameraStats>(jsonString)
        assertEquals(stats.activeCameraId, decoded.activeCameraId)
        assertEquals(stats.currentFps, decoded.currentFps, 0.01f)
    }

    @Test
    fun testImuDataSerialization() {
        val imu = ImuData(
            timestampNanos = 1234567890L,
            accelX = 0.1f,
            accelY = -9.81f,
            accelZ = 0.2f,
            gyroX = 0.01f,
            gyroY = 0.02f,
            gyroZ = -0.01f,
            quatX = 0f,
            quatY = 0f,
            quatZ = 0f,
            quatW = 1f,
        )

        val jsonString = Json.encodeToString(imu)
        assertTrue(jsonString.contains("\"accelY\":-9.81"))

        val decoded = Json.decodeFromString<ImuData>(jsonString)
        assertEquals(imu.accelY, decoded.accelY, 0.001f)
        assertEquals(imu.quatW, decoded.quatW, 0.001f)
    }

    @Test
    fun testHardwareTelemetrySerialization() {
        val telemetry = HardwareTelemetry(
            batteryLevelPct = 85,
            batteryTempCelsius = 32.5f,
            isCharging = true,
            chargingSource = "AC Charger",
            thermalStatus = "Normal",
        )

        val jsonString = Json.encodeToString(telemetry)
        val decoded = Json.decodeFromString<HardwareTelemetry>(jsonString)

        assertEquals(85, decoded.batteryLevelPct)
        assertEquals("AC Charger", decoded.chargingSource)
        assertTrue(decoded.isCharging)
    }

    @Test
    fun testSensorDataSerialization() {
        val sensorData = SensorData(
            type = 1,
            timestamp = 9876543210L,
            values = listOf(0.0f, 9.8f, 0.1f),
        )

        val jsonString = Json.encodeToString(sensorData)
        val decoded = Json.decodeFromString<SensorData>(jsonString)

        assertEquals(1, decoded.type)
        assertEquals(3, decoded.values.size)
        assertEquals(9.8f, decoded.values[1], 0.01f)
    }
}
