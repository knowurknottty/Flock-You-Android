package com.flockyou.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.flockyou.R
import com.flockyou.data.model.Detection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ========== AI Analysis (extension functions on MainViewModel) ==========

/**
 * Prioritize a detection for immediate LLM enrichment.
 * This triggers the BackgroundAnalysisWorker to analyze the specific detection immediately.
 */
fun MainViewModel.prioritizeEnrichment(detection: Detection) {
    viewModelScope.launch {
        Log.d("MainViewModel", "Prioritizing enrichment for detection: ${detection.id}")

        // Add to the set of prioritized IDs
        _prioritizedEnrichmentIds.update { it + detection.id }

        // Trigger the background analysis worker for this specific detection
        com.flockyou.worker.BackgroundAnalysisWorker.triggerForDetections(
            application,
            listOf(detection.id)
        )

        // Remove from prioritized set after a delay (the worker will process it)
        delay(5000)
        _prioritizedEnrichmentIds.update { it - detection.id }
    }
}

/**
 * Check if a detection is currently queued for prioritized enrichment.
 */
fun MainViewModel.isEnrichmentPending(detectionId: String): Boolean {
    return _prioritizedEnrichmentIds.value.contains(detectionId)
}

/**
 * Analyze a detection using the on-device AI.
 */
fun MainViewModel.analyzeDetection(detection: Detection) {
    viewModelScope.launch {
        Log.d("MainViewModel", "Starting AI analysis for detection: ${detection.id} (${detection.deviceType})")
        _uiState.update { it.copy(analyzingDetectionId = detection.id, analysisResult = null) }

        try {
            val result = detectionAnalyzer.analyzeDetection(detection)
            Log.d("MainViewModel", "AI analysis complete: success=${result.success}, model=${result.modelUsed}, " +
                "error=${result.error}, analysisLength=${result.analysis?.length ?: 0}")
            _uiState.update { it.copy(analyzingDetectionId = null, analysisResult = result) }
        } catch (e: Exception) {
            Log.e("MainViewModel", "AI analysis failed with exception", e)
            _uiState.update {
                it.copy(
                    analyzingDetectionId = null,
                    analysisResult = com.flockyou.data.AiAnalysisResult(
                        success = false,
                        error = e.message ?: "Analysis failed"
                    )
                )
            }
        }
    }
}

/**
 * Clear the analysis result.
 */
fun MainViewModel.clearAnalysisResult() {
    _uiState.update { it.copy(analysisResult = null) }
}

/**
 * Check if AI analysis is available (always true since rule-based fallback exists).
 */
fun MainViewModel.isAiAnalysisAvailable(): Boolean {
    // Rule-based analysis is always available as a fallback
    return true
}

// ========== Debug Export ==========

/**
 * Export all detection debug information for algorithm tuning.
 * Returns a formatted string containing all detection-related state.
 */
