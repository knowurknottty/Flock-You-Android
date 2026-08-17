package com.flockyou.service

/**
 * Data models previously nested inside ScanningService.
 *
 * Extracted for:
 * - Reduced coupling: UI/IPC code can import models without importing the full service
 * - Testability: Models can be constructed in unit tests without a service context
 * - File size: Keeps ScanningService.kt focused on lifecycle and scan orchestration
 *
 * Related files:
 * - ScanningServiceState.kt: Companion object state flows and utility methods
 * - ScanningService.kt: Service lifecycle, scan loop, detection processing
 * - ScanningServiceIpc.kt: IPC serialization/deserialization of these models
 */

// Default scan timing values
private const val DEFAULT_WIFI_SCAN_INTERVAL = 25000L
private const val DEFAULT_BLE_SCAN_DURATION = 10000L
private const val DEFAULT_BLE_COOLDOWN = 4000L
private const val DEFAULT_INACTIVE_TIMEOUT = 60000L
private const val DEFAULT_SEEN_DEVICE_TIMEOUT = 300000L

/** Runtime scan configuration */
data class ScanConfig(
    val wifiScanInterval: Long = DEFAULT_WIFI_SCAN_INTERVAL,
    val bleScanDuration: Long = DEFAULT_BLE_SCAN_DURATION,
    val bleCooldown: Long = DEFAULT_BLE_COOLDOWN,
    val inactiveTimeout: Long = DEFAULT_INACTIVE_TIMEOUT,
    val seenDeviceTimeout: Long = DEFAULT_SEEN_DEVICE_TIMEOUT,
    val enableBle: Boolean = true,
    val enableWifi: Boolean = true,
    val enableCellular: Boolean = true,
    val trackSeenDevices: Boolean = true,
    val aggressiveBleMode: Boolean = true
)

/** Seen device that didn't match surveillance patterns */
data class SeenDevice(
    val id: String,
    val name: String?,
    val type: String,
    val rssi: Int,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val seenCount: Int = 1,
    val manufacturer: String? = null,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Map<Int, String> = emptyMap(),
    val advertisingRate: Float = 0f
)

/** Learned device signature (user-confirmed suspicious device) */
data class LearnedSignature(
    val id: String,
    val name: String?,
    val macPrefix: String,
    val serviceUuids: List<String>,
    val manufacturerIds: List<Int>,
    val learnedAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)

/** Scan statistics */
data class ScanStatistics(
    val totalBleScans: Int = 0,
    val totalWifiScans: Int = 0,
    val successfulWifiScans: Int = 0,
    val throttledWifiScans: Int = 0,
    val bleDevicesSeen: Int = 0,
    val wifiNetworksSeen: Int = 0,
    val detectionsCreated: Int = 0,
    val lastBleSuccessTime: Long? = null,
    val lastWifiSuccessTime: Long? = null
)

/** Overall scanning status */
sealed class ScanStatus {
    object Idle : ScanStatus()
    object Starting : ScanStatus()
    object Active : ScanStatus()
    object Stopping : ScanStatus()
    data class Error(val message: String, val recoverable: Boolean = true) : ScanStatus()

    fun toIpcString(): String = when (this) {
        is Idle -> "Idle"
        is Starting -> "Starting"
        is Active -> "Active"
        is Stopping -> "Stopping"
        is Error -> "Error:${this.message}"
    }

    companion object {
        fun fromIpcString(str: String): ScanStatus = when {
            str == "Idle" -> Idle
            str == "Starting" -> Starting
            str == "Active" -> Active
            str == "Stopping" -> Stopping
            str.startsWith("Error:") -> Error(str.removePrefix("Error:"))
            else -> Idle
        }
    }
}

/** Individual subsystem status */
sealed class SubsystemStatus {
    object Idle : SubsystemStatus()
    object Active : SubsystemStatus()
    object Disabled : SubsystemStatus()
    data class Error(val code: Int, val message: String) : SubsystemStatus()
    data class PermissionDenied(val permission: String) : SubsystemStatus()

    fun toIpcString(): String = when (this) {
        is Idle -> "Idle"
        is Active -> "Active"
        is Disabled -> "Disabled"
        is Error -> "Error:${this.code}:${this.message}"
        is PermissionDenied -> "PermissionDenied:${this.permission}"
    }

    companion object {
        fun fromIpcString(str: String): SubsystemStatus = when {
            str == "Idle" -> Idle
            str == "Active" -> Active
            str == "Disabled" -> Disabled
            str.startsWith("Error:") -> {
                val parts = str.removePrefix("Error:").split(":", limit = 2)
                Error(parts.getOrNull(0)?.toIntOrNull() ?: -1, parts.getOrElse(1) { "Unknown" })
            }
            str.startsWith("PermissionDenied:") -> PermissionDenied(str.removePrefix("PermissionDenied:"))
            else -> Idle
        }
    }
}

/** Error log entry */
data class ScanError(
    val timestamp: Long = System.currentTimeMillis(),
    val subsystem: String,
    val code: Int,
    val message: String,
    val recoverable: Boolean = true
)

/** Detector health status for monitoring individual detector subsystems */
data class DetectorHealthStatus(
    val name: String,
    val isRunning: Boolean = false,
    val lastSuccessfulScan: Long? = null,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
    val lastErrorTime: Long? = null,
    val restartCount: Int = 0,
    val isHealthy: Boolean = true
) {
    companion object {
        const val DETECTOR_ULTRASONIC = "Ultrasonic"
        const val DETECTOR_ROGUE_WIFI = "RogueWiFi"
        const val DETECTOR_RF_SIGNAL = "RfSignal"
        const val DETECTOR_CELLULAR = "Cellular"
        const val DETECTOR_GNSS = "GNSS"
        const val DETECTOR_SATELLITE = "Satellite"
        const val DETECTOR_BLE = "BLE"
        const val DETECTOR_WIFI = "WiFi"
    }
}

/** Callback interface for detectors to report errors and health status */
interface DetectorCallback {
    fun onError(detectorName: String, error: String, recoverable: Boolean = true)
    fun onScanSuccess(detectorName: String)
    fun onDetectorStarted(detectorName: String)
    fun onDetectorStopped(detectorName: String)
}
