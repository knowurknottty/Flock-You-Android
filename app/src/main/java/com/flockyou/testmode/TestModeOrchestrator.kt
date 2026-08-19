package com.flockyou.testmode

import android.content.Context
import android.util.Log
import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DetectionSource
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import com.flockyou.data.model.rssiToSignalStrength
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.scanner.CellularAnomaly
import com.flockyou.scanner.CellularAnomalyType
import com.flockyou.testmode.scanner.MockBleScanner
import com.flockyou.testmode.scanner.MockBleScanResult
import com.flockyou.testmode.scanner.MockCellularScanner
import com.flockyou.testmode.scanner.MockGnssScanner
import com.flockyou.testmode.scanner.GnssAnomaly
import com.flockyou.testmode.scanner.GnssAnomalyType
import com.flockyou.testmode.scanner.MockAudioScanner
import com.flockyou.testmode.scanner.UltrasonicDetection
import com.flockyou.testmode.scanner.MockWifiScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Test Mode Orchestrator for the Flock-You Android app.
 *
 * This service coordinates test mode operation by:
 * 1. Managing test mode state (enabled/disabled)
 * 2. Coordinating mock scanners based on active scenario
 * 3. Injecting test data into the detection pipeline
 * 4. Providing a clean API for UI to control test mode
 */
