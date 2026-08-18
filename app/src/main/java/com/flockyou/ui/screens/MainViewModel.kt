package com.flockyou.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.flockyou.data.BroadcastSettings
import com.flockyou.data.BroadcastSettingsRepository
import com.flockyou.data.DetectionSettingsRepository
import com.flockyou.data.FlipperUiSettings
import com.flockyou.data.FlipperUiSettingsRepository
import com.flockyou.data.NetworkSettings
import com.flockyou.data.NetworkSettingsRepository
import com.flockyou.data.OuiSettings
import com.flockyou.data.OuiSettingsRepository
import com.flockyou.data.PrivacySettings
import com.flockyou.data.PrivacySettingsRepository
import com.flockyou.data.model.*
import com.flockyou.data.repository.EphemeralDetectionRepository
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.network.OrbotHelper
import com.flockyou.network.TorAwareHttpClient
import com.flockyou.network.TorConnectionStatus
import com.flockyou.scanner.flipper.FlipperClient
import com.flockyou.scanner.flipper.FlipperConnectionState
import com.flockyou.scanner.flipper.FlipperOnboardingRepository
import com.flockyou.scanner.flipper.FlipperOnboardingSettings
import com.flockyou.scanner.flipper.FlipperScannerManager
import com.flockyou.scanner.flipper.FlipperSettings
import com.flockyou.scanner.flipper.FlipperSettingsRepository
import com.flockyou.scanner.flipper.FlipperStatusResponse
import com.flockyou.service.CellularMonitor
import com.flockyou.service.ScanningService
import com.flockyou.service.ScanningServiceConnection
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import dagger.Lazy
import javax.inject.Inject

/**
 * View mode for detection list display
 */
enum class DetectionViewMode(val label: String) {
    TIMELINE("Timeline"),
    INCIDENTS("Incidents")
}

/**
 * Sort order options for detection history list
 */
enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    THREAT_SCORE_DESC("Highest Threat"),
    SIGNAL_STRENGTH_DESC("Strongest Signal"),
    SEEN_COUNT_DESC("Most Seen")
}

/**
 * Time range presets for filtering detections
 */
enum class TimeRange(val label: String, val durationMs: Long?) {
    LAST_HOUR("Last hour", 3_600_000L),
    LAST_24H("Last 24h", 86_400_000L),
    LAST_7D("Last 7 days", 604_800_000L),
    LAST_30D("Last 30 days", 2_592_000_000L),
    ALL_TIME("All time", null),
    CUSTOM("Custom", null)
}

