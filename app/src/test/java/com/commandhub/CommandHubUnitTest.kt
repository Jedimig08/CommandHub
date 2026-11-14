package com.commandhub

import com.commandhub.data.AccessMode
import com.commandhub.data.PrivacyState
import com.commandhub.data.evaluateAccess
import com.commandhub.viewmodel.CameraStats
import com.commandhub.viewmodel.HardwareTelemetry
import com.commandhub.viewmodel.ImuData
import com.commandhub.viewmodel.Resolution
import com.commandhub.viewmodel.SensorData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun testPrivacyStateDefaults() {
        val state = PrivacyState()
        assertEquals(AccessMode.ONLINE, state.accessMode)
        assertTrue(state.cameraEnabled)
        assertTrue(state.sensorsEnabled)
        assertTrue(state.hardwareEnabled)
        assertTrue(state.bluetoothEnabled)
        assertTrue(state.uartEnabled)
        assertTrue(state.tcpEnabled)
        assertTrue(state.flashlightEnabled)
        assertTrue(state.logEnabled)
        assertTrue(state.microphoneEnabled)
        assertTrue(state.locationEnabled)
        assertTrue(state.fileSystemEnabled)
    }

    @Test
    fun testPrivacyStateSerialization() {
        val state = PrivacyState(
            accessMode = AccessMode.LOCAL_ONLY,
            cameraEnabled = false,
            sensorsEnabled = true,
        )

        val jsonString = Json.encodeToString(state)
        assertTrue(jsonString.contains("\"accessMode\":\"LOCAL_ONLY\""))
        assertTrue(jsonString.contains("\"cameraEnabled\":false"))

        val decoded = Json.decodeFromString<PrivacyState>(jsonString)
        assertEquals(AccessMode.LOCAL_ONLY, decoded.accessMode)
        assertEquals(false, decoded.cameraEnabled)
        assertEquals(true, decoded.sensorsEnabled)
    }

    @Test
    fun testAccessModeEvaluation() {
        // ONLINE mode allows remote IP
        val onlineState = PrivacyState(accessMode = AccessMode.ONLINE)
        assertNull(onlineState.evaluateAccess("/camera", "192.168.1.50"))

        // LOCAL_ONLY mode blocks remote IP, permits loopback IP
        val localOnlyState = PrivacyState(accessMode = AccessMode.LOCAL_ONLY)
        assertNotNull(localOnlyState.evaluateAccess("/camera", "192.168.1.50"))
        assertNull(localOnlyState.evaluateAccess("/camera", "127.0.0.1"))
        assertNull(localOnlyState.evaluateAccess("/camera", "localhost"))

        // OFFLINE mode blocks remote IP, permits local loopback to privacy settings
        val offlineState = PrivacyState(accessMode = AccessMode.OFFLINE)
        assertNotNull(offlineState.evaluateAccess("/camera", "192.168.1.50"))
        assertNull(offlineState.evaluateAccess("/privacy", "127.0.0.1"))
        assertNotNull(offlineState.evaluateAccess("/privacy", "192.168.1.50"))
    }

    @Test
    fun testIndividualFeatureToggles() {
        val baseState = PrivacyState(accessMode = AccessMode.ONLINE)

        // Camera toggle - should ONLY block camera, leaving everything else fully allowed
        val cameraDisabledState = baseState.copy(cameraEnabled = false)
        assertNotNull(cameraDisabledState.evaluateAccess("/camera", "127.0.0.1"))
        assertNotNull(cameraDisabledState.evaluateAccess("/stream/0", "127.0.0.1"))
        assertNotNull(cameraDisabledState.evaluateAccess("/camera-ws/0", "127.0.0.1"))
        assertNotNull(cameraDisabledState.evaluateAccess("/api/cameras", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/sensors", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/hardware", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/bluetooth", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/uart", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/tcp", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/api/flashlight", "127.0.0.1"))
        assertNull(cameraDisabledState.evaluateAccess("/log", "127.0.0.1"))

        // Sensors toggle
        val sensorsDisabledState = baseState.copy(sensorsEnabled = false)
        assertNotNull(sensorsDisabledState.evaluateAccess("/sensors", "127.0.0.1"))
        assertNotNull(sensorsDisabledState.evaluateAccess("/sensors-ws/1", "127.0.0.1"))
        assertNotNull(sensorsDisabledState.evaluateAccess("/api/sensors", "127.0.0.1"))
        assertNull(sensorsDisabledState.evaluateAccess("/hardware", "127.0.0.1"))

        // Bluetooth toggle
        val bluetoothDisabledState = baseState.copy(bluetoothEnabled = false)
        assertNotNull(bluetoothDisabledState.evaluateAccess("/bluetooth", "127.0.0.1"))
        assertNotNull(bluetoothDisabledState.evaluateAccess("/bluetooth-connect", "127.0.0.1"))

        // Flashlight toggle
        val flashlightDisabledState = baseState.copy(flashlightEnabled = false)
        assertNotNull(flashlightDisabledState.evaluateAccess("/api/flashlight/toggle", "127.0.0.1"))
        assertNull(flashlightDisabledState.evaluateAccess("/hardware", "127.0.0.1"))
    }
}
