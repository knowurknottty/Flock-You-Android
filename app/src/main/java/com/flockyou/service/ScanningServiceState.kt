package com.flockyou.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.flockyou.data.model.Detection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Shared state for ScanningService, accessible from any process.
 *
 * IMPORTANT: Process Isolation
 * ScanningService runs in a separate process (":scanning"). These static MutableStateFlows
 * are process-local - they will have different instances in the main app process vs the
 * :scanning process. State synchronization between processes is handled via the
 * Messenger-based IPC mechanism (ScanningServiceIpc).
 *
 * Related files:
 * - ScanningServiceModels.kt: Data classes (ScanConfig, SeenDevice, ScanStatus, etc.)
 * - ScanningService.kt: Service lifecycle, scan loop, detection processing
 * - ScanningServiceIpc.kt: IPC bridge for cross-process state sync
 */
object ScanningServiceState {
    private const val TAG = "ScanningServiceState"

    // Broadcast actions for cross-process communication
    const val ACTION_NUKE_INITIATED = "com.flockyou.NUKE_INITIATED"
    const val ACTION_DATABASE_SHUTDOWN = "com.flockyou.DATABASE_SHUTDOWN"

    // Flag to track if database is available (set to false during nuke)
    val isDatabaseAvailable = MutableStateFlow(true)

    // Current configured values
    val currentSettings = MutableStateFlow(ScanConfig())

    // Core scanning state
    val isScanning = MutableStateFlow(false)
    val lastDetection = MutableStateFlow<Detection?>(null)
    val detectionCount = MutableStateFlow(0)