fun MainViewModel.exportAllDebugInfo(): String {
    val state = _uiState.value
    val sb = StringBuilder()
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
    val now = System.currentTimeMillis()

    sb.appendLine("=== ${getApplication<Application>().getString(R.string.debug_export_header)} ===")
    sb.appendLine("Export Time: ${dateFormat.format(java.util.Date(now))}")
    sb.appendLine("App Version: ${getAppVersion()}")
    sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    sb.appendLine("Advanced Mode: ${state.advancedMode}")
    sb.appendLine("Scanning Active: ${state.isScanning}")
    sb.appendLine("Scan Status: ${state.scanStatus}")
    sb.appendLine()

    // Subsystem Status
    sb.appendLine("=== SUBSYSTEM STATUS ===")
    sb.appendLine("BLE: ${state.bleStatus}")
    sb.appendLine("WiFi: ${state.wifiStatus}")
    sb.appendLine("Location: ${state.locationStatus}")
    sb.appendLine("Cellular: ${state.cellularStatus}")
    sb.appendLine("Satellite: ${state.satelliteStatus}")
    sb.appendLine()

    // Detection Summary
    sb.appendLine("=== DETECTION SUMMARY ===")
    sb.appendLine("Total Detections: ${state.totalCount}")
    sb.appendLine("High Threat Count: ${state.highThreatCount}")
    sb.appendLine("Last Detection: ${state.lastDetection?.let { "${it.deviceType} - ${it.deviceName}" } ?: "none"}")
    sb.appendLine()

    // Scan Statistics
    sb.appendLine("=== SCAN STATISTICS ===")
    val stats = state.scanStats
    sb.appendLine("Total BLE Scans: ${stats.totalBleScans}")
    sb.appendLine("Total WiFi Scans: ${stats.totalWifiScans}")
    sb.appendLine("BLE Devices Seen: ${stats.bleDevicesSeen}")
    sb.appendLine("WiFi Networks Seen: ${stats.wifiNetworksSeen}")
    sb.appendLine("Detections Created: ${stats.detectionsCreated}")
    sb.appendLine("BLE Candidates: ${stats.bleCandidates}")
    sb.appendLine("WiFi Candidates: ${stats.wifiCandidates}")
    sb.appendLine("BLE New/Not-new: ${stats.bleDetectionsCreated}/${stats.bleDetectionsNotNew}")
    sb.appendLine("WiFi New/Not-new: ${stats.wifiDetectionsCreated}/${stats.wifiDetectionsNotNew}")
    sb.appendLine("WiFi Explicit FP Suppressions: ${stats.wifiExplicitSuppressions}")
    sb.appendLine("Last BLE Scan: ${stats.lastBleSuccessTime?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
    sb.appendLine("Last WiFi Scan: ${stats.lastWifiSuccessTime?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
    sb.appendLine()

    // Seen BLE Devices
    sb.appendLine("=== SEEN BLE DEVICES (${state.seenBleDevices.size}) ===")
    if (state.seenBleDevices.isEmpty()) {
        sb.appendLine("No BLE devices seen")
    } else {
        state.seenBleDevices.sortedByDescending { it.lastSeen }.take(50).forEach { device ->
            sb.appendLine("${device.name ?: "Unknown"} [${device.id}]")
            sb.appendLine("  RSSI: ${device.rssi}dBm, Seen: ${device.seenCount}x")
            sb.appendLine("  Last: ${dateFormat.format(java.util.Date(device.lastSeen))}")
        }
        if (state.seenBleDevices.size > 50) {
            sb.appendLine("... and ${state.seenBleDevices.size - 50} more")
        }
    }
    sb.appendLine()

    // Seen WiFi Networks
    sb.appendLine("=== SEEN WIFI NETWORKS (${state.seenWifiNetworks.size}) ===")
    if (state.seenWifiNetworks.isEmpty()) {
        sb.appendLine("No WiFi networks seen")
    } else {
        state.seenWifiNetworks.sortedByDescending { it.lastSeen }.take(50).forEach { network ->
            sb.appendLine("${network.name ?: "Hidden"} [${network.id}]")
            sb.appendLine("  RSSI: ${network.rssi}dBm, Seen: ${network.seenCount}x")
            sb.appendLine("  Last: ${dateFormat.format(java.util.Date(network.lastSeen))}")
        }
        if (state.seenWifiNetworks.size > 50) {
            sb.appendLine("... and ${state.seenWifiNetworks.size - 50} more")
        }
    }
    sb.appendLine()

    // Cellular Status
    sb.appendLine("=== CELLULAR STATUS ===")
    val cellStatus = state.cellStatus
    if (cellStatus != null) {
        sb.appendLine("Network Type: ${cellStatus.networkType}")
        sb.appendLine("Operator: ${cellStatus.operator}")
        sb.appendLine("Signal Strength: ${cellStatus.signalStrength}dBm")
        sb.appendLine("Cell ID: ${cellStatus.cellId}")
        sb.appendLine("LAC: ${cellStatus.lac}")
        sb.appendLine("MCC: ${cellStatus.mcc}, MNC: ${cellStatus.mnc}")
    } else {
        sb.appendLine("No cellular status available")
    }
    sb.appendLine()

    // Seen Cell Towers
    sb.appendLine("=== SEEN CELL TOWERS (${state.seenCellTowers.size}) ===")
    if (state.seenCellTowers.isEmpty()) {
        sb.appendLine("No cell towers seen")
    } else {
        state.seenCellTowers.sortedByDescending { it.lastSeen }.forEach { tower ->
            sb.appendLine("Cell ${tower.cellId} (${tower.networkType})")
            sb.appendLine("  Signal: ${tower.lastSignal}dBm, Seen: ${tower.seenCount}x")
            sb.appendLine("  LAC: ${tower.lac}, MCC: ${tower.mcc}, MNC: ${tower.mnc}")
            sb.appendLine("  Last: ${dateFormat.format(java.util.Date(tower.lastSeen))}")
        }
    }
    sb.appendLine()

    // Cellular Anomalies
    sb.appendLine("=== CELLULAR ANOMALIES (${state.cellularAnomalies.size}) ===")
    if (state.cellularAnomalies.isEmpty()) {
        sb.appendLine("No cellular anomalies detected")
    } else {
        state.cellularAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Severity: ${anomaly.severity}")
        }
    }
    sb.appendLine()

    // Cellular Events
    sb.appendLine("=== CELLULAR EVENTS (last 20) ===")
    if (state.cellularEvents.isEmpty()) {
        sb.appendLine("No cellular events")
    } else {
        state.cellularEvents.take(20).forEach { event ->
            sb.appendLine("${dateFormat.format(java.util.Date(event.timestamp))} [${event.type}] ${event.description}")
        }
    }
    sb.appendLine()

    // Satellite Status
    sb.appendLine("=== SATELLITE CONNECTION STATUS ===")
    val satState = state.satelliteState
    if (satState != null) {
        sb.appendLine("Connected: ${satState.isConnected}")
        sb.appendLine("Connection Type: ${satState.connectionType}")
        sb.appendLine("Provider: ${satState.provider}")
        sb.appendLine("Signal Strength: ${satState.signalStrength ?: "unknown"}")
    } else {
        sb.appendLine("No satellite status available")
    }
    sb.appendLine()

    // Satellite Anomalies
    sb.appendLine("=== SATELLITE ANOMALIES (${state.satelliteAnomalies.size}) ===")
    if (state.satelliteAnomalies.isEmpty()) {
        sb.appendLine("No satellite anomalies detected")
    } else {
        state.satelliteAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Description: ${anomaly.description}")
        }
    }
    sb.appendLine()

    // GNSS Status
    sb.appendLine("=== GNSS STATUS ===")
    val gnssStatus = state.gnssStatus
    if (gnssStatus != null) {
        sb.appendLine("Total Satellites: ${gnssStatus.totalSatellites}")
        sb.appendLine("Satellites Used In Fix: ${gnssStatus.satellitesUsedInFix}")
        sb.appendLine("Has Fix: ${gnssStatus.hasFix}")
        sb.appendLine("Fix Accuracy: ${gnssStatus.fixAccuracyMeters ?: "unknown"}m")
        sb.appendLine("Spoofing Risk Level: ${gnssStatus.spoofingRiskLevel}")
    } else {
        sb.appendLine("No GNSS status available")
    }
    sb.appendLine()

    // GNSS Satellites
    sb.appendLine("=== GNSS SATELLITES (${state.gnssSatellites.size}) ===")
    if (state.gnssSatellites.isEmpty()) {
        sb.appendLine("No GNSS satellites visible")
    } else {
        state.gnssSatellites.sortedByDescending { it.cn0DbHz }.forEach { sat ->
            sb.appendLine("${sat.constellation} PRN ${sat.svid}: ${sat.cn0DbHz}dB-Hz, Elev=${sat.elevationDegrees}, Az=${sat.azimuthDegrees}, Used=${sat.usedInFix}")
        }
    }
    sb.appendLine()

    // GNSS Anomalies
    sb.appendLine("=== GNSS ANOMALIES (${state.gnssAnomalies.size}) ===")
    if (state.gnssAnomalies.isEmpty()) {
        sb.appendLine("No GNSS anomalies detected")
    } else {
        state.gnssAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Severity: ${anomaly.severity}")
        }
    }
    sb.appendLine()

    // GNSS Measurements
    sb.appendLine("=== GNSS MEASUREMENTS ===")
    val gnssMeas = state.gnssMeasurements
    if (gnssMeas != null) {
        sb.appendLine("Clock Bias: ${gnssMeas.clockBiasNs ?: "unknown"}ns")
        sb.appendLine("Clock Drift: ${gnssMeas.clockDriftNsPerSec ?: "unknown"}ns/s")
        sb.appendLine("Measurement Count: ${gnssMeas.measurementCount}")
    } else {
        sb.appendLine("No GNSS measurements available")
    }
    sb.appendLine()

    // Ultrasonic Status
    sb.appendLine("=== ULTRASONIC STATUS ===")
    val ultraStatus = state.ultrasonicStatus
    if (ultraStatus != null) {
        sb.appendLine("Scanning Active: ${ultraStatus.isScanning}")
        sb.appendLine("Noise Floor: ${ultraStatus.noiseFloorDb}dB")
        sb.appendLine("Ultrasonic Activity Detected: ${ultraStatus.ultrasonicActivityDetected}")
        sb.appendLine("Active Beacon Count: ${ultraStatus.activeBeaconCount}")
    } else {
        sb.appendLine("No ultrasonic status available")
    }
    sb.appendLine()

    // Ultrasonic Anomalies
    sb.appendLine("=== ULTRASONIC ANOMALIES (${state.ultrasonicAnomalies.size}) ===")
    if (state.ultrasonicAnomalies.isEmpty()) {
        sb.appendLine("No ultrasonic anomalies detected")
    } else {
        state.ultrasonicAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Frequency: ${anomaly.frequency ?: "unknown"}Hz")
        }
    }
    sb.appendLine()

    // Ultrasonic Beacons
    sb.appendLine("=== ULTRASONIC BEACONS (${state.ultrasonicBeacons.size}) ===")
    if (state.ultrasonicBeacons.isEmpty()) {
        sb.appendLine("No ultrasonic beacons detected")
    } else {
        state.ultrasonicBeacons.sortedByDescending { it.lastDetected }.forEach { beacon ->
            sb.appendLine("Beacon at ${beacon.frequency}Hz")
            sb.appendLine("  Amplitude: ${beacon.peakAmplitudeDb}dB, Source: ${beacon.possibleSource}")
            sb.appendLine("  First: ${dateFormat.format(java.util.Date(beacon.firstDetected))}")
            sb.appendLine("  Last: ${dateFormat.format(java.util.Date(beacon.lastDetected))}")
        }
    }
    sb.appendLine()

    // RF Status
    sb.appendLine("=== RF STATUS ===")
    val rfStatus = state.rfStatus
    if (rfStatus != null) {
        sb.appendLine("Total Networks: ${rfStatus.totalNetworks}")
        sb.appendLine("Band Distribution: 2.4GHz=${rfStatus.band24GHz}, 5GHz=${rfStatus.band5GHz}, 6GHz=${rfStatus.band6GHz}")
        sb.appendLine("Average Signal: ${rfStatus.averageSignalStrength}dBm")
        sb.appendLine("Noise Level: ${rfStatus.noiseLevel.displayName}")
        sb.appendLine("Channel Congestion: ${rfStatus.channelCongestion.displayName}")
        sb.appendLine("Environment Risk: ${rfStatus.environmentRisk.displayName}")
        sb.appendLine("Jammer Suspected: ${rfStatus.jammerSuspected}")
        sb.appendLine("Drones Detected: ${rfStatus.dronesDetected}")
        sb.appendLine("Surveillance Cameras: ${rfStatus.surveillanceCameras}")
        sb.appendLine("Last Scan: ${dateFormat.format(java.util.Date(rfStatus.lastScanTime))}")
    } else {
        sb.appendLine("No RF status available")
    }
    sb.appendLine()

    // RF Anomalies
    sb.appendLine("=== RF ANOMALIES (${state.rfAnomalies.size}) ===")
    if (state.rfAnomalies.isEmpty()) {
        sb.appendLine("No RF anomalies detected")
    } else {
        state.rfAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type.emoji} ${anomaly.type.displayName} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Severity: ${anomaly.severity}, Confidence: ${anomaly.confidence.displayName}")
            sb.appendLine("  Advanced Only: ${anomaly.isAdvancedOnly}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Technical Details:")
            anomaly.technicalDetails.lines().forEach { line ->
                sb.appendLine("    $line")
            }
            if (anomaly.contributingFactors.isNotEmpty()) {
                sb.appendLine("  Contributing Factors:")
                anomaly.contributingFactors.forEach { factor ->
                    sb.appendLine("    - $factor")
                }
            }
        }
    }
    sb.appendLine()

    // Detected Drones
    sb.appendLine("=== DETECTED DRONES (${state.detectedDrones.size}) ===")
    if (state.detectedDrones.isEmpty()) {
        sb.appendLine("No drones detected")
    } else {
        state.detectedDrones.sortedByDescending { it.lastSeen }.forEach { drone ->
            sb.appendLine("--- ${drone.manufacturer} Drone ---")
            sb.appendLine("  BSSID: ${drone.bssid}, SSID: ${drone.ssid}")
            sb.appendLine("  RSSI: ${drone.rssi}dBm, Distance: ${drone.estimatedDistance}")
            sb.appendLine("  Seen: ${drone.seenCount}x")
            sb.appendLine("  First: ${dateFormat.format(java.util.Date(drone.firstSeen))}")
            sb.appendLine("  Last: ${dateFormat.format(java.util.Date(drone.lastSeen))}")
        }
    }
    sb.appendLine()

    // Rogue WiFi Status
    sb.appendLine("=== ROGUE WIFI STATUS ===")
    val rogueStatus = state.rogueWifiStatus
    if (rogueStatus != null) {
        sb.appendLine("Total Networks: ${rogueStatus.totalNetworks}")
        sb.appendLine("Open Networks: ${rogueStatus.openNetworks}")
        sb.appendLine("Suspicious: ${rogueStatus.suspiciousNetworks}")
        sb.appendLine("Hidden Networks: ${rogueStatus.hiddenNetworks}")
        sb.appendLine("Potential Evil Twins: ${rogueStatus.potentialEvilTwins}")
    } else {
        sb.appendLine("No rogue WiFi status available")
    }
    sb.appendLine()

    // Suspicious Networks
    sb.appendLine("=== SUSPICIOUS NETWORKS (${state.suspiciousNetworks.size}) ===")
    if (state.suspiciousNetworks.isEmpty()) {
        sb.appendLine("No suspicious networks")
    } else {
        state.suspiciousNetworks.forEach { network ->
            sb.appendLine("${network.ssid} [${network.bssid}]")
            sb.appendLine("  Reason: ${network.reason}")
            sb.appendLine("  Threat Level: ${network.threatLevel}, RSSI: ${network.rssi}dBm")
        }
    }
    sb.appendLine()

    // Rogue WiFi Anomalies
    sb.appendLine("=== ROGUE WIFI ANOMALIES (${state.rogueWifiAnomalies.size}) ===")
    if (state.rogueWifiAnomalies.isEmpty()) {
        sb.appendLine("No rogue WiFi anomalies")
    } else {
        state.rogueWifiAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type} ---")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Severity: ${anomaly.severity}")
        }
    }
    sb.appendLine()

    // Detector Health Status
    sb.appendLine("=== DETECTOR HEALTH STATUS ===")
    if (state.detectorHealth.isEmpty()) {
        sb.appendLine("No detector health data")
    } else {
        state.detectorHealth.forEach { (name, health) ->
            sb.appendLine("$name:")
            sb.appendLine("  Running: ${health.isRunning}, Healthy: ${health.isHealthy}")
            sb.appendLine("  Last Success: ${health.lastSuccessfulScan?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
            sb.appendLine("  Last Error: ${health.lastError ?: "none"}")
            sb.appendLine("  Consecutive Failures: ${health.consecutiveFailures}")
        }
    }
    sb.appendLine()

    // Recent Errors
    sb.appendLine("=== RECENT ERRORS (${state.recentErrors.size}) ===")
    if (state.recentErrors.isEmpty()) {
        sb.appendLine("No recent errors")
    } else {
        state.recentErrors.forEach { error ->
            sb.appendLine("${dateFormat.format(java.util.Date(error.timestamp))} [${error.subsystem}]: ${error.message}")
        }
    }
    sb.appendLine()

    // Detection Type Breakdown - comprehensive summary of all detections
    sb.appendLine("=== DETECTION TYPE BREAKDOWN (${state.detections.size} total) ===")
    if (state.detections.isEmpty()) {
        sb.appendLine("No detections recorded")
    } else {
        // Group by device type
        val byDeviceType = state.detections.groupBy { it.deviceType }
        sb.appendLine("By Device Type:")
        byDeviceType.entries.sortedByDescending { it.value.size }.forEach { (type, detections) ->
            sb.appendLine("  $type: ${detections.size}")
        }
        sb.appendLine()

        // Group by protocol
        val byProtocol = state.detections.groupBy { it.protocol }
        sb.appendLine("By Protocol:")
        byProtocol.entries.sortedByDescending { it.value.size }.forEach { (protocol, detections) ->
            sb.appendLine("  $protocol: ${detections.size}")
        }
        sb.appendLine()

        // Group by detection method
        val byMethod = state.detections.groupBy { it.detectionMethod }
        sb.appendLine("By Detection Method:")
        byMethod.entries.sortedByDescending { it.value.size }.forEach { (method, detections) ->
            sb.appendLine("  $method: ${detections.size}")
        }
        sb.appendLine()

        // Group by threat level
        val byThreatLevel = state.detections.groupBy { it.threatLevel }
        sb.appendLine("By Threat Level:")
        byThreatLevel.entries.sortedByDescending { it.key.ordinal }.forEach { (level, detections) ->
            sb.appendLine("  $level: ${detections.size}")
        }
        sb.appendLine()

        // Time distribution
        val oldestDetection = state.detections.minByOrNull { it.timestamp }
        val newestDetection = state.detections.maxByOrNull { it.timestamp }
        if (oldestDetection != null && newestDetection != null) {
            sb.appendLine("Time Range:")
            sb.appendLine("  Oldest: ${dateFormat.format(java.util.Date(oldestDetection.timestamp))}")
            sb.appendLine("  Newest: ${dateFormat.format(java.util.Date(newestDetection.timestamp))}")
            val durationMs = newestDetection.timestamp - oldestDetection.timestamp
            val durationMin = durationMs / 60_000
            sb.appendLine("  Span: ${durationMin} minutes")
            if (durationMin > 0) {
                sb.appendLine("  Rate: ${String.format("%.1f", state.detections.size.toFloat() / durationMin)} detections/min")
            }
        }
    }
    sb.appendLine()

    // All Detections - full list for debugging
    sb.appendLine("=== ALL DETECTIONS (${state.detections.size}) ===")
    if (state.detections.isEmpty()) {
        sb.appendLine("No detections")
    } else {
        state.detections.sortedByDescending { it.timestamp }.forEach { detection ->
            sb.appendLine("--- ${detection.deviceType} ---")
            sb.appendLine("  Name: ${detection.deviceName}")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(detection.timestamp))}")
            sb.appendLine("  Protocol: ${detection.protocol}, Method: ${detection.detectionMethod}")
            sb.appendLine("  Threat: ${detection.threatLevel} (score: ${detection.threatScore})")
            sb.appendLine("  MAC: ${detection.macAddress ?: "unknown"}")
            sb.appendLine("  RSSI: ${detection.rssi}dBm")
            detection.matchedPatterns?.let { sb.appendLine("  Patterns: $it") }
            detection.manufacturer?.let { sb.appendLine("  Manufacturer: $it") }
            detection.ssid?.let { sb.appendLine("  SSID: $it") }
            if (detection.latitude != null && detection.longitude != null) {
                sb.appendLine("  Location: ${detection.latitude}, ${detection.longitude}")
            }
        }
    }
    sb.appendLine()

    // Flipper Zero Status
    sb.appendLine("=== FLIPPER ZERO STATUS ===")
    sb.appendLine("Connection State: ${state.flipperConnectionState}")
    sb.appendLine("Connection Type: ${state.flipperConnectionType}")
    sb.appendLine("Scanning: ${state.flipperIsScanning}")
    sb.appendLine("Detection Count: ${state.flipperDetectionCount}")
    sb.appendLine("WIPS Alerts: ${state.flipperWipsAlertCount}")
    state.flipperLastError?.let { sb.appendLine("Last Error: $it") }
    state.flipperStatus?.let { status ->
        sb.appendLine("Flipper Status:")
        sb.appendLine("  Battery: ${status.batteryPercent}%")
        sb.appendLine("  Uptime: ${status.uptimeSeconds}s")
        sb.appendLine("  WiFi Board Connected: ${status.wifiBoardConnected}")
    }
    sb.appendLine()

    sb.appendLine("=== END DEBUG EXPORT ===")
    sb.appendLine()
    sb.appendLine("Please share this export to help improve detection accuracy.")

    return sb.toString()
}

