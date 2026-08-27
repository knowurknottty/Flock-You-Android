package com.flockyou.data.repository

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the ACTIVE deduplicator wired into the persistence layer
 * ([com.flockyou.data.repository.DetectionDeduplicator]).
 *
 * Note: a similarly-named class exists in `com.flockyou.detection` but is not wired into DI and
 * is not used by any repository; this test targets the one actually injected via AppModule.
 */
class DetectionDeduplicatorTest {

    private lateinit var deduplicator: DetectionDeduplicator

    @Before
    fun setUp() {
        deduplicator = DetectionDeduplicator()
    }

    @Test
    fun `shouldThrottle returns false first then true for rapid duplicate`() {
        val d = detection(mac = "AA:BB:CC:DD:EE:FF")
        assertFalse(deduplicator.shouldThrottle(d))
        assertTrue(deduplicator.shouldThrottle(d))
    }

    @Test
    fun `shouldThrottle uses protocol-specific windows`() {
        val ble = detection(mac = "AA:BB:CC:DD:EE:01", protocol = DetectionProtocol.BLUETOOTH_LE)
        val wifi = detection(ssid = "Net", protocol = DetectionProtocol.WIFI)
        assertFalse(deduplicator.shouldThrottle(ble))
        assertFalse(deduplicator.shouldThrottle(wifi))
    }

    @Test
    fun `findMatch returns a match for same device with matching attributes`() {
        val candidate = detection(mac = "11:22:33:44:55:66", deviceName = "FlockCam", manufacturer = "Flock Safety")
        val incoming = detection(mac = "AA:BB:CC:DD:EE:FF", deviceName = "FlockCam", manufacturer = "Flock Safety")
        val match = deduplicator.findMatch(incoming, listOf(candidate))
        assertEquals(candidate, match)
    }

    @Test
    fun `findMatch returns null when device types differ`() {
        val candidate = detection(deviceType = DeviceType.RING_DOORBELL, deviceName = "Cam", manufacturer = "X")
        val incoming = detection(deviceType = DeviceType.FLOCK_SAFETY_CAMERA, deviceName = "Cam", manufacturer = "X")
        assertNull(deduplicator.findMatch(incoming, listOf(candidate)))
    }

    @Test
    fun `findMatch returns null for empty candidates`() {
        assertNull(deduplicator.findMatch(detection(mac = "AA:BB:CC:DD:EE:FF"), emptyList()))
    }

    @Test
    fun `clearThrottleState resets throttle cache`() {
        val d = detection(mac = "AA:BB:CC:DD:EE:FF")
        deduplicator.shouldThrottle(d)
        deduplicator.clearThrottleState()
        assertEquals(0, deduplicator.getThrottleCacheSize())
    }

    private fun detection(
        mac: String? = null,
        ssid: String? = null,
        deviceName: String? = null,
        manufacturer: String? = null,
        deviceType: DeviceType = DeviceType.FLOCK_SAFETY_CAMERA,
        protocol: DetectionProtocol = DetectionProtocol.WIFI,
    ): Detection = Detection(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        protocol = protocol,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = deviceType,
        rssi = -60,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.HIGH,
        macAddress = mac,
        ssid = ssid,
        deviceName = deviceName,
        manufacturer = manufacturer,
    )
}