    // Status tracking
    val scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val bleStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)
    val wifiStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)
    val locationStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)
    val cellularStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)
    val errorLog = MutableStateFlow<List<ScanError>>(emptyList())

    // Seen but unmatched devices
    val seenBleDevices = MutableStateFlow<List<SeenDevice>>(emptyList())
    val seenWifiNetworks = MutableStateFlow<List<SeenDevice>>(emptyList())

    // Cellular monitoring data
    val cellStatus = MutableStateFlow<CellularMonitor.CellStatus?>(null)
    val seenCellTowers = MutableStateFlow<List<CellularMonitor.SeenCellTower>>(emptyList())
    val cellularAnomalies = MutableStateFlow<List<CellularMonitor.CellularAnomaly>>(emptyList())
    val cellularEvents = MutableStateFlow<List<CellularMonitor.CellularEvent>>(emptyList())

    // Satellite monitoring data
    val satelliteState = MutableStateFlow<com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState?>(null)
    val satelliteAnomalies = MutableStateFlow<List<com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly>>(emptyList())
    val satelliteHistory = MutableStateFlow<List<com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionEvent>>(emptyList())
    val satelliteStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)

    // Rogue WiFi monitoring data
    val rogueWifiStatus = MutableStateFlow<RogueWifiMonitor.WifiEnvironmentStatus?>(null)
    val rogueWifiAnomalies = MutableStateFlow<List<RogueWifiMonitor.WifiAnomaly>>(emptyList())
    val rogueWifiEvents = MutableStateFlow<List<RogueWifiMonitor.WifiEvent>>(emptyList())
    val suspiciousNetworks = MutableStateFlow<List<RogueWifiMonitor.SuspiciousNetwork>>(emptyList())

    // RF signal analysis data
    val rfStatus = MutableStateFlow<RfSignalAnalyzer.RfEnvironmentStatus?>(null)
    val rfAnomalies = MutableStateFlow<List<RfSignalAnalyzer.RfAnomaly>>(emptyList())
    val rfEvents = MutableStateFlow<List<RfSignalAnalyzer.RfEvent>>(emptyList())
    val detectedDrones = MutableStateFlow<List<RfSignalAnalyzer.DroneInfo>>(emptyList())

    // Ultrasonic detection data
    val ultrasonicStatus = MutableStateFlow<UltrasonicDetector.UltrasonicStatus?>(null)
    val ultrasonicAnomalies = MutableStateFlow<List<UltrasonicDetector.UltrasonicAnomaly>>(emptyList())
    val ultrasonicEvents = MutableStateFlow<List<UltrasonicDetector.UltrasonicEvent>>(emptyList())
    val ultrasonicBeacons = MutableStateFlow<List<UltrasonicDetector.BeaconDetection>>(emptyList())

    // Shannon SDM diagnostic data (OEM only)
    val shannonDiagStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)
    val shannonAnomalies = MutableStateFlow<List<com.flockyou.shannon.ShannonAnomaly>>(emptyList())

    // GNSS satellite monitoring data
    val gnssStatus = MutableStateFlow<com.flockyou.monitoring.GnssSatelliteMonitor.GnssEnvironmentStatus?>(null)
    val gnssSatellites = MutableStateFlow<List<com.flockyou.monitoring.GnssSatelliteMonitor.SatelliteInfo>>(emptyList())
    val gnssAnomalies = MutableStateFlow<List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomaly>>(emptyList())
    val gnssEvents = MutableStateFlow<List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssEvent>>(emptyList())
    val gnssMeasurements = MutableStateFlow<com.flockyou.monitoring.GnssSatelliteMonitor.GnssMeasurementData?>(null)
    val gnssMonitorStatus = MutableStateFlow<SubsystemStatus>(SubsystemStatus.Idle)

    // Detector health tracking for all subsystems
    val detectorHealth = MutableStateFlow<Map<String, DetectorHealthStatus>>(emptyMap())

    // Battery-adaptive scanning mode
    val currentBatteryMode = MutableStateFlow(com.flockyou.data.BatteryAdaptiveMode.BALANCED)
    val currentBatteryLevel = MutableStateFlow(100)
    val isBatteryCharging = MutableStateFlow(false)

    // Scan statistics
    val scanStats = MutableStateFlow(ScanStatistics())

    // Detection refresh event - emits when detections are added/updated
    private val _detectionRefreshEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val detectionRefreshEvent: SharedFlow<Unit> = _detectionRefreshEvent.asSharedFlow()

    fun emitDetectionRefresh() {
        _detectionRefreshEvent.tryEmit(Unit)
    }

    // Learning mode
    val learningModeEnabled = MutableStateFlow(false)
    val learnedSignatures = MutableStateFlow<List<LearnedSignature>>(emptyList())

    // Packet rate tracking for Signal trigger detection
    private val devicePacketCounts = java.util.concurrent.ConcurrentHashMap<String, java.util.ArrayDeque<Long>>()
    private val packetCountsLock = Any()
    val highActivityDevices = MutableStateFlow<List<String>>(emptyList())

    private const val MAX_ERROR_LOG_SIZE = 50
    private const val MAX_SEEN_DEVICES = 100
    private const val PACKET_RATE_WINDOW_MS = 5000L
    private const val HIGH_ACTIVITY_THRESHOLD = 20f

    private val seenDevicesLock = Any()

    fun clearErrors() {
        errorLog.value = emptyList()
    }

    fun clearSeenDevices() {
        seenBleDevices.value = emptyList()
        seenWifiNetworks.value = emptyList()
    }

    fun enableLearningMode() {
        learningModeEnabled.value = true
    }

    fun disableLearningMode() {
        learningModeEnabled.value = false
    }

    fun learnDeviceSignature(device: SeenDevice, notes: String? = null) {
        val signature = LearnedSignature(
            id = device.id,
            name = device.name,
            macPrefix = device.id.take(8).uppercase(),
            serviceUuids = device.serviceUuids,
            manufacturerIds = device.manufacturerData.keys.toList(),
            notes = notes
        )

        val current = learnedSignatures.value.toMutableList()
        current.removeAll { it.id == device.id }
        current.add(0, signature)
        learnedSignatures.value = current
    }

    fun clearLearnedSignatures() {
        learnedSignatures.value = emptyList()
    }

    fun trackPacket(macAddress: String): Float {
        val now = System.currentTimeMillis()
        val cutoff = now - PACKET_RATE_WINDOW_MS

        val rate = synchronized(packetCountsLock) {
            val packets = devicePacketCounts.getOrPut(macAddress) { java.util.ArrayDeque() }
            packets.addLast(now)

            while (packets.isNotEmpty() && packets.peekFirst() < cutoff) {
                packets.removeFirst()
            }

            if (packets.size > 1) {
                packets.size.toFloat() / (PACKET_RATE_WINDOW_MS / 1000f)
            } else {
                0f
            }
        }

        if (rate >= HIGH_ACTIVITY_THRESHOLD) {
            val current = highActivityDevices.value.toMutableList()
            if (!current.contains(macAddress)) {
                current.add(macAddress)
                highActivityDevices.value = current
            }
        }

        return rate
    }

    fun clearCellularHistory() {
        seenCellTowers.value = emptyList()
        cellularAnomalies.value = emptyList()
        cellularEvents.value = emptyList()
    }

    fun clearSatelliteHistory() {
        satelliteAnomalies.value = emptyList()
        satelliteHistory.value = emptyList()
    }

    fun updateSettings(
        wifiIntervalSeconds: Int = 35,
        bleDurationSeconds: Int = 10,
        inactiveTimeoutSeconds: Int = 60,
        seenDeviceTimeoutMinutes: Int = 5,
        enableBle: Boolean = true,
        enableWifi: Boolean = true,
        enableCellular: Boolean = true,
        trackSeenDevices: Boolean = true
    ) {
        currentSettings.value = ScanConfig(
            wifiScanInterval = wifiIntervalSeconds * 1000L,
            bleScanDuration = bleDurationSeconds * 1000L,
            inactiveTimeout = inactiveTimeoutSeconds * 1000L,
            seenDeviceTimeout = seenDeviceTimeoutMinutes * 60 * 1000L,
            enableBle = enableBle,
            enableWifi = enableWifi,
            enableCellular = enableCellular,
            trackSeenDevices = trackSeenDevices,
            // This API represents an admitted operator configuration. The
            // effective battery mode still gates actual LOW_LATENCY BLE.
            aggressiveBleMode = true
        )
    }

    /**
     * Forcefully stop the scanning service and prevent auto-restart.
     */
    fun forceStop(context: Context) {
        Log.w(TAG, "Force stopping scanning service")

        isScanning.value = false
        scanStatus.value = ScanStatus.Idle
        bleStatus.value = SubsystemStatus.Idle
        wifiStatus.value = SubsystemStatus.Idle
        locationStatus.value = SubsystemStatus.Idle
        cellularStatus.value = SubsystemStatus.Idle
        satelliteStatus.value = SubsystemStatus.Idle

        val intent = Intent(context, ScanningService::class.java)
        context.stopService(intent)
    }
}
