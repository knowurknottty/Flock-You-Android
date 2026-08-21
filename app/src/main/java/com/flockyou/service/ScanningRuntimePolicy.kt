package com.flockyou.service

import com.flockyou.data.BatteryAdaptiveMode
import com.flockyou.data.ScanSettings

/**
 * Pure runtime policy for scanner admission and BLE aggressiveness.
 *
 * The default [ScanConfig] is deliberately conservative. Persisted
 * [ScanSettings] must be admitted before this mapper enables the capability
 * for LOW_LATENCY BLE, and the effective battery mode must still be explicit
 * PERFORMANCE before that capability is exercised.
 */
internal object ScanningRuntimePolicy {
    fun toRuntimeScanConfig(settings: ScanSettings): ScanConfig = ScanConfig(
        wifiScanInterval = settings.wifiScanIntervalSeconds * 1000L,
        bleScanDuration = settings.bleScanDurationSeconds * 1000L,
        inactiveTimeout = settings.inactiveTimeoutSeconds * 1000L,
        seenDeviceTimeout = settings.seenDeviceTimeoutMinutes * 60 * 1000L,
        enableBle = settings.enableBleScanning,
        enableWifi = settings.enableWifiScanning,
        trackSeenDevices = settings.trackSeenDevices,
        aggressiveBleMode = true
    )

    fun shouldUseAggressiveBle(
        config: ScanConfig,
        batteryMode: BatteryAdaptiveMode
    ): Boolean = config.aggressiveBleMode && batteryMode == BatteryAdaptiveMode.PERFORMANCE
}
