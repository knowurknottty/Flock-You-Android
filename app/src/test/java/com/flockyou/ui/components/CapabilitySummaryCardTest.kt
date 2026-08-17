package com.flockyou.ui.components

import com.flockyou.data.FeasibilityLevel
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.privilege.PrivilegeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitySummaryCardTest {
    private val fullPhone = DeviceHardwareCapabilities(
        hasBle = true,
        hasWifi = true,
        hasTelephony = true,
        hasGps = true,
        hasMicrophone = true,
        hasNfc = false
    )

    @Test
    fun sideloadCellular_isPresentedAsHeuristic() {
        val result = resolveCapabilityUiStatus(
            protocol = DetectionProtocol.CELLULAR,
            mode = PrivilegeMode.Sideload,
            hardware = fullPhone,
            androidSdk = 34,
            hasExternalRfHardware = false
        )

        assertEquals(FeasibilityLevel.HEURISTIC_ONLY, result.level)
        assertEquals("Heuristic", result.statusLabel)
        assertTrue(result.enabled)
    }

    @Test
    fun preAndroid15Satellite_isDisabledInsteadOfPretendingToWork() {
        val result = resolveCapabilityUiStatus(
            protocol = DetectionProtocol.SATELLITE,
            mode = PrivilegeMode.Sideload,
            hardware = fullPhone,
            androidSdk = 34,
            hasExternalRfHardware = false
        )

        assertEquals(FeasibilityLevel.NOT_FEASIBLE, result.level)
        assertFalse(result.enabled)
        assertTrue(result.detail.contains("Android 15"))
    }

    @Test
    fun rfWithoutExternalHardware_isLabeledExternalAndDisabled() {
        val result = resolveCapabilityUiStatus(
            protocol = DetectionProtocol.RF,
            mode = PrivilegeMode.Sideload,
            hardware = fullPhone,
            androidSdk = 35,
            hasExternalRfHardware = false
        )

        assertEquals(FeasibilityLevel.NOT_FEASIBLE, result.level)
        assertEquals("External", result.statusLabel)
        assertFalse(result.enabled)
    }

    @Test
    fun rfWithFlipper_isEnabledAsExternalReady() {
        val result = resolveCapabilityUiStatus(
            protocol = DetectionProtocol.RF,
            mode = PrivilegeMode.Sideload,
            hardware = fullPhone,
            androidSdk = 35,
            hasExternalRfHardware = true
        )

        assertEquals(FeasibilityLevel.FULL, result.level)
        assertEquals("External ready", result.statusLabel)
        assertTrue(result.enabled)
    }

    @Test
    fun missingBleHardware_overridesPrivilegeClaims() {
        val noBle = fullPhone.copy(hasBle = false)
        val result = resolveCapabilityUiStatus(
            protocol = DetectionProtocol.BLUETOOTH_LE,
            mode = PrivilegeMode.OEM(platformSigned = true),
            hardware = noBle,
            androidSdk = 35,
            hasExternalRfHardware = false
        )

        assertEquals(FeasibilityLevel.NOT_FEASIBLE, result.level)
        assertFalse(result.enabled)
    }
}
