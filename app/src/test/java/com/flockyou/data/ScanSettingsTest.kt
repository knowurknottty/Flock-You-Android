package com.flockyou.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSettingsTest {
    @Test
    fun autoBattery_neverPromotesChargedDeviceToPerformance() {
        assertEquals(BatteryAdaptiveMode.BALANCED, BatteryAdaptiveMode.forBatteryLevel(100))
        assertEquals(BatteryAdaptiveMode.BALANCED, BatteryAdaptiveMode.forBatteryLevel(51))
    }

    @Test
    fun autoBattery_conservesAtLowBatteryThresholds() {
        assertEquals(BatteryAdaptiveMode.BATTERY_SAVER, BatteryAdaptiveMode.forBatteryLevel(30))
        assertEquals(BatteryAdaptiveMode.MINIMAL, BatteryAdaptiveMode.forBatteryLevel(15))
    }

    @Test
    fun manualPerformance_remainsAvailable() {
        val settings = ScanSettings(batteryAdaptiveMode = "performance", autoBatteryAdaptive = false)
        assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
    }

    @Test
    fun effectiveWifiInterval_respectsConfiguredBaseAndBatteryMode() {
        val balanced = ScanSettings(
            wifiScanIntervalSeconds = 45,
            batteryAdaptiveMode = "balanced",
            autoBatteryAdaptive = false
        )
        assertEquals(45, balanced.getEffectiveWifiInterval(100))

        val saver = balanced.copy(batteryAdaptiveMode = "battery_saver")
        assertEquals(67, saver.getEffectiveWifiInterval(100))

        val performance = balanced.copy(batteryAdaptiveMode = "performance")
        assertEquals(22, performance.getEffectiveWifiInterval(100))
    }
}