/**
 * Export RF detection debug information for algorithm tuning.
 * Returns a formatted string containing all RF-related state.
 */
fun MainViewModel.exportRfDebugInfo(): String {
    val state = _uiState.value
    val sb = StringBuilder()
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
    val now = System.currentTimeMillis()

    sb.appendLine("=== ${getApplication<Application>().getString(R.string.rf_debug_export_header)} ===")
    sb.appendLine("Export Time: ${dateFormat.format(java.util.Date(now))}")
    sb.appendLine("App Version: ${getAppVersion()}")
    sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    sb.appendLine("Advanced Mode: ${state.advancedMode}")
    sb.appendLine("Scanning Active: ${state.isScanning}")
    sb.appendLine()

    // Current RF Status
    sb.appendLine("=== CURRENT RF STATUS ===")
    val rfStatus = state.rfStatus
    if (rfStatus != null) {
        sb.appendLine("Total Networks: ${rfStatus.totalNetworks}")
        sb.appendLine("Band Distribution: 2.4GHz=${rfStatus.band24GHz}, 5GHz=${rfStatus.band5GHz}, 6GHz=${rfStatus.band6GHz}")
        sb.appendLine("Average Signal Strength: ${rfStatus.averageSignalStrength}dBm")
        sb.appendLine("Noise Level: ${rfStatus.noiseLevel.displayName} ${rfStatus.noiseLevel.emoji}")
        sb.appendLine("Channel Congestion: ${rfStatus.channelCongestion.displayName}")
        sb.appendLine("Environment Risk: ${rfStatus.environmentRisk.displayName} ${rfStatus.environmentRisk.emoji}")
        sb.appendLine("Jammer Suspected: ${rfStatus.jammerSuspected}")
        sb.appendLine("Drones Detected: ${rfStatus.dronesDetected}")
        sb.appendLine("Surveillance Cameras: ${rfStatus.surveillanceCameras}")
        sb.appendLine("Last Scan: ${dateFormat.format(java.util.Date(rfStatus.lastScanTime))}")
    } else {
        sb.appendLine("No RF status available (scanning may not have started)")
    }
    sb.appendLine()

    // All RF Anomalies (including advanced-only)
    sb.appendLine("=== ALL RF ANOMALIES (${state.rfAnomalies.size} total) ===")
    if (state.rfAnomalies.isEmpty()) {
        sb.appendLine("No anomalies detected")
    } else {
        state.rfAnomalies.sortedByDescending { it.timestamp }.forEach { anomaly ->
            sb.appendLine("--- ${anomaly.type.emoji} ${anomaly.type.displayName} ---")
            sb.appendLine("  ID: ${anomaly.id}")
            sb.appendLine("  Time: ${dateFormat.format(java.util.Date(anomaly.timestamp))}")
            sb.appendLine("  Display Name: ${anomaly.displayName}")
            sb.appendLine("  Severity: ${anomaly.severity}")
            sb.appendLine("  Confidence: ${anomaly.confidence.displayName}")
            sb.appendLine("  Advanced Only: ${anomaly.isAdvancedOnly}")
            sb.appendLine("  Description: ${anomaly.description}")
            sb.appendLine("  Technical Details:")
            anomaly.technicalDetails.lines().forEach { line ->
                sb.appendLine("    $line")
            }
            if (anomaly.contributingFactors.isNotEmpty()) {
                sb.appendLine("  Contributing Factors:")
                anomaly.contributingFactors.forEach { factor ->
                    sb.appendLine("    - $factor")
                }
            }
            if (anomaly.latitude != null && anomaly.longitude != null) {
                sb.appendLine("  Location: ${anomaly.latitude}, ${anomaly.longitude}")
            }
            sb.appendLine()
        }
    }

    // Detected Drones
    sb.appendLine("=== DETECTED DRONES (${state.detectedDrones.size}) ===")
    if (state.detectedDrones.isEmpty()) {
        sb.appendLine("No drones detected")
    } else {
        state.detectedDrones.sortedByDescending { it.lastSeen }.forEach { drone ->
            sb.appendLine("--- ${drone.manufacturer} Drone ---")
            sb.appendLine("  BSSID: ${drone.bssid}")
            sb.appendLine("  SSID: ${drone.ssid}")
            sb.appendLine("  First Seen: ${dateFormat.format(java.util.Date(drone.firstSeen))}")
            sb.appendLine("  Last Seen: ${dateFormat.format(java.util.Date(drone.lastSeen))}")
            sb.appendLine("  Seen Count: ${drone.seenCount}")
            sb.appendLine("  RSSI: ${drone.rssi}dBm")
            sb.appendLine("  Estimated Distance: ${drone.estimatedDistance}")
            if (drone.latitude != null && drone.longitude != null) {
                sb.appendLine("  Location: ${drone.latitude}, ${drone.longitude}")
            }
            sb.appendLine()
        }
    }

    // Rogue WiFi Status (related to RF analysis)
    sb.appendLine("=== ROGUE WIFI STATUS ===")
    val rogueStatus = state.rogueWifiStatus
    if (rogueStatus != null) {
        sb.appendLine("Total Networks: ${rogueStatus.totalNetworks}")
        sb.appendLine("Open Networks: ${rogueStatus.openNetworks}")
        sb.appendLine("Suspicious Networks: ${rogueStatus.suspiciousNetworks}")
        sb.appendLine("Hidden Networks: ${rogueStatus.hiddenNetworks}")
        sb.appendLine("Potential Evil Twins: ${rogueStatus.potentialEvilTwins}")
    } else {
        sb.appendLine("No rogue WiFi status available")
    }
    sb.appendLine()

    // Suspicious Networks
    sb.appendLine("=== SUSPICIOUS NETWORKS (${state.suspiciousNetworks.size}) ===")
    if (state.suspiciousNetworks.isEmpty()) {
        sb.appendLine("No suspicious networks detected")
    } else {
        state.suspiciousNetworks.forEach { network ->
            sb.appendLine("--- ${network.ssid} ---")
            sb.appendLine("  BSSID: ${network.bssid}")
            sb.appendLine("  Reason: ${network.reason}")
            sb.appendLine("  Threat Level: ${network.threatLevel}")
            sb.appendLine("  RSSI: ${network.rssi}dBm")
            sb.appendLine()
        }
    }

    // Scan Statistics
    sb.appendLine("=== SCAN STATISTICS ===")
    val stats = state.scanStats
    sb.appendLine("Total BLE Scans: ${stats.totalBleScans}")
    sb.appendLine("Total WiFi Scans: ${stats.totalWifiScans}")
    sb.appendLine("BLE Devices Seen: ${stats.bleDevicesSeen}")
    sb.appendLine("WiFi Networks Seen: ${stats.wifiNetworksSeen}")
    sb.appendLine("Detections Created: ${stats.detectionsCreated}")
    sb.appendLine("BLE Candidates: ${stats.bleCandidates}")
    sb.appendLine("WiFi Candidates: ${stats.wifiCandidates}")
    sb.appendLine("BLE New/Not-new: ${stats.bleDetectionsCreated}/${stats.bleDetectionsNotNew}")
    sb.appendLine("WiFi New/Not-new: ${stats.wifiDetectionsCreated}/${stats.wifiDetectionsNotNew}")
    sb.appendLine("WiFi Explicit FP Suppressions: ${stats.wifiExplicitSuppressions}")
    sb.appendLine("Last BLE Scan: ${stats.lastBleSuccessTime?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
    sb.appendLine("Last WiFi Scan: ${stats.lastWifiSuccessTime?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
    sb.appendLine()

    // Detector Health Status
    sb.appendLine("=== DETECTOR HEALTH STATUS ===")
    if (state.detectorHealth.isEmpty()) {
        sb.appendLine("No detector health data available")
    } else {
        state.detectorHealth.forEach { (name, health) ->
            sb.appendLine("$name:")
            sb.appendLine("  Running: ${health.isRunning}")
            sb.appendLine("  Healthy: ${health.isHealthy}")
            sb.appendLine("  Last Success: ${health.lastSuccessfulScan?.let { dateFormat.format(java.util.Date(it)) } ?: "never"}")
            sb.appendLine("  Last Error: ${health.lastError ?: "none"}")
            sb.appendLine("  Consecutive Failures: ${health.consecutiveFailures}")
        }
    }
    sb.appendLine()

    // Recent Errors
    sb.appendLine("=== RECENT ERRORS (${state.recentErrors.size}) ===")
    if (state.recentErrors.isEmpty()) {
        sb.appendLine("No recent errors")
    } else {
        state.recentErrors.forEach { error ->
            sb.appendLine("${dateFormat.format(java.util.Date(error.timestamp))} [${error.subsystem}]: ${error.message}")
        }
    }
    sb.appendLine()

    sb.appendLine("=== END DEBUG EXPORT ===")
    sb.appendLine()
    sb.appendLine("Please share this export to help improve detection accuracy.")

    return sb.toString()
}
