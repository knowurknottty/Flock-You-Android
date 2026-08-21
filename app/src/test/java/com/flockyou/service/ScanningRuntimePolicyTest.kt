package com.flockyou.service

import com.flockyou.data.BatteryAdaptiveMode
import com.flockyou.data.ScanSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime-policy regressions for the scanning service.
 *
 * These tests deliberately exercise pure service-owned policy so failures are
 * independent of Android framework timing and radio hardware.
 */
class ScanningRuntimePolicyTest {

    @Test
    fun defaultScanConfig_isConservativeUntilPersistedSettingsAreAdmitted() {
        assertFalse(
            "The pre-admission ScanConfig must never opt into aggressive/LOW_LATENCY BLE",
            ScanConfig().aggressiveBleMode
        )
    }

    @Test
    fun admittedSettings_preservePerformanceCapability() {
        val admitted = ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings())

        assertTrue(
            "Persisted/admitted scan settings should retain the capability for explicit PERFORMANCE mode",
            admitted.aggressiveBleMode
        )
    }

    @Test
    fun balancedMode_neverUsesAggressiveBle() {
        val admitted = ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings())

        assertFalse(
            "BALANCED mode must not use LOW_LATENCY BLE even after settings admission",
            ScanningRuntimePolicy.shouldUseAggressiveBle(admitted, BatteryAdaptiveMode.BALANCED)
        )
    }

    @Test
    fun performanceMode_requiresSettingsAdmission() {
        assertFalse(
            "PERFORMANCE cannot escalate a pre-admission/default ScanConfig",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                ScanConfig(),
                BatteryAdaptiveMode.PERFORMANCE
            )
        )

        assertTrue(
            "Explicit PERFORMANCE may use aggressive BLE only after settings admission",
            ScanningRuntimePolicy.shouldUseAggressiveBle(
                ScanningRuntimePolicy.toRuntimeScanConfig(ScanSettings()),
                BatteryAdaptiveMode.PERFORMANCE
            )
        )
    }
}
