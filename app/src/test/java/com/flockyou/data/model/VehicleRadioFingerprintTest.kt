package com.flockyou.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VehicleRadioFingerprintTest {
    @Test fun `official Tesla vehicle command BLE service is recognized`() {
        val service = UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")
        assertTrue(DetectionPatterns.isTeslaVehicleCommandService(listOf(service)))
        assertFalse(DetectionPatterns.isTeslaVehicleCommandService(listOf(UUID.randomUUID())))
    }

    @Test fun `Tesla vehicle command advertised name is strict lowercase fingerprint`() {
        assertTrue(DetectionPatterns.isTeslaVehicleAdvertisementName("S0123456789abcdefC"))
        assertEquals(DeviceType.TESLA_VEHICLE, DetectionPatterns.matchBleNamePattern("S0123456789abcdefC")?.deviceType)
        for (name in listOf("S0123456789ABCDEFC", "S0123456789abcdefC-extra", "s0123456789abcdefC")) {
            assertFalse(DetectionPatterns.isTeslaVehicleAdvertisementName(name))
            assertNotEquals(DeviceType.TESLA_VEHICLE, DetectionPatterns.matchBleNamePattern(name)?.deviceType)
        }
    }

    @Test fun `Waymo self identifying names are bounded and near matches do not classify`() {
        assertEquals(DeviceType.WAYMO_VEHICLE, DetectionPatterns.matchBleNamePattern("Waymo")?.deviceType)
        assertEquals(DeviceType.WAYMO_VEHICLE, DetectionPatterns.matchBleNamePattern("Waymo_7A2B")?.deviceType)
        for (name in listOf("waymotion", "mywaymoevil", "WaymoCamera123", "Waymo_")) {
            assertNotEquals(DeviceType.WAYMO_VEHICLE, DetectionPatterns.matchBleNamePattern(name)?.deviceType)
        }
    }

    @Test fun `OUI matching rejects locally administered addresses`() {
        assertTrue(DetectionPatterns.isGloballyAdministeredMac("00:0E:8E:11:22:33"))
        assertNotNull(DetectionPatterns.matchMacPrefix("00:0E:8E:11:22:33"))
        assertFalse(DetectionPatterns.isGloballyAdministeredMac("02:0E:8E:11:22:33"))
        assertNull(DetectionPatterns.matchMacPrefix("02:0E:8E:11:22:33"))
        assertFalse(DetectionPatterns.isGloballyAdministeredMac("not-a-mac"))
    }
}