@Singleton
class TestModeOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scenarioProvider: TestScenarioProvider,
    private val detectionRepository: DetectionRepository
) {
    companion object {
        private const val TAG = "TestModeOrchestrator"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Test mode configuration state
    private val _config = MutableStateFlow(TestModeConfig())
    val config: StateFlow<TestModeConfig> = _config.asStateFlow()

    // Current test mode status
    private val _status = MutableStateFlow(TestModeStatus())
    val status: StateFlow<TestModeStatus> = _status.asStateFlow()

    // Mock scanners
    private val mockWifiScanner = MockWifiScanner()
    private val mockBleScanner = MockBleScanner()
    private val mockCellularScanner = MockCellularScanner()
    private val mockGnssScanner = MockGnssScanner()
    private val mockAudioScanner = MockAudioScanner()

    // Active scenario job
    private var scenarioJob: Job? = null

    // Current location for geo-tagging test detections
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    /**
     * Enable test mode with optional scenario.
     */
    fun enableTestMode(scenarioId: String? = null) {
        Log.i(TAG, "Enabling test mode${scenarioId?.let { " with scenario: $it" } ?: ""}")

        _config.update { it.copy(enabled = true, activeScenarioId = scenarioId) }
        _status.update { it.copy(isActive = true, startTime = System.currentTimeMillis()) }

        if (scenarioId != null) {
            startScenario(scenarioId)
        }
    }

    /**
     * Disable test mode and stop all mock scanners.
     */
    fun disableTestMode() {
        Log.i(TAG, "Disabling test mode")

        scenarioJob?.cancel()
        scenarioJob = null
        stopAllMockScanners()

        _config.update { it.copy(enabled = false, activeScenarioId = null) }
        _status.update { TestModeStatus() }
    }

    /**
     * Start a specific test scenario.
     */
    fun startScenario(scenarioId: String) {
        val scenario = TestScenario.fromId(scenarioId)
        if (scenario == null) {
            Log.w(TAG, "Unknown scenario ID: $scenarioId")
            return
        }

        Log.i(TAG, "Starting scenario: ${scenario.name}")

        val previousJob = scenarioJob
        scenarioJob = scope.launch {
            previousJob?.cancelAndJoin()
            _config.update { it.copy(activeScenarioId = scenarioId) }
            _status.update {
                it.copy(
                    activeScenarioId = scenarioId,
                    activeScenarioName = scenario.name,
                    detectionCount = 0
                )
            }

            // Get mock data for this scenario
            val scenarioData = scenarioProvider.getScenarioData(scenario)

            // Configure mock scanners with scenario data
            configureMockScanners(scenarioData)

            // Start mock scanners
            startMockScanners(scenarioData)

            // Collect and process mock data continuously
            collectAndProcessMockData()
        }
    }

    /**
     * Stop the current scenario.
     */
    fun stopScenario() {
        Log.i(TAG, "Stopping current scenario")

        scenarioJob?.cancel()
        scenarioJob = null
        stopAllMockScanners()

        _config.update { it.copy(activeScenarioId = null) }
        _status.update { it.copy(activeScenarioId = null, activeScenarioName = null) }
    }

    /**
     * Get list of available scenarios.
     */
    fun getAvailableScenarios(): List<TestScenario> = scenarioProvider.getAllScenarios()

    /**
     * Update test mode configuration.
     */
    fun updateConfig(newConfig: TestModeConfig) {
        _config.value = newConfig
        val latitude = newConfig.syntheticLatitude
        val longitude = newConfig.syntheticLongitude
        val syntheticLocation = SyntheticLocationPolicy.validated(latitude, longitude)
        if (syntheticLocation != null) {
            updateLocation(syntheticLocation.latitude, syntheticLocation.longitude)
        } else {
            currentLatitude = null
            currentLongitude = null
            if (latitude != null || longitude != null) {
                Log.w(TAG, "Rejected incomplete or out-of-range synthetic location")
            }
        }
        mockWifiScanner.setEmissionInterval(newConfig.dataEmissionIntervalMs)
        mockBleScanner.setEmissionInterval(newConfig.dataEmissionIntervalMs)
        mockCellularScanner.setEmissionInterval(newConfig.dataEmissionIntervalMs)
        mockGnssScanner.setEmissionInterval(newConfig.dataEmissionIntervalMs)
        mockAudioScanner.setEmissionInterval(newConfig.dataEmissionIntervalMs)
        mockWifiScanner.setSignalVariationEnabled(newConfig.simulateSignalVariation)
        mockBleScanner.setRssiVariationEnabled(newConfig.simulateSignalVariation)
        mockCellularScanner.setSignalVariation(newConfig.simulateSignalVariation)
        mockAudioScanner.setAmplitudeVariation(newConfig.simulateSignalVariation)
    }

    /**
     * Update location for test detections.
     */
    fun updateLocation(latitude: Double, longitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude
    }

    /**
     * Check if test mode is currently enabled.
     */
    fun isEnabled(): Boolean = _config.value.enabled

    /**
     * Check if a scenario is actively running.
     */
    fun isScenarioRunning(): Boolean = scenarioJob?.isActive == true

    /**
     * Get the current scenario ID if one is running.
     */
    fun getCurrentScenarioId(): String? = _status.value.activeScenarioId

    /**
     * Clean up resources.
     */
    fun destroy() {
        scope.cancel()
        disableTestMode()
    }

    // ==================== Private Methods ====================

    private fun configureMockScanners(data: TestScenarioData) {
        Log.d(TAG, "Configuring mock scanners")

        if (data.wifiNetworks.isNotEmpty()) {
            val mockNetworks = data.wifiNetworks.map { network ->
                com.flockyou.testmode.scanner.MockWifiNetwork(
                    ssid = network.ssid,
                    bssid = network.bssid,
                    rssi = network.rssi,
                    frequency = network.frequency,
                    capabilities = network.capabilities
                )
            }
            mockWifiScanner.setMockData(mockNetworks)
        }

        if (data.bleDevices.isNotEmpty()) {
            val mockDevices = data.bleDevices.map { device ->
                val manufacturerDataMap = if (device.manufacturerId != null && device.manufacturerData != null) {
                    mapOf(device.manufacturerId to device.manufacturerData)
                } else {
                    emptyMap()
                }
                com.flockyou.testmode.scanner.MockBleDevice(
                    name = device.name,
                    address = device.macAddress,
                    rssi = device.rssi,
                    manufacturerData = manufacturerDataMap,
                    serviceUuids = device.serviceUuids
                )
            }
            mockBleScanner.setMockData(mockDevices)
        }

        data.cellularState?.let { state ->
            mockCellularScanner.setMockData(state)
        }

        data.gnssState?.let { state ->
            mockGnssScanner.clearAnomalies()
            mockGnssScanner.setMockData(state)
        }

        if (data.audioBeacons.isNotEmpty()) {
            mockAudioScanner.setMockBeacons(data.audioBeacons)
        }
    }

    private fun startMockScanners(data: TestScenarioData) {
        Log.d(TAG, "Starting mock scanners")

        if (data.wifiNetworks.isNotEmpty()) {
            mockWifiScanner.start()
        }
        if (data.bleDevices.isNotEmpty()) {
            mockBleScanner.start()
        }
        if (data.cellularState != null) {
            mockCellularScanner.start()
        }
        if (data.gnssState != null) {
            mockGnssScanner.start()
        }
        if (data.audioBeacons.isNotEmpty()) {
            mockAudioScanner.start()
        }
    }

    private fun stopAllMockScanners() {
        Log.d(TAG, "Stopping all mock scanners")
        mockWifiScanner.stop()
        mockBleScanner.stop()
        mockCellularScanner.stop()
        mockGnssScanner.stop()
        mockAudioScanner.stop()
    }

    private suspend fun collectAndProcessMockData(): Nothing = coroutineScope {
        // Collectors are children of the active scenario session. Cancelling or replacing
        // the scenario therefore cancels the entire collector set before a new one starts.
        launch {
            mockWifiScanner.scanResults.collect { results ->
                try {
                    processWifiResults(results)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing WiFi results", e)
                }
            }
        }

        launch {
            mockBleScanner.mockResults.collect { result ->
                try {
                    processBleResult(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing BLE result", e)
                }
            }
        }

        launch {
            mockCellularScanner.anomalies.collect { anomalies ->
                try {
                    processCellularAnomalies(anomalies)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cellular anomalies", e)
                }
            }
        }

        launch {
            mockGnssScanner.anomalies.collect { anomalies ->
                try {
                    processGnssAnomalies(anomalies)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing GNSS anomalies", e)
                }
            }
        }

        launch {
            mockAudioScanner.detections.collect { detection ->
                try {
                    processUltrasonicDetection(detection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ultrasonic detection", e)
                }
            }
        }

        // Keep the scenario session alive until it is explicitly stopped or replaced.
        awaitCancellation()
    }

    private suspend fun processWifiResults(results: List<android.net.wifi.ScanResult>) {
        for (result in results) {
            val detection = Detection(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                protocol = DetectionProtocol.WIFI,
                detectionMethod = DetectionMethod.SSID_PATTERN,
                deviceType = com.flockyou.data.model.DeviceType.UNKNOWN_SURVEILLANCE,
                deviceName = result.SSID,
                macAddress = result.BSSID,
                ssid = result.SSID,
                rssi = result.level,
                signalStrength = rssiToSignalStrength(result.level),
                latitude = currentLatitude,
                longitude = currentLongitude,
                threatLevel = ThreatLevel.MEDIUM,
                threatScore = 50,
                manufacturer = null,
                firmwareVersion = null,
                serviceUuids = null,
                matchedPatterns = "[TEST_MODE]",
                rawData = null,
                isActive = true,
                seenCount = 1,
                lastSeenTimestamp = System.currentTimeMillis(),
                detectionSource = DetectionSource.NATIVE_WIFI,
                fpScore = null,
                fpReason = null,
                fpCategory = null,
                analyzedAt = null,
                llmAnalyzed = false
            )

            detectionRepository.upsertDetection(detection)
            incrementDetectionCount()
        }
    }

    private suspend fun processBleResult(result: MockBleScanResult) {
        val detection = Detection(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            protocol = DetectionProtocol.BLUETOOTH_LE,
            detectionMethod = DetectionMethod.BLE_SERVICE_UUID,
            deviceType = com.flockyou.data.model.DeviceType.UNKNOWN_SURVEILLANCE,
            deviceName = result.device.name,
            macAddress = result.device.address,
            ssid = null,
            rssi = result.rssi,
            signalStrength = rssiToSignalStrength(result.rssi),
            latitude = currentLatitude,
            longitude = currentLongitude,
            threatLevel = ThreatLevel.MEDIUM,
            threatScore = 50,
            manufacturer = null,
            firmwareVersion = null,
            serviceUuids = result.device.serviceUuids.joinToString(","),
            matchedPatterns = "[TEST_MODE]",
            rawData = null,
            isActive = true,
            seenCount = 1,
            lastSeenTimestamp = System.currentTimeMillis(),
            detectionSource = DetectionSource.NATIVE_BLE,
            fpScore = null,
            fpReason = null,
            fpCategory = null,
            analyzedAt = null,
            llmAnalyzed = false
        )

        detectionRepository.upsertDetection(detection)
        incrementDetectionCount()
    }

    private suspend fun processCellularAnomalies(anomalies: List<CellularAnomaly>) {
        for (anomaly in anomalies) {
            val detection = Detection(
                id = UUID.randomUUID().toString(),
                timestamp = anomaly.timestamp,
                protocol = DetectionProtocol.CELLULAR,
                detectionMethod = DetectionMethod.CELL_ENCRYPTION_DOWNGRADE,
                deviceType = com.flockyou.data.model.DeviceType.STINGRAY_IMSI,
                deviceName = "Cell ${anomaly.cellId}",
                macAddress = null,
                ssid = null,
                rssi = anomaly.signalStrength ?: -70,
                signalStrength = rssiToSignalStrength(anomaly.signalStrength ?: -70),
                latitude = currentLatitude,
                longitude = currentLongitude,
                threatLevel = if (anomaly.type == CellularAnomalyType.IMSI_CATCHER_SUSPECTED ||
                    anomaly.type == CellularAnomalyType.FAKE_BASE_STATION) ThreatLevel.CRITICAL else ThreatLevel.HIGH,
                threatScore = 80,
                manufacturer = null,
                firmwareVersion = null,
                serviceUuids = null,
                matchedPatterns = "[TEST_MODE,${anomaly.type.name}]",
                rawData = anomaly.description,
                isActive = true,
                seenCount = 1,
                lastSeenTimestamp = System.currentTimeMillis(),
                detectionSource = DetectionSource.CELLULAR,
                fpScore = null,
                fpReason = null,
                fpCategory = null,
                analyzedAt = null,
                llmAnalyzed = false
            )

            detectionRepository.upsertDetection(detection)
            incrementDetectionCount()
        }
    }

    private suspend fun processGnssAnomalies(anomalies: List<GnssAnomaly>) {
        for (anomaly in anomalies) {
            val (method, deviceType, threatLevel, threatScore) = when (anomaly.type) {
                GnssAnomalyType.SPOOFING_DETECTED -> Quadruple(
                    DetectionMethod.GNSS_SPOOFING, DeviceType.GNSS_SPOOFER, ThreatLevel.CRITICAL, 95
                )
                GnssAnomalyType.JAMMING -> Quadruple(
                    DetectionMethod.GNSS_JAMMING, DeviceType.GNSS_JAMMER, ThreatLevel.CRITICAL, 90
                )
                GnssAnomalyType.UNIFORM_CN0 -> Quadruple(
                    DetectionMethod.GNSS_SIGNAL_ANOMALY, DeviceType.GNSS_SPOOFER, ThreatLevel.HIGH, 75
                )
                GnssAnomalyType.CONSTELLATION_MISMATCH -> Quadruple(
                    DetectionMethod.GNSS_CONSTELLATION_ANOMALY, DeviceType.GNSS_SPOOFER, ThreatLevel.HIGH, 75
                )
                GnssAnomalyType.POSITION_JUMP -> Quadruple(
                    DetectionMethod.GNSS_GEOMETRY_ANOMALY, DeviceType.GNSS_SPOOFER, ThreatLevel.HIGH, 75
                )
                GnssAnomalyType.CLOCK_DRIFT -> Quadruple(
                    DetectionMethod.GNSS_CLOCK_ANOMALY, DeviceType.GNSS_SPOOFER, ThreatLevel.HIGH, 75
                )
                GnssAnomalyType.FIX_LOST -> Quadruple(
                    DetectionMethod.GNSS_SIGNAL_LOSS, DeviceType.GNSS_JAMMER, ThreatLevel.HIGH, 70
                )
            }
            val detection = Detection(
                id = UUID.randomUUID().toString(),
                timestamp = anomaly.timestamp,
                protocol = DetectionProtocol.GNSS,
                detectionMethod = method,
                deviceType = deviceType,
                deviceName = "Test GNSS ${anomaly.type.name}",
                rssi = -100,
                signalStrength = SignalStrength.UNKNOWN,
                latitude = currentLatitude,
                longitude = currentLongitude,
                threatLevel = threatLevel,
                threatScore = threatScore,
                matchedPatterns = "[TEST_MODE,${anomaly.type.name}]",
                rawData = buildString {
                    append(anomaly.description)
                    append("; cn0Average=").append(anomaly.cn0Average)
                    append("; satelliteCount=").append(anomaly.satelliteCount)
                    append("; hdop=").append(anomaly.hdop)
                    if (anomaly.metadata.isNotEmpty()) append("; metadata=").append(anomaly.metadata)
                },
                detectionSource = DetectionSource.GNSS
            )
            detectionRepository.upsertDetection(detection)
            incrementDetectionCount()
        }
    }

    private suspend fun processUltrasonicDetection(result: UltrasonicDetection) {
        val method = when (result.beaconType) {
            MockAudioBeacon.TYPE_AD_TRACKING, MockAudioBeacon.TYPE_TV_ATTRIBUTION -> DetectionMethod.ULTRASONIC_AD_BEACON
            MockAudioBeacon.TYPE_RETAIL_ANALYTICS -> DetectionMethod.ULTRASONIC_RETAIL_BEACON
            MockAudioBeacon.TYPE_CROSS_DEVICE -> DetectionMethod.ULTRASONIC_CROSS_DEVICE
            else -> DetectionMethod.ULTRASONIC_UNKNOWN
        }
        val detection = Detection(
            id = UUID.randomUUID().toString(),
            timestamp = result.timestamp,
            protocol = DetectionProtocol.AUDIO,
            detectionMethod = method,
            deviceType = DeviceType.ULTRASONIC_BEACON,
            deviceName = "Test Ultrasonic ${result.beaconType}",
            rssi = -100,
            signalStrength = SignalStrength.UNKNOWN,
            latitude = currentLatitude,
            longitude = currentLongitude,
            threatLevel = ThreatLevel.MEDIUM,
            threatScore = 50,
            matchedPatterns = "[TEST_MODE,${result.beaconType}]",
            rawData = "frequencyHz=${result.frequencyHz}; amplitudeDb=${result.amplitudeDb}; durationMs=${result.durationMs}; confidence=${result.confidence}; metadata=${result.metadata}",
            detectionSource = DetectionSource.AUDIO
        )
        detectionRepository.upsertDetection(detection)
        incrementDetectionCount()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun incrementDetectionCount() {
        _status.update {
            it.copy(
                detectionCount = it.detectionCount + 1,
                lastDetectionTime = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Status of test mode operation.
 */
data class TestModeStatus(
    val isActive: Boolean = false,
    val activeScenarioId: String? = null,
    val activeScenarioName: String? = null,
    val startTime: Long? = null,
    val detectionCount: Int = 0,
    val lastDetectionTime: Long? = null,
    val lastError: String? = null
) {
    val sessionDurationMs: Long?
        get() = startTime?.let { System.currentTimeMillis() - it }

    val hasGeneratedDetections: Boolean
        get() = detectionCount > 0

    val isScenarioActive: Boolean
        get() = activeScenarioId != null
}