data class MainUiState(
    val isScanning: Boolean = false,
    val isLoading: Boolean = true,
    val detections: List<Detection> = emptyList(),
    val totalCount: Int = 0,
    val highThreatCount: Int = 0,
    val lastDetection: Detection? = null,
    val selectedTab: Int = 0,
    val filterThreatLevel: ThreatLevel? = null,
    val filterDeviceTypes: Set<DeviceType> = emptySet(), // Multiple device types now
    val filterMatchAll: Boolean = true, // true = AND, false = OR
    val hideFalsePositives: Boolean = true, // Hide detections flagged as FP by default
    val fpFilterThreshold: Float = 0.6f, // FP score threshold for filtering (MEDIUM_CONFIDENCE)
    // New unified filters
    val filterProtocols: Set<DetectionProtocol> = emptySet(), // Empty = all protocols
    val filterTimeRange: TimeRange = TimeRange.ALL_TIME,
    val filterCustomStartTime: Long? = null,
    val filterCustomEndTime: Long? = null,
    val filterSignalStrength: Set<SignalStrength> = emptySet(), // Empty = all signal strengths
    val filterActiveOnly: Boolean = false,
    // Sort and search
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val searchQuery: String = "",
    // Status information
    val scanStatus: com.flockyou.service.ScanStatus = com.flockyou.service.ScanStatus.Idle,
    val bleStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    val wifiStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    val locationStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    val cellularStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    val satelliteStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    val recentErrors: List<com.flockyou.service.ScanError> = emptyList(),
    // Seen devices (from IPC)
    val seenBleDevices: List<com.flockyou.service.SeenDevice> = emptyList(),
    val seenWifiNetworks: List<com.flockyou.service.SeenDevice> = emptyList(),
    // Cellular monitoring
    val cellStatus: CellularMonitor.CellStatus? = null,
    val cellularAnomalies: List<CellularMonitor.CellularAnomaly> = emptyList(),
    val seenCellTowers: List<CellularMonitor.SeenCellTower> = emptyList(),
    val cellularEvents: List<CellularMonitor.CellularEvent> = emptyList(),
    // Satellite monitoring
    val satelliteState: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState? = null,
    val satelliteAnomalies: List<com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly> = emptyList(),
    val satelliteHistory: List<com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionEvent> = emptyList(),
    // Rogue WiFi monitoring
    val rogueWifiStatus: com.flockyou.service.RogueWifiMonitor.WifiEnvironmentStatus? = null,
    val rogueWifiAnomalies: List<com.flockyou.service.RogueWifiMonitor.WifiAnomaly> = emptyList(),
    val suspiciousNetworks: List<com.flockyou.service.RogueWifiMonitor.SuspiciousNetwork> = emptyList(),
    // RF signal analysis
    val rfStatus: com.flockyou.service.RfSignalAnalyzer.RfEnvironmentStatus? = null,
    val rfAnomalies: List<com.flockyou.service.RfSignalAnalyzer.RfAnomaly> = emptyList(),
    val detectedDrones: List<com.flockyou.service.RfSignalAnalyzer.DroneInfo> = emptyList(),
    // Ultrasonic detection
    val ultrasonicStatus: com.flockyou.service.UltrasonicDetector.UltrasonicStatus? = null,
    val ultrasonicAnomalies: List<com.flockyou.service.UltrasonicDetector.UltrasonicAnomaly> = emptyList(),
    val ultrasonicBeacons: List<com.flockyou.service.UltrasonicDetector.BeaconDetection> = emptyList(),
    // GNSS satellite monitoring
    val gnssStatus: com.flockyou.monitoring.GnssSatelliteMonitor.GnssEnvironmentStatus? = null,
    val gnssSatellites: List<com.flockyou.monitoring.GnssSatelliteMonitor.SatelliteInfo> = emptyList(),
    val gnssAnomalies: List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomaly> = emptyList(),
    val gnssEvents: List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssEvent> = emptyList(),
    val gnssMeasurements: com.flockyou.monitoring.GnssSatelliteMonitor.GnssMeasurementData? = null,
    // Detector health status
    val detectorHealth: Map<String, com.flockyou.service.DetectorHealthStatus> = emptyMap(),
    // Scan statistics
    val scanStats: com.flockyou.service.ScanStatistics = com.flockyou.service.ScanStatistics(),
    // Threading monitor
    val threadingSystemState: com.flockyou.monitoring.ScannerThreadingMonitor.SystemThreadingState? = null,
    val threadingScannerStates: Map<String, com.flockyou.monitoring.ScannerThreadingMonitor.ScannerThreadState> = emptyMap(),
    val threadingAlerts: List<com.flockyou.monitoring.ScannerThreadingMonitor.ThreadingAlert> = emptyList(),
    // Cross-detection pattern alerts
    val correlatedThreats: List<com.flockyou.ai.correlation.CorrelatedThreat> = emptyList(),
    val dismissedCorrelationIds: Set<String> = emptySet(),
    // Related detection counts (detectionId -> count of related)
    val relatedDetectionCounts: Map<String, Int> = emptyMap(),
    // Selection mode for batch actions
    val selectionMode: Boolean = false,
    val selectedDetectionIds: Set<String> = emptySet(),
    // View mode
    val viewMode: DetectionViewMode = DetectionViewMode.TIMELINE,
    // UI settings
    val advancedMode: Boolean = false,
    // AI Analysis
    val analyzingDetectionId: String? = null,
    val analysisResult: com.flockyou.data.AiAnalysisResult? = null,
    val isAiAnalysisAvailable: Boolean = false,
    // Flipper Zero
    val flipperConnectionState: FlipperConnectionState = FlipperConnectionState.DISCONNECTED,
    val flipperConnectionType: FlipperClient.ConnectionType = FlipperClient.ConnectionType.NONE,
    val flipperStatus: FlipperStatusResponse? = null,
    val flipperIsScanning: Boolean = false,
    val flipperDetectionCount: Int = 0,
    val flipperWipsAlertCount: Int = 0,
    val flipperLastError: String? = null,
    val flipperScanSchedulerStatus: com.flockyou.scanner.flipper.ScanSchedulerStatus = com.flockyou.scanner.flipper.ScanSchedulerStatus(),
    // Flipper UX improvements
    val flipperAutoReconnectState: com.flockyou.scanner.flipper.AutoReconnectState = com.flockyou.scanner.flipper.AutoReconnectState(),
    val flipperDiscoveredDevices: List<com.flockyou.scanner.flipper.DiscoveredFlipperDevice> = emptyList(),
    val flipperRecentDevices: List<com.flockyou.scanner.flipper.RecentFlipperDevice> = emptyList(),
    val flipperIsScanningForDevices: Boolean = false,
    val flipperConnectionRssi: Int? = null,
    val flipperShowDevicePicker: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val application: Application,
    internal val repository: DetectionRepository,
    internal val ephemeralRepository: EphemeralDetectionRepository,
    internal val settingsRepository: DetectionSettingsRepository,
    internal val ouiSettingsRepository: OuiSettingsRepository,
    internal val networkSettingsRepository: NetworkSettingsRepository,
    internal val broadcastSettingsRepository: BroadcastSettingsRepository,
    internal val privacySettingsRepository: PrivacySettingsRepository,
    internal val orbotHelper: OrbotHelper,
    internal val torAwareHttpClient: TorAwareHttpClient,
    internal val workManager: WorkManager,
    internal val detectionAnalyzerLazy: Lazy<com.flockyou.ai.DetectionAnalyzer>,
    internal val crossDomainAnalyzer: com.flockyou.ai.correlation.CrossDomainAnalyzer,
    internal val serviceConnection: ScanningServiceConnection,  // Injected singleton
    internal val flipperScannerManager: FlipperScannerManager,  // Flipper Zero integration
    internal val flipperUiSettingsRepository: FlipperUiSettingsRepository,  // Flipper UI settings
    internal val flipperOnboardingRepository: FlipperOnboardingRepository,  // Flipper onboarding
    internal val flipperSettingsRepository: FlipperSettingsRepository,  // Flipper settings (scan modules, WIPS, etc.)
    internal val enrichedDataCache: com.flockyou.ai.EnrichedDataCache  // Enriched detector data cache
) : AndroidViewModel(application) {

    internal val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Keep existing extension/call sites source-compatible while deferring the
    // heavyweight AI graph until an AI feature actually asks for it.
    internal val detectionAnalyzer: com.flockyou.ai.DetectionAnalyzer
        get() = detectionAnalyzerLazy.get()

    // Privilege mode for feasibility-aware UI
    val privilegeMode: com.flockyou.privilege.PrivilegeMode =
        com.flockyou.privilege.PrivilegeModeDetector.detect(application)

    // Expose the service connection bound state for debugging
    val serviceConnectionBound: StateFlow<Boolean> = serviceConnection.isBound

    // OUI Settings
    val ouiSettings: StateFlow<OuiSettings> = ouiSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OuiSettings()
        )

    internal val _isOuiUpdating = MutableStateFlow(false)
    val isOuiUpdating: StateFlow<Boolean> = _isOuiUpdating.asStateFlow()

    // Network Settings
    val networkSettings: StateFlow<NetworkSettings> = networkSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetworkSettings()
        )

    // Broadcast Settings
    val broadcastSettings: StateFlow<BroadcastSettings> = broadcastSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BroadcastSettings()
        )

    // Privacy Settings
    val privacySettings: StateFlow<PrivacySettings> = privacySettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PrivacySettings()
        )

    // Detection Settings (patterns, thresholds, presets)
    val detectionSettings: StateFlow<com.flockyou.data.DetectionSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.flockyou.data.DetectionSettings()
        )

    // Flipper UI Settings
    val flipperUiSettings: StateFlow<FlipperUiSettings> = flipperUiSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlipperUiSettings()
        )

    // Flipper Onboarding Settings
    val flipperOnboardingSettings: StateFlow<FlipperOnboardingSettings> = flipperOnboardingRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlipperOnboardingSettings()
        )

    // Flipper Settings (scan modules, WIPS, connection preferences)
    val flipperSettings: StateFlow<FlipperSettings> = flipperSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FlipperSettings()
        )

    // Flipper FAP installation state
    internal val _flipperIsInstalling = MutableStateFlow(false)
    val flipperIsInstalling: StateFlow<Boolean> = _flipperIsInstalling.asStateFlow()

    internal val _flipperInstallProgress = MutableStateFlow<String?>(null)
    val flipperInstallProgress: StateFlow<String?> = _flipperInstallProgress.asStateFlow()

    // Flipper Sub-GHz scan status from FlipperScannerManager
    val flipperSubGhzScanStatus = flipperScannerManager.subGhzScanStatus

    // Flipper Setup Wizard state
    internal val _showFlipperSetupWizard = MutableStateFlow(false)
    val showFlipperSetupWizard: StateFlow<Boolean> = _showFlipperSetupWizard.asStateFlow()

    private val _isOrbotInstalled = MutableStateFlow(false)
    val isOrbotInstalled: StateFlow<Boolean> = _isOrbotInstalled.asStateFlow()

    private val _isOrbotRunning = MutableStateFlow(false)
    val isOrbotRunning: StateFlow<Boolean> = _isOrbotRunning.asStateFlow()

    internal val _torConnectionStatus = MutableStateFlow<TorConnectionStatus?>(null)
    val torConnectionStatus: StateFlow<TorConnectionStatus?> = _torConnectionStatus.asStateFlow()

    internal val _isTorTesting = MutableStateFlow(false)
    val isTorTesting: StateFlow<Boolean> = _isTorTesting.asStateFlow()

    // Related detections for the currently selected detection
    private val _relatedDetections = MutableStateFlow<List<Detection>>(emptyList())
    val relatedDetections: StateFlow<List<Detection>> = _relatedDetections.asStateFlow()

    // Currently selected detection (for tracking which detection's related items are shown)
    private val _selectedDetectionId = MutableStateFlow<String?>(null)

    // Job for loading related detections (to cancel previous loads)
    private var loadRelatedJob: Job? = null

    // Track detection IDs that have been prioritized for enrichment
    internal val _prioritizedEnrichmentIds = MutableStateFlow<Set<String>>(emptySet())
    val prioritizedEnrichmentIds: StateFlow<Set<String>> = _prioritizedEnrichmentIds.asStateFlow()

    init {
        // The serviceConnection is now a singleton injected via Hilt
        // It is automatically bound when created by the provider

        // Consolidated IPC state collection - combines all service connection flows into a single collection
        // This reduces context switching overhead and makes state updates more atomic
        viewModelScope.launch {
            combine(
                serviceConnection.isScanning,
                serviceConnection.scanStatus,
                serviceConnection.bleStatus,
                serviceConnection.wifiStatus,
                serviceConnection.locationStatus,
                serviceConnection.cellularStatus,
                serviceConnection.satelliteStatus,
                serviceConnection.lastDetection
            ) { values ->
                IpcStateUpdate(
                    isScanning = values[0] as Boolean,
                    scanStatus = values[1] as String,
                    bleStatus = values[2] as String,
                    wifiStatus = values[3] as String,
                    locationStatus = values[4] as String,
                    cellularStatus = values[5] as String,
                    satelliteStatus = values[6] as String,
                    lastDetection = values[7] as? Detection
                )
            }.collect { update ->
                _uiState.update {
                    it.copy(
                        isScanning = update.isScanning,
                        scanStatus = com.flockyou.service.ScanStatus.fromIpcString(update.scanStatus),
                        bleStatus = com.flockyou.service.SubsystemStatus.fromIpcString(update.bleStatus),
                        wifiStatus = com.flockyou.service.SubsystemStatus.fromIpcString(update.wifiStatus),
                        locationStatus = com.flockyou.service.SubsystemStatus.fromIpcString(update.locationStatus),
                        cellularStatus = com.flockyou.service.SubsystemStatus.fromIpcString(update.cellularStatus),
                        satelliteStatus = com.flockyou.service.SubsystemStatus.fromIpcString(update.satelliteStatus),
                        lastDetection = update.lastDetection
                    )
                }
            }
        }

        // Consolidated device/network data collection
        viewModelScope.launch {
            combine(
                serviceConnection.seenBleDevices,
                serviceConnection.seenWifiNetworks
            ) { bleDevices, wifiNetworks ->
                Pair(bleDevices, wifiNetworks)
            }.collect { (bleDevices, wifiNetworks) ->
                _uiState.update {
                    it.copy(
                        seenBleDevices = bleDevices,
                        seenWifiNetworks = wifiNetworks
                    )
                }
            }
        }

        // Consolidated cellular data collection
        viewModelScope.launch {
            combine(
                serviceConnection.cellStatus,
                serviceConnection.seenCellTowers,
                serviceConnection.cellularAnomalies,
                serviceConnection.cellularEvents
            ) { cellStatus, towers, anomalies, events ->
                CellularDataUpdate(cellStatus, towers, anomalies, events)
            }.collect { update ->
                _uiState.update {
                    it.copy(
                        cellStatus = update.cellStatus,
                        seenCellTowers = update.seenCellTowers,
                        cellularAnomalies = update.cellularAnomalies,
                        cellularEvents = update.cellularEvents
                    )
                }
            }
        }

        // Consolidated satellite data collection
        viewModelScope.launch {
            combine(
                serviceConnection.satelliteState,
                serviceConnection.satelliteAnomalies,
                serviceConnection.satelliteHistory
            ) { state, anomalies, history ->
                Triple(state, anomalies, history)
            }.collect { (state, anomalies, history) ->
                _uiState.update {
                    it.copy(
                        satelliteState = state,
                        satelliteAnomalies = anomalies,
                        satelliteHistory = history
                    )
                }
            }
        }

        // Consolidated rogue WiFi data collection
        viewModelScope.launch {
            combine(
                serviceConnection.rogueWifiStatus,
                serviceConnection.rogueWifiAnomalies,
                serviceConnection.suspiciousNetworks
            ) { status, anomalies, suspicious ->
                Triple(status, anomalies, suspicious)
            }.collect { (status, anomalies, suspicious) ->
                _uiState.update {
                    it.copy(
                        rogueWifiStatus = status,
                        rogueWifiAnomalies = anomalies,
                        suspiciousNetworks = suspicious
                    )
                }
            }
        }

        // Consolidated RF data collection
        viewModelScope.launch {
            combine(
                serviceConnection.rfStatus,
                serviceConnection.rfAnomalies,
                serviceConnection.detectedDrones
            ) { status, anomalies, drones ->
                Triple(status, anomalies, drones)
            }.collect { (status, anomalies, drones) ->
                _uiState.update {
                    it.copy(
                        rfStatus = status,
                        rfAnomalies = anomalies,
                        detectedDrones = drones
                    )
                }
            }
        }

        // Consolidated ultrasonic data collection
        viewModelScope.launch {
            combine(
                serviceConnection.ultrasonicStatus,
                serviceConnection.ultrasonicAnomalies,
                serviceConnection.ultrasonicBeacons
            ) { status, anomalies, beacons ->
                Triple(status, anomalies, beacons)
            }.collect { (status, anomalies, beacons) ->
                _uiState.update {
                    it.copy(
                        ultrasonicStatus = status,
                        ultrasonicAnomalies = anomalies,
                        ultrasonicBeacons = beacons
                    )
                }
            }
        }

        // Consolidated GNSS satellite data collection
        viewModelScope.launch {
            combine(
                serviceConnection.gnssStatus,
                serviceConnection.gnssSatellites,
                serviceConnection.gnssAnomalies,
                serviceConnection.gnssEvents,
                serviceConnection.gnssMeasurements
            ) { status, satellites, anomalies, events, measurements ->
                GnssDataUpdate(status, satellites, anomalies, events, measurements)
            }.collect { update ->
                _uiState.update {
                    it.copy(
                        gnssStatus = update.status,
                        gnssSatellites = update.satellites,
                        gnssAnomalies = update.anomalies,
                        gnssEvents = update.events,
                        gnssMeasurements = update.measurements
                    )
                }
            }
        }

        // Detector health status collection
        viewModelScope.launch {
            serviceConnection.detectorHealth.collect { health ->
                Log.d("MainViewModel", "Received detector health update: ${health.size} detectors, running=${health.values.count { it.isRunning }}")
                _uiState.update { it.copy(detectorHealth = health) }
            }
        }

        // Scan statistics collection
        viewModelScope.launch {
            serviceConnection.scanStats.collect { stats ->
                Log.d("MainViewModel", "Received scan stats update: totalBleScans=${stats.totalBleScans}, totalWifiScans=${stats.totalWifiScans}")
                _uiState.update { it.copy(scanStats = stats) }
            }
        }

        // Threading monitor data collection
        viewModelScope.launch {
            serviceConnection.threadingSystemState.collect { state ->
                _uiState.update { it.copy(threadingSystemState = state) }
            }
        }

        viewModelScope.launch {
            serviceConnection.threadingScannerStates.collect { states ->
                _uiState.update { it.copy(threadingScannerStates = states) }
            }
        }

        viewModelScope.launch {
            serviceConnection.threadingAlerts.collect { alerts ->
                _uiState.update { it.copy(threadingAlerts = alerts) }
            }
        }

        // Database state collection - consolidated
        viewModelScope.launch {
            combine(
                repository.allDetections,
                repository.totalDetectionCount,
                repository.highThreatCount
            ) { detections, totalCount, highThreatCount ->
                Triple(detections, totalCount, highThreatCount)
            }.collect { (detections, totalCount, highThreatCount) ->
                _uiState.update {
                    it.copy(
                        detections = detections,
                        totalCount = totalCount,
                        highThreatCount = highThreatCount,
                        isLoading = false
                    )
                }
            }
        }

        // Observe detection refresh events from service via IPC (cross-process notification)
        viewModelScope.launch {
            serviceConnection.detectionRefreshEvent.collect {
                refreshDetections()
            }
        }

        // Observe cross-domain correlated threats
        viewModelScope.launch {
            crossDomainAnalyzer.correlatedThreats.collect { threats ->
                _uiState.update { it.copy(correlatedThreats = threats) }
            }
        }

        // Compute related detection counts when detections change using the presentation-layer
        // indexed counter. This preserves the previous semantics without an O(n^2) nested scan
        // on every Room emission.
        viewModelScope.launch {
            repository.allDetections.collect { detections ->
                val counts = RelatedDetectionCounter.compute(detections)
                _uiState.update { it.copy(relatedDetectionCounts = counts) }
            }
        }

        // Check Orbot status periodically
        viewModelScope.launch {
            while (true) {
                try {
                    _isOrbotInstalled.value = orbotHelper.isOrbotInstalled()
                    if (_isOrbotInstalled.value) {
                        val settings = networkSettings.value
                        _isOrbotRunning.value = orbotHelper.isOrbotRunning(
                            settings.torProxyHost,
                            settings.torProxyPort
                        )
                    } else {
                        _isOrbotRunning.value = false
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error checking Orbot status", e)
                }
                delay(5000) // Check every 5 seconds
            }
        }

        // Observe advanced mode setting
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(advancedMode = settings.advancedMode) }
            }
        }

        // Sync ephemeral mode to FlipperScannerManager when privacy settings change
        viewModelScope.launch {
            privacySettingsRepository.settings.collect { settings ->
                flipperScannerManager.setEphemeralMode(settings.ephemeralModeEnabled)
            }
        }

        // Observe error log from service via IPC
        viewModelScope.launch {
            serviceConnection.errorLog.collect { errors ->
                _uiState.update { it.copy(recentErrors = errors) }
            }
        }

        // Observe Flipper Zero state
        viewModelScope.launch {
            combine(
                flipperScannerManager.connectionState,
                flipperScannerManager.connectionType,
                flipperScannerManager.flipperStatus,
                flipperScannerManager.isRunning,
                flipperScannerManager.detectionCount,
                flipperScannerManager.wipsAlertCount,
                flipperScannerManager.lastError
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                FlipperStateUpdate(
                    connectionState = values[0] as FlipperConnectionState,
                    connectionType = values[1] as FlipperClient.ConnectionType,
                    status = values[2] as? FlipperStatusResponse,
                    isScanning = values[3] as Boolean,
                    detectionCount = values[4] as Int,
                    wipsAlertCount = values[5] as Int,
                    lastError = values[6] as? String
                )
            }.collect { update ->
                if (update.status != null) {
                    Log.d("MainViewModel", "Flipper status received in ViewModel: battery=${update.status.batteryPercent}%")
                }
                _uiState.update {
                    it.copy(
                        flipperConnectionState = update.connectionState,
                        flipperConnectionType = update.connectionType,
                        flipperStatus = update.status,
                        flipperIsScanning = update.isScanning,
                        flipperDetectionCount = update.detectionCount,
                        flipperWipsAlertCount = update.wipsAlertCount,
                        flipperLastError = update.lastError
                    )
                }
            }
        }

        // Observe Flipper scan scheduler status
        viewModelScope.launch {
            flipperScannerManager.scanSchedulerStatus.collect { status ->
                _uiState.update { it.copy(flipperScanSchedulerStatus = status) }
            }
        }

        // Observe Flipper auto-reconnect state
        viewModelScope.launch {
            flipperScannerManager.autoReconnectState.collect { state ->
                _uiState.update { it.copy(flipperAutoReconnectState = state) }
            }
        }

        // Observe Flipper discovered devices (Bluetooth scanning)
        viewModelScope.launch {
            flipperScannerManager.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(flipperDiscoveredDevices = devices) }
            }
        }

        // Observe Flipper device scanning state
        viewModelScope.launch {
            flipperScannerManager.isScanningForDevices.collect { isScanning ->
                _uiState.update { it.copy(flipperIsScanningForDevices = isScanning) }
            }
        }

        // Observe Flipper recent devices from history
        viewModelScope.launch {
            flipperScannerManager.recentDevices.collect { devices ->
                _uiState.update { it.copy(flipperRecentDevices = devices) }
            }
        }

        // Observe Flipper connection RSSI
        viewModelScope.launch {
            flipperScannerManager.connectionRssi.collect { rssi ->
                _uiState.update { it.copy(flipperConnectionRssi = rssi) }
            }
        }

        // Request the current state after all collectors are set up
        // This ensures state updates will be properly received
        serviceConnection.requestState()
    }

    // Data classes for consolidated state updates
    private data class IpcStateUpdate(
        val isScanning: Boolean,
        val scanStatus: String,
        val bleStatus: String,
        val wifiStatus: String,
        val locationStatus: String,
        val cellularStatus: String,
        val satelliteStatus: String,
        val lastDetection: Detection?
    )

    private data class CellularDataUpdate(
        val cellStatus: CellularMonitor.CellStatus?,
        val seenCellTowers: List<CellularMonitor.SeenCellTower>,
        val cellularAnomalies: List<CellularMonitor.CellularAnomaly>,
        val cellularEvents: List<CellularMonitor.CellularEvent>
    )

    private data class GnssDataUpdate(
        val status: com.flockyou.monitoring.GnssSatelliteMonitor.GnssEnvironmentStatus?,
        val satellites: List<com.flockyou.monitoring.GnssSatelliteMonitor.SatelliteInfo>,
        val anomalies: List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomaly>,
        val events: List<com.flockyou.monitoring.GnssSatelliteMonitor.GnssEvent>,
        val measurements: com.flockyou.monitoring.GnssSatelliteMonitor.GnssMeasurementData?
    )

    private data class FlipperStateUpdate(
        val connectionState: FlipperConnectionState,
        val connectionType: FlipperClient.ConnectionType,
        val status: FlipperStatusResponse?,
        val isScanning: Boolean,
        val detectionCount: Int,
        val wipsAlertCount: Int,
        val lastError: String?
    )

    companion object {
        internal const val FAP_ASSET_PATH = "flipper/flock_bridge.fap"
        internal const val FAP_DEST_PATH = "/ext/apps/Tools/flock_bridge.fap"
    }

    // --- Cross-detection pattern alerts ---

    fun dismissCorrelation(correlationId: String) {
        _uiState.update {
            it.copy(dismissedCorrelationIds = it.dismissedCorrelationIds + correlationId)
        }
    }

    fun getVisibleCorrelatedThreats(): List<com.flockyou.ai.correlation.CorrelatedThreat> {
        val state = _uiState.value
        return state.correlatedThreats.filter { it.id !in state.dismissedCorrelationIds }
    }

    // --- View mode ---

    fun setViewMode(mode: DetectionViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    // --- Batch/selection actions ---

    fun toggleSelectionMode() {
        _uiState.update {
            if (it.selectionMode) {
                // Exiting selection mode - clear selections
                it.copy(selectionMode = false, selectedDetectionIds = emptySet())
            } else {
                it.copy(selectionMode = true)
            }
        }
    }

    fun toggleDetectionSelection(detectionId: String) {
        _uiState.update { state ->
            val newSelection = if (detectionId in state.selectedDetectionIds) {
                state.selectedDetectionIds - detectionId
            } else {
                state.selectedDetectionIds + detectionId
            }
            state.copy(selectedDetectionIds = newSelection)
        }
    }

    fun selectAllDetections(detectionIds: List<String>) {
        _uiState.update { it.copy(selectedDetectionIds = detectionIds.toSet()) }
    }

    fun batchMarkReviewed() {
        viewModelScope.launch {
            val detections = _uiState.value.detections.filter {
                it.id in _uiState.value.selectedDetectionIds
            }
            detections.forEach { detection ->
                val updated = detection.copy(
                    fpCategory = "USER_REVIEWED",
                    fpReason = "Manually reviewed and dismissed by user",
                    analyzedAt = System.currentTimeMillis()
                )
                repository.updateDetection(updated)
            }
            _uiState.update { it.copy(selectionMode = false, selectedDetectionIds = emptySet()) }
        }
    }

    fun batchMarkFalsePositive() {
        viewModelScope.launch {
            val detections = _uiState.value.detections.filter {
                it.id in _uiState.value.selectedDetectionIds
            }
            detections.forEach { detection ->
                val updated = detection.copy(
                    fpScore = 0.9f,
                    fpCategory = "USER_MARKED_FP",
                    fpReason = "Manually marked as false positive by user",
                    analyzedAt = System.currentTimeMillis()
                )
                repository.updateDetection(updated)
            }
            _uiState.update { it.copy(selectionMode = false, selectedDetectionIds = emptySet()) }
        }
    }

    // --- Enriched data access ---

    /**
     * Retrieve enriched detector data for a detection, if available in cache.
     */
    fun getEnrichedData(detectionId: String): com.flockyou.ai.EnrichedDetectorData? {
        return enrichedDataCache.get(detectionId)
    }

    override fun onCleared() {
        super.onCleared()
        // The serviceConnection is a singleton - do not unbind here
        // It will remain bound for the lifetime of the application
    }

    fun startScanning() {
        // Optimistically update UI state immediately for responsive feedback
        // The actual IPC state sync will confirm or correct this
        _uiState.update { it.copy(isScanning = true, scanStatus = com.flockyou.service.ScanStatus.Starting) }

        // First, start the service (required for foreground service)
        val intent = Intent(application, ScanningService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
        }
        // Also send IPC command (service will receive both, but IPC ensures state sync)
        serviceConnection.startScanning()

        // Request state after a short delay to ensure we get the actual service state
        // This handles the race condition where IPC client isn't registered yet
        viewModelScope.launch {
            delay(500)
            serviceConnection.requestState()
        }
    }

    fun stopScanning() {
        // Optimistically update UI state immediately for responsive feedback
        _uiState.update { it.copy(isScanning = false, scanStatus = com.flockyou.service.ScanStatus.Idle) }

        // Send IPC command to stop scanning
        serviceConnection.stopScanning()
        // Also stop the service
        val intent = Intent(application, ScanningService::class.java)
        application.stopService(intent)
    }

    fun toggleScanning() {
        if (_uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun deleteDetection(detection: Detection) {
        viewModelScope.launch {
            repository.deleteDetection(detection)
        }
    }

    /**
     * Mark a detection as reviewed/dismissed.
     * This updates the detection to have a lower threat score indicating it was manually reviewed.
     */
    fun markAsReviewed(detection: Detection) {
        viewModelScope.launch {
            // Mark as reviewed - preserve existing fpScore if set, don't artificially increase it
            // "Reviewed" means user looked at it and dismissed, NOT that it's a false positive
            val updatedDetection = detection.copy(
                fpScore = detection.fpScore, // Keep existing score - don't change FP likelihood
                fpCategory = "USER_REVIEWED",
                fpReason = "Manually reviewed and dismissed by user",
                analyzedAt = System.currentTimeMillis()
            )
            repository.updateDetection(updatedDetection)
        }
    }

    /**
     * Mark a detection as a false positive.
     * This sets a high FP score so it will be filtered out by default.
     */
    fun markAsFalsePositive(detection: Detection) {
        viewModelScope.launch {
            val updatedDetection = detection.copy(
                fpScore = 0.9f, // High FP score - user confirmed false positive
                fpCategory = "USER_MARKED_FP",
                fpReason = "Manually marked as false positive by user",
                analyzedAt = System.currentTimeMillis()
            )
            repository.updateDetection(updatedDetection)
        }
    }

    /**
     * Undo marking a detection (reset FP fields if user-marked).
     */
    fun undoMarkDetection(detection: Detection) {
        viewModelScope.launch {
            // Only reset if it was user-marked
            if (detection.fpCategory == "USER_REVIEWED" || detection.fpCategory == "USER_MARKED_FP") {
                val updatedDetection = detection.copy(
                    fpScore = null,
                    fpCategory = null,
                    fpReason = null,
                    analyzedAt = null
                )
                repository.updateDetection(updatedDetection)
            }
        }
    }

    /**
     * Mark a detection as a confirmed threat.
     * This clears any false positive marking and sets the confirmedThreat flag.
     */
    fun markAsConfirmedThreat(detection: Detection) {
        viewModelScope.launch {
            val updatedDetection = detection.copy(
                confirmedThreat = true,
                fpScore = 0.0f, // Low FP score - user confirmed it's a real threat
                fpCategory = "USER_CONFIRMED_THREAT",
                fpReason = "Manually confirmed as threat by user",
                analyzedAt = System.currentTimeMillis()
            )
            repository.updateDetection(updatedDetection)
        }
    }

    /**
     * Update or add a user note to a detection.
     * @param detection The detection to update
     * @param note The note text (null to clear the note)
     */
    fun updateUserNote(detection: Detection, note: String?) {
        viewModelScope.launch {
            val updatedDetection = detection.copy(
                userNote = note?.takeIf { it.isNotBlank() }
            )
            repository.updateDetection(updatedDetection)
        }
    }

    fun clearAllDetections() {
        viewModelScope.launch {
            repository.deleteAllDetections()
        }
    }

    fun clearErrors() {
        serviceConnection.clearErrors()
    }

    fun updateScanSettings(
        wifiIntervalSeconds: Int,
        bleDurationSeconds: Int,
        enableBle: Boolean,
        enableWifi: Boolean,
        enableCellular: Boolean,
        trackSeenDevices: Boolean
    ) {
        serviceConnection.updateScanSettings(
            wifiIntervalSeconds = wifiIntervalSeconds,
            bleDurationSeconds = bleDurationSeconds,
            enableBle = enableBle,
            enableWifi = enableWifi,
            enableCellular = enableCellular,
            trackSeenDevices = trackSeenDevices
        )
    }

    /**
     * Request refresh of all service data via IPC.
     * This triggers the service to send all current state to the UI.
     */
    fun requestRefresh() {
        serviceConnection.requestState()
        viewModelScope.launch {
            refreshDetections()
        }
    }

    /**
     * Manually refresh detections from database.
     * Called when detection refresh events are received to ensure UI stays in sync
     * even if Room's Flow emissions don't trigger properly with SQLCipher.
     */
    private suspend fun refreshDetections() {
        try {
            val detections = repository.getAllDetectionsSnapshot()
            val totalCount = repository.getTotalDetectionCount()
            _uiState.update {
                it.copy(
                    detections = detections,
                    totalCount = totalCount
                )
            }
        } catch (e: Exception) {
            // Log error but don't crash - Room Flow should still work as backup
            android.util.Log.e("MainViewModel", "Error refreshing detections: ${e.message}", e)
        }
    }

    /**
     * Load related detections for a given detection.
     * Cancels any previous load operation and queries for related items.
     * Results are stored in the relatedDetections StateFlow.
     *
     * @param detection The detection to find related items for
     */
    fun loadRelatedDetections(detection: Detection) {
        // Skip if we're already showing related for this detection
        if (_selectedDetectionId.value == detection.id) {
            return
        }

        // Cancel previous load job
        loadRelatedJob?.cancel()
        _selectedDetectionId.value = detection.id

        loadRelatedJob = viewModelScope.launch {
            try {
                val related = repository.getRelatedDetections(detection, limit = 10)
                _relatedDetections.value = related
                Log.d("MainViewModel", "Loaded ${related.size} related detections for ${detection.id}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading related detections: ${e.message}", e)
                _relatedDetections.value = emptyList()
            }
        }
    }

    /**
     * Clear related detections when the detail sheet is dismissed.
     */
    fun clearRelatedDetections() {
        loadRelatedJob?.cancel()
        _selectedDetectionId.value = null
        _relatedDetections.value = emptyList()
    }

    /**
     * Called when a related detection is clicked.
     * Loads the related detections for the newly selected detection.
     *
     * @param detection The newly selected detection
     */
    fun onRelatedDetectionSelected(detection: Detection) {
        // Load related detections for the newly selected item
        loadRelatedDetections(detection)
    }

    internal fun getAppVersion(): String {
        return try {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
