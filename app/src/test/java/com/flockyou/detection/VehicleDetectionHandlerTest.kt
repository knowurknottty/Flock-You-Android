package com.flockyou.detection.handler

import android.content.Context
import com.flockyou.data.model.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VehicleDetectionHandlerTest {
    private val handler = BleDetectionHandler(mockk<Context>(relaxed = true))

    @Test fun `Tesla command UUID emits informational vehicle detection`() {
        val result = handler.handleDetection(BleDetectionContext(
            macAddress = "02:11:22:33:44:55",
            deviceName = "S0123456789abcdefC",
            rssi = -48,
            serviceUuids = listOf(UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")),
            manufacturerData = emptyMap(),
            advertisingRate = 1.0f
        ))
        assertNotNull(result)
        assertEquals(DeviceType.TESLA_VEHICLE, result!!.detection.deviceType)
        assertEquals(DetectionMethod.BLE_SERVICE_UUID, result.detection.detectionMethod)
        assertEquals(ThreatLevel.INFO, result.detection.threatLevel)
        assertEquals(5, result.detection.threatScore)
        assertTrue(result.aiPrompt.contains("do not infer", ignoreCase = true))
    }

    @Test fun `Waymo self identifying name remains informational`() {
        val result = handler.handleDetection(BleDetectionContext(
            macAddress = "02:AA:BB:CC:DD:EE",
            deviceName = "Waymo_7A2B",
            rssi = -60, serviceUuids = emptyList(), manufacturerData = emptyMap(), advertisingRate = 1.0f
        ))
        assertNotNull(result)
        assertEquals(DeviceType.WAYMO_VEHICLE, result!!.detection.deviceType)
        assertEquals(DetectionMethod.BLE_DEVICE_NAME, result.detection.detectionMethod)
        assertEquals(ThreatLevel.INFO, result.detection.threatLevel)
        assertTrue(result.detection.matchedPatterns.orEmpty().contains("spoofable", ignoreCase = true))
    }
}
