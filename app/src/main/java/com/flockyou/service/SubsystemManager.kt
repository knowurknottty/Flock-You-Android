package com.flockyou.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flockyou.BuildConfig
import com.flockyou.MainActivity
import com.flockyou.R
import com.flockyou.data.model.*
import com.flockyou.detection.handler.SatelliteDetectionContext
import com.flockyou.detection.handler.SatelliteDetectionHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

/**
 * Subsystem start/stop logic for ScanningService, extracted for file-size management.
 *
 * Contains the start/stop methods for all 7 detection subsystems:
 * - Cellular monitoring
 * - Satellite monitoring
 * - Rogue WiFi monitoring
 * - RF signal analysis
 * - Ultrasonic detection
 * - GNSS satellite monitoring
 * - Satellite connection notifications
 *
 * All functions here are extension functions on [ScanningService].
 *
 * Related files:
 * - ScanningService.kt: Service lifecycle, core scan loop
 * - ScanningServiceBroadcaster.kt: IPC broadcast methods
 * - DetectionProcessor.kt: Detection result handling
 */

private const val TAG = "ScanningService"

// ==================== Cellular Monitoring ====================

internal fun ScanningService.startCellularMonitoring() {
    if (!hasTelephonyPermissions()) {
        ScanningServiceState.cellularStatus.value = SubsystemStatus.PermissionDenied("READ_PHONE_STATE")
        Log.w(TAG, "Missing telephony permissions for cellular monitoring")
        return
    }

    cellularMonitor?.startMonitoring()
    syncCurrentLocationToSubsystems()
    ScanningServiceState.cellularStatus.value = SubsystemStatus.Active
    broadcastSubsystemStatus()
    Log.d(TAG, "Cellular monitoring started")

    // Collect cellular status updates
    cellularStatusJob = serviceScope.launch {
        cellularMonitor?.cellStatus?.collect { status ->
            ScanningServiceState.cellStatus.value = status
            broadcastCellularData()
        }
    }

    // Collect seen cell tower history
    cellularHistoryJob = serviceScope.launch {
        cellularMonitor?.seenCellTowers?.collect { towers ->
            ScanningServiceState.seenCellTowers.value = towers
            broadcastCellularData()
        }
    }

    // Collect cellular timeline events
    cellularEventsJob = serviceScope.launch {
        cellularMonitor?.cellularEvents?.collect { events ->
            ScanningServiceState.cellularEvents.value = events
            broadcastCellularData()
        }
    }

    // Collect cellular anomalies and convert to detections
    // ==================== Handler-Based Cellular Detection ====================
    // Delegate anomaly-to-detection conversion to CellularDetectionHandler for:
    // - Settings-based filtering (enabled patterns, thresholds)
    // - IMSI catcher score calculation
    // - AI prompt generation for cellular anomalies
    // Note: processedCellularAnomalyIds is now a class-level property to persist across restarts

    cellularAnomalyJob = serviceScope.launch {
        cellularMonitor?.anomalies?.collect { anomalies ->
            ScanningServiceState.cellularAnomalies.value = anomalies
            broadcastCellularData()

            for (anomaly in anomalies) {
                // Skip if already processed in this session
                if (anomaly.id in processedCellularAnomalyIds) {
                    continue
                }

                // Send broadcast for automation apps
                sendCellularAnomalyBroadcast(anomaly)

                // Convert anomaly to detection using the handler
                // The handler implements settings-based filtering and IMSI score calculation
                val detection = processCellularWithHandler(anomaly)
                detection?.let { det ->
                    // Check if we already have this detection (use anomaly ID)
                    val detWithId = det.copy(macAddress = anomaly.id)
                    val existing = repository.getDetectionByMacAddress(anomaly.id)
                    if (existing == null) {
                        try {
                            // Store enriched heuristics data for LLM analysis
                            anomaly.analysis?.let { analysis ->
                                enrichedDataCache.putCellular(detWithId.id, analysis)
                                Log.d(TAG, "Stored cellular heuristics for detection ${detWithId.id} (IMSI score: ${analysis.imsiCatcherScore})")
                            }

                            insertDetectionWithAnalysis(detWithId)
                            processedCellularAnomalyIds.add(anomaly.id)

                            // Alert and vibrate for high-severity anomalies
                            if (det.threatLevel == ThreatLevel.CRITICAL ||
                                det.threatLevel == ThreatLevel.HIGH) {
                                alertUser(det)
                            }

                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()

                            if (BuildConfig.DEBUG) {
                                Log.w(TAG, "CELLULAR ANOMALY: ${anomaly.type.displayName} - ${anomaly.description}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving cellular detection: ${e.message}", e)
                        }
                    } else {
                        // Already in database, mark as processed
                        processedCellularAnomalyIds.add(anomaly.id)
                    }
                }
            }
        }
    }

    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().
}

internal fun ScanningService.stopCellularMonitoring() {
    cellularAnomalyJob?.cancel()
    cellularAnomalyJob = null
    cellularStatusJob?.cancel()
    cellularStatusJob = null
    cellularHistoryJob?.cancel()
    cellularHistoryJob = null
    cellularEventsJob?.cancel()
    cellularEventsJob = null
    cellularMonitor?.stopMonitoring()
    Log.d(TAG, "Cellular monitoring stopped")
}

// ==================== Satellite Monitoring ====================

@SuppressLint("MissingPermission")
internal fun ScanningService.startSatelliteMonitoring() {
    if (!hasTelephonyPermissions()) {
        ScanningServiceState.satelliteStatus.value = SubsystemStatus.PermissionDenied("READ_PHONE_STATE")
        Log.w(TAG, "Missing telephony permissions for satellite monitoring")
        return
    }

    Log.d(TAG, "Starting satellite monitoring")
    ScanningServiceState.satelliteStatus.value = SubsystemStatus.Active
    broadcastSubsystemStatus()

    satelliteMonitor?.startMonitoring()

    // Collect satellite state updates
    satelliteStateJob = serviceScope.launch {
        var wasSatelliteConnected = false
        satelliteMonitor?.satelliteState?.collect { state ->
            ScanningServiceState.satelliteState.value = state
            broadcastSatelliteData()
            Log.d(TAG, "Satellite state updated: connected=${state.isConnected}, type=${state.connectionType}")

            // Send/cancel satellite connection notification on state transitions
            if (state.isConnected && !wasSatelliteConnected) {
                sendSatelliteConnectionNotification(state)
            } else if (!state.isConnected && wasSatelliteConnected) {
                cancelSatelliteConnectionNotification()
            }
            wasSatelliteConnected = state.isConnected
        }
    }

    // Collect satellite anomalies
    // ==================== Handler-Based Satellite Detection ====================
    // Delegate anomaly-to-detection conversion to SatelliteDetectionHandler for:
    // - Settings-based filtering (enabled patterns, thresholds)
    // - NTN parameter analysis and timing validation
    // - Device type classification (SATELLITE_NTN vs STINGRAY_IMSI)
    // - AI prompt generation for satellite anomalies
    satelliteAnomalyJob = serviceScope.launch {
        satelliteMonitor?.anomalies?.collect { anomaly ->
            Log.d(TAG, "Satellite anomaly detected: ${anomaly.type} - ${anomaly.severity}")

            // Send broadcast for automation apps
            sendSatelliteAnomalyBroadcast(anomaly)

            // Add to anomaly list
            val currentAnomalies = ScanningServiceState.satelliteAnomalies.value.toMutableList()
            currentAnomalies.add(0, anomaly)
            if (currentAnomalies.size > 100) {
                currentAnomalies.removeLast()
            }
            ScanningServiceState.satelliteAnomalies.value = currentAnomalies
            broadcastSatelliteData()

            // Convert anomaly to detection using the handler
            // The handler implements settings-based filtering, NTN analysis,
            // and proper device type classification (SATELLITE_NTN vs STINGRAY_IMSI)
            val connectionState = satelliteMonitor?.satelliteState?.value
                ?: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState()

            val detection = processSatelliteWithHandler(anomaly, connectionState)
            if (detection != null) {
                handleDetection(detection)
            }
        }
    }
}

internal fun ScanningService.stopSatelliteMonitoring() {
    satelliteStateJob?.cancel()
    satelliteStateJob = null
    satelliteAnomalyJob?.cancel()
    satelliteAnomalyJob = null
    satelliteMonitor?.stopMonitoring()
    Log.d(TAG, "Satellite monitoring stopped")
}

/**
 * Legacy method for converting satellite anomalies to detections.
 *
 * @deprecated Use [processSatelliteWithHandler] instead, which delegates to
 * [SatelliteDetectionHandler] for more comprehensive analysis including:
 * - Settings-based pattern filtering
 * - NTN parameter validation
 * - Proper device type classification (SATELLITE_NTN vs STINGRAY_IMSI)
 * - AI prompt generation
 *
 * TODO: Remove this method once all callers are migrated to the handler.
 */
@Deprecated(
    message = "Use processSatelliteWithHandler for handler-based detection",
    replaceWith = ReplaceWith("processSatelliteWithHandler(anomaly, connectionState)")
)
internal fun ScanningService.satelliteAnomalyToDetection(anomaly: com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly): Detection? {
    val threatLevel = when (anomaly.severity) {
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.CRITICAL -> ThreatLevel.CRITICAL
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.HIGH -> ThreatLevel.HIGH
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.MEDIUM -> ThreatLevel.MEDIUM
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.LOW -> ThreatLevel.LOW
        com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.INFO -> ThreatLevel.LOW
    }

    val detectionMethod = when (anomaly.type) {
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.UNEXPECTED_SATELLITE_CONNECTION,
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.SATELLITE_IN_COVERED_AREA -> DetectionMethod.SAT_UNEXPECTED_CONNECTION
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.FORCED_SATELLITE_HANDOFF -> DetectionMethod.SAT_FORCED_HANDOFF
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.DOWNGRADE_TO_SATELLITE -> DetectionMethod.SAT_DOWNGRADE
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.SUSPICIOUS_NTN_PARAMETERS,
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.NTN_BAND_MISMATCH -> DetectionMethod.SAT_SUSPICIOUS_NTN
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.TIMING_ADVANCE_ANOMALY,
        com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomalyType.EPHEMERIS_MISMATCH -> DetectionMethod.SAT_TIMING_ANOMALY
        else -> DetectionMethod.SAT_UNEXPECTED_CONNECTION
    }

    return Detection(
        id = UUID.randomUUID().toString(),
        timestamp = anomaly.timestamp,
        protocol = DetectionProtocol.CELLULAR,
        detectionMethod = detectionMethod,
        deviceType = DeviceType.STINGRAY_IMSI,
        deviceName = "\uD83D\uDEF0\uFE0F ${anomaly.type.name.replace("_", " ")}",
        rssi = -100, // Unknown for satellite
        signalStrength = SignalStrength.UNKNOWN,
        latitude = currentLocation?.latitude,
        longitude = currentLocation?.longitude,
        threatLevel = threatLevel,
        threatScore = when (anomaly.severity) {
            com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.CRITICAL -> 100
            com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.HIGH -> 80
            com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.MEDIUM -> 50
            com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.LOW -> 30
            com.flockyou.monitoring.SatelliteMonitor.AnomalySeverity.INFO -> 10
        },
        manufacturer = "Satellite Network",
        matchedPatterns = anomaly.description
    )
}

// ==================== Rogue WiFi Monitoring ====================

internal fun ScanningService.startRogueWifiMonitoring() {
    Log.d(TAG, "Starting rogue WiFi monitoring")

    rogueWifiMonitor?.startMonitoring()
    syncCurrentLocationToSubsystems()

    // Collect status updates
    rogueWifiStatusJob = serviceScope.launch {
        rogueWifiMonitor?.wifiStatus?.collect { status ->
            ScanningServiceState.rogueWifiStatus.value = status
            broadcastRogueWifiData()
        }
    }

    // Collect suspicious networks
    suspiciousNetworksJob = serviceScope.launch {
        rogueWifiMonitor?.suspiciousNetworks?.collect { networks ->
            ScanningServiceState.suspiciousNetworks.value = networks
            broadcastRogueWifiData()
        }
    }

    // Collect events
    rogueWifiEventsJob = serviceScope.launch {
        rogueWifiMonitor?.wifiEvents?.collect { events ->
            ScanningServiceState.rogueWifiEvents.value = events
            broadcastRogueWifiData()
        }
    }

    // Collect anomalies and convert to detections
    rogueWifiAnomalyJob = serviceScope.launch {
        rogueWifiMonitor?.anomalies?.collect { anomalies ->
            ScanningServiceState.rogueWifiAnomalies.value = anomalies
            broadcastRogueWifiData()

            for (anomaly in anomalies) {
                // Send broadcast for automation apps
                sendWifiAnomalyBroadcast(anomaly)

                val detection = rogueWifiMonitor?.anomalyToDetection(anomaly)
                detection?.let { det ->
                    // Check if we already have this detection
                    val existing = det.macAddress?.let { repository.getDetectionByMacAddress(it) }
                        ?: det.ssid?.let { repository.getDetectionBySsid(it) }

                    if (existing == null) {
                        try {
                            // Store enriched heuristics data for LLM analysis
                            anomaly.followingAnalysis?.let { analysis ->
                                enrichedDataCache.putWifiFollowing(det.id, analysis)
                                Log.d(TAG, "Stored WiFi following heuristics for detection ${det.id} (confidence: ${analysis.followingConfidence}%)")
                            }

                            insertDetectionWithAnalysis(det)

                            if (anomaly.severity == ThreatLevel.CRITICAL ||
                                anomaly.severity == ThreatLevel.HIGH) {
                                alertUser(det)
                            }

                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()

                            if (BuildConfig.DEBUG) {
                                Log.w(TAG, "WIFI ANOMALY: ${anomaly.type.displayName} - ${anomaly.description}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving WiFi detection: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().
}

internal fun ScanningService.stopRogueWifiMonitoring() {
    suspiciousNetworksJob?.cancel()
    suspiciousNetworksJob = null
    rogueWifiStatusJob?.cancel()
    rogueWifiStatusJob = null
    rogueWifiAnomalyJob?.cancel()
    rogueWifiAnomalyJob = null
    rogueWifiEventsJob?.cancel()
    rogueWifiEventsJob = null
    rogueWifiMonitor?.stopMonitoring()
    Log.d(TAG, "Rogue WiFi monitoring stopped")
}

// ==================== RF Signal Analysis ====================

internal fun ScanningService.startRfSignalAnalysis() {
    if (!currentDetectionSettings.enableRfDetection) {
        Log.d(TAG, "RF signal analysis disabled by settings, skipping")
        return
    }
    if (rfSignalAnalyzer == null) {
        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl).also {
            it.enableHiddenNetworkRfAnomaly = currentDetectionSettings.enableHiddenNetworkRfAnomaly
        }
    }
    Log.d(TAG, "Starting RF signal analysis")

    rfSignalAnalyzer?.startMonitoring()
    syncCurrentLocationToSubsystems()

    // Collect status updates
    rfStatusJob = serviceScope.launch {
        rfSignalAnalyzer?.rfStatus?.collect { status ->
            ScanningServiceState.rfStatus.value = status
            broadcastRfData()
        }
    }

    // Collect events
    rfEventsJob = serviceScope.launch {
        rfSignalAnalyzer?.rfEvents?.collect { events ->
            ScanningServiceState.rfEvents.value = events
            broadcastRfData()
        }
    }

    // Collect detected drones
    rfDronesJob = serviceScope.launch {
        rfSignalAnalyzer?.dronesDetected?.collect { drones ->
            ScanningServiceState.detectedDrones.value = drones
            broadcastRfData()

            // Convert new drones to detections
            for (drone in drones) {
                val detection = rfSignalAnalyzer?.droneToDetection(drone)
                detection?.let { det ->
                    val existing = det.macAddress?.let { repository.getDetectionByMacAddress(it) }
                    if (existing == null) {
                        try {
                            insertDetectionWithAnalysis(det)
                            alertUser(det)
                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()
                            Log.w(TAG, "DRONE DETECTED: ${drone.manufacturer} at ${drone.estimatedDistance}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving drone detection: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    // Collect anomalies and convert to detections
    rfAnomalyJob = serviceScope.launch {
        rfSignalAnalyzer?.anomalies?.collect { anomalies ->
            ScanningServiceState.rfAnomalies.value = anomalies
            broadcastRfData()

            for (anomaly in anomalies) {
                // Send broadcast for automation apps
                sendRfAnomalyBroadcast(anomaly)

                val detection = rfSignalAnalyzer?.anomalyToDetection(anomaly)
                detection?.let { det ->
                    // Use timestamp-based unique ID for RF anomalies
                    val existing = repository.getDetectionBySsid(det.deviceName ?: "")
                    if (existing == null) {
                        try {
                            insertDetectionWithAnalysis(det)

                            if (anomaly.severity == ThreatLevel.CRITICAL ||
                                anomaly.severity == ThreatLevel.HIGH) {
                                alertUser(det)
                            }

                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()

                            if (BuildConfig.DEBUG) {
                                Log.w(TAG, "RF ANOMALY: ${anomaly.type.displayName} - ${anomaly.description}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving RF detection: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().
}

internal fun ScanningService.stopRfSignalAnalysis() {
    rfStatusJob?.cancel()
    rfStatusJob = null
    rfAnomalyJob?.cancel()
    rfAnomalyJob = null
    rfEventsJob?.cancel()
    rfEventsJob = null
    rfDronesJob?.cancel()
    rfDronesJob = null
    rfSignalAnalyzer?.stopMonitoring()
    Log.d(TAG, "RF signal analysis stopped")
}

// ==================== Ultrasonic Detection ====================

internal fun ScanningService.startUltrasonicDetection() {
    // Double-check that user has opted in with consent
    if (!currentPrivacySettings.ultrasonicDetectionEnabled || !currentPrivacySettings.ultrasonicConsentAcknowledged) {
        Log.w(TAG, "Ultrasonic detection not enabled - user must opt-in via Privacy settings")
        return
    }

    if (!hasAudioPermissions()) {
        Log.w(TAG, "Missing audio permissions for ultrasonic detection")
        return
    }

    if (ultrasonicDetector == null) {
        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)
    }
    ultrasonicDetector?.updateScanTiming(
        intervalSeconds = currentScanSettings.ultrasonicScanIntervalSeconds,
        durationSeconds = currentScanSettings.ultrasonicScanDurationSeconds
    )

    Log.d(TAG, "Starting ultrasonic beacon detection (user consented, audio encrypted in memory)")

    ultrasonicDetector?.startMonitoring()
    syncCurrentLocationToSubsystems()

    // Collect status updates
    ultrasonicStatusJob = serviceScope.launch {
        ultrasonicDetector?.status?.collect { status ->
            ScanningServiceState.ultrasonicStatus.value = status
            broadcastUltrasonicData()
        }
    }

    // Collect events
    ultrasonicEventsJob = serviceScope.launch {
        ultrasonicDetector?.events?.collect { events ->
            ScanningServiceState.ultrasonicEvents.value = events
            broadcastUltrasonicData()
        }
    }

    // Collect active beacons
    ultrasonicBeaconsJob = serviceScope.launch {
        ultrasonicDetector?.beaconsDetected?.collect { beacons ->
            ScanningServiceState.ultrasonicBeacons.value = beacons
            broadcastUltrasonicData()
        }
    }

    // Collect anomalies and convert to detections
    ultrasonicAnomalyJob = serviceScope.launch {
        ultrasonicDetector?.anomalies?.collect { anomalies ->
            ScanningServiceState.ultrasonicAnomalies.value = anomalies
            broadcastUltrasonicData()

            for (anomaly in anomalies) {
                // Send broadcast for automation apps
                sendUltrasonicAnomalyBroadcast(anomaly)

                val detection = ultrasonicDetector?.anomalyToDetection(anomaly)
                detection?.let { det ->
                    // Use frequency as unique identifier
                    val existing = det.ssid?.let { repository.getDetectionBySsid(it) }
                    if (existing == null) {
                        try {
                            // Store enriched heuristics data for LLM analysis
                            anomaly.analysis?.let { analysis ->
                                enrichedDataCache.putUltrasonic(det.id, analysis)
                                Log.d(TAG, "Stored ultrasonic heuristics for detection ${det.id} (tracking likelihood: ${analysis.trackingLikelihood}%)")
                            }

                            insertDetectionWithAnalysis(det)

                            if (anomaly.severity == ThreatLevel.CRITICAL ||
                                anomaly.severity == ThreatLevel.HIGH) {
                                alertUser(det)
                            }

                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()

                            Log.w(TAG, "ULTRASONIC: ${anomaly.type.displayName} - ${anomaly.frequency}Hz")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving ultrasonic detection: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().
}

internal fun ScanningService.stopUltrasonicDetection() {
    ultrasonicStatusJob?.cancel()
    ultrasonicStatusJob = null
    ultrasonicAnomalyJob?.cancel()
    ultrasonicAnomalyJob = null
    ultrasonicEventsJob?.cancel()
    ultrasonicEventsJob = null
    ultrasonicBeaconsJob?.cancel()
    ultrasonicBeaconsJob = null
    ultrasonicDetector?.stopMonitoring()
    Log.d(TAG, "Ultrasonic detection stopped")
}

// ==================== GNSS Satellite Monitoring ====================

@SuppressLint("MissingPermission")
internal fun ScanningService.startGnssMonitoring() {
    // Only start GNSS monitoring if enabled in settings
    if (currentDetectionSettings?.enableGnssDetection != true) {
        Log.d(TAG, "GNSS monitoring disabled by settings, skipping")
        ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Disabled
        return
    }

    if (!hasLocationPermissions()) {
        ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.PermissionDenied("ACCESS_FINE_LOCATION")
        Log.w(TAG, "Missing location permissions for GNSS monitoring")
        return
    }

    if (gnssSatelliteMonitor == null) {
        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)
    }
    gnssSatelliteMonitor?.updateScanTiming(currentScanSettings.gnssScanIntervalSeconds)

    Log.d(TAG, "Starting GNSS satellite monitoring for spoofing/jamming detection")
    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Active

    gnssSatelliteMonitor?.startMonitoring()
    syncCurrentLocationToSubsystems()

    // Collect status updates
    gnssStatusJob = serviceScope.launch {
        gnssSatelliteMonitor?.gnssStatus?.collect { status ->
            ScanningServiceState.gnssStatus.value = status
            broadcastGnssData()
        }
    }

    // Collect satellite info
    gnssSatellitesJob = serviceScope.launch {
        gnssSatelliteMonitor?.satellites?.collect { sats ->
            ScanningServiceState.gnssSatellites.value = sats
            broadcastGnssData()
        }
    }

    // Collect events
    gnssEventsJob = serviceScope.launch {
        gnssSatelliteMonitor?.events?.collect { events ->
            ScanningServiceState.gnssEvents.value = events
            broadcastGnssData()
        }
    }

    // Collect raw measurements
    gnssMeasurementsJob = serviceScope.launch {
        gnssSatelliteMonitor?.measurements?.collect { measurements ->
            ScanningServiceState.gnssMeasurements.value = measurements
            broadcastGnssData()
        }
    }

    // Collect anomalies and convert to detections
    // Note: processedGnssAnomalyIds is now a class-level property to persist across restarts

    gnssAnomalyJob = serviceScope.launch {
        gnssSatelliteMonitor?.anomalies?.collect { anomalies ->
            ScanningServiceState.gnssAnomalies.value = anomalies
            broadcastGnssData()

            for (anomaly in anomalies) {
                // Skip if already processed in this session
                if (anomaly.id in processedGnssAnomalyIds) {
                    continue
                }

                // Send broadcast for automation apps
                sendGnssAnomalyBroadcast(anomaly)

                val detection = gnssSatelliteMonitor?.anomalyToDetection(anomaly)
                detection?.let { det ->
                    // Use anomaly ID as unique identifier
                    val detWithId = det.copy(macAddress = anomaly.id)
                    val existing = repository.getDetectionByMacAddress(anomaly.id)
                    if (existing == null) {
                        try {
                            // Store enriched heuristics data for LLM analysis
                            anomaly.analysis?.let { analysis ->
                                enrichedDataCache.putGnss(detWithId.id, analysis)
                                Log.d(TAG, "Stored GNSS heuristics for detection ${detWithId.id} (spoofing: ${analysis.spoofingLikelihood}%, jamming: ${analysis.jammingLikelihood}%)")
                            }

                            insertDetectionWithAnalysis(detWithId)
                            processedGnssAnomalyIds.add(anomaly.id)

                            if (anomaly.severity == ThreatLevel.CRITICAL ||
                                anomaly.severity == ThreatLevel.HIGH) {
                                alertUser(det)
                            }

                            ScanningServiceState.lastDetection.value = det
                            ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                            broadcastLastDetection()
                            broadcastStateToClients()
                            broadcastDetectionRefresh()

                            Log.w(TAG, "GNSS: ${anomaly.type.displayName} - ${anomaly.description}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving GNSS detection: ${e.message}", e)
                        }
                    } else {
                        // Already in database, mark as processed
                        processedGnssAnomalyIds.add(anomaly.id)
                    }
                }
            }
        }
    }

    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().
}

internal fun ScanningService.stopGnssMonitoring() {
    gnssStatusJob?.cancel()
    gnssStatusJob = null
    gnssAnomalyJob?.cancel()
    gnssAnomalyJob = null
    gnssEventsJob?.cancel()
    gnssEventsJob = null
    gnssSatellitesJob?.cancel()
    gnssSatellitesJob = null
    gnssMeasurementsJob?.cancel()
    gnssMeasurementsJob = null
    gnssSatelliteMonitor?.stopMonitoring()
    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Idle
    Log.d(TAG, "GNSS monitoring stopped")
}

// ==================== Satellite Connection Notifications ====================

internal fun ScanningService.sendSatelliteConnectionNotification(state: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState) {
    val providerName = when (state.provider) {
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.STARLINK -> "Starlink"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.SKYLO -> "Skylo"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.GLOBALSTAR -> "Globalstar"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.AST_SPACEMOBILE -> "AST SpaceMobile"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.LYNK -> "Lynk"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.IRIDIUM -> "Iridium"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.INMARSAT -> "Inmarsat"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteProvider.UNKNOWN -> state.networkName ?: "Unknown"
    }
    val connectionTypeName = when (state.connectionType) {
        com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionType.T_MOBILE_STARLINK -> "T-Mobile Satellite"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionType.SKYLO_NTN -> "Skylo NTN"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionType.GENERIC_NTN -> "NTN"
        com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionType.PROPRIETARY -> "Proprietary Satellite"
        else -> "Satellite"
    }

    val pendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, ScanningService.CHANNEL_ID)
        .setContentTitle("Satellite Connection Active")
        .setContentText("Connected to $providerName via $connectionTypeName")
        .setSmallIcon(R.drawable.ic_radar)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOngoing(true)
        .build()

    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(ScanningService.SATELLITE_CONNECTION_NOTIF_ID, notification)
}

internal fun ScanningService.cancelSatelliteConnectionNotification() {
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.cancel(ScanningService.SATELLITE_CONNECTION_NOTIF_ID)
}

// ==================== Permission Checks ====================

internal fun ScanningService.hasTelephonyPermissions(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun ScanningService.hasAudioPermissions(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

// ==================== Shannon SDM Diagnostic Monitoring (OEM Only) ====================

internal fun ScanningService.startShannonDiagMonitoring() {
    // Guard: feature flag + runtime capability
    if (!com.flockyou.config.OemFeatureFlags.SHANNON_DIAG_ENABLED) {
        return
    }

    val capability = com.flockyou.shannon.ShannonCapabilityDetector.detect()
    if (capability != com.flockyou.shannon.ShannonCapabilityDetector.ShannonStatus.AVAILABLE) {
        ScanningServiceState.shannonDiagStatus.value = SubsystemStatus.Error(
            -1, "Shannon: ${capability.displayName}"
        )
        Log.d(TAG, "Shannon diagnostics not available: ${capability.displayName}")
        return
    }

    shannonDiagMonitor?.startMonitoring()
    ScanningServiceState.shannonDiagStatus.value = SubsystemStatus.Active
    broadcastSubsystemStatus()
    Log.i(TAG, "Shannon SDM diagnostic monitoring started")

    // Collect Shannon status updates
    shannonStatusJob = serviceScope.launch {
        shannonDiagMonitor?.status?.collect { status ->
            ScanningServiceState.shannonDiagStatus.value = when (status) {
                com.flockyou.shannon.ShannonDiagStatus.ACTIVE -> SubsystemStatus.Active
                com.flockyou.shannon.ShannonDiagStatus.STARTING -> SubsystemStatus.Active
                com.flockyou.shannon.ShannonDiagStatus.RECONNECTING -> SubsystemStatus.Active
                com.flockyou.shannon.ShannonDiagStatus.IDLE -> SubsystemStatus.Idle
                com.flockyou.shannon.ShannonDiagStatus.UNAVAILABLE -> SubsystemStatus.Error(-1, "Unavailable")
                com.flockyou.shannon.ShannonDiagStatus.ERROR -> SubsystemStatus.Error(-2, "Error")
            }
            broadcastSubsystemStatus()
        }
    }

    // Collect Shannon anomalies and convert to detections
    shannonAnomalyJob = serviceScope.launch {
        shannonDiagMonitor?.anomalies?.collect { anomalies ->
            ScanningServiceState.shannonAnomalies.value = anomalies

            for (anomaly in anomalies) {
                // Skip if already processed
                if (anomaly.id in processedShannonAnomalyIds) continue

                // Send broadcast for automation apps
                broadcastShannonAnomaly(anomaly)

                // Convert to detection via handler
                val detection = cellularDetectionHandler.convertShannonAnomalyToDetection(
                    anomaly = anomaly,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude
                )
                detection?.let { det ->
                    try {
                        insertDetectionWithAnalysis(det)
                        processedShannonAnomalyIds.add(anomaly.id)

                        // Alert for CRITICAL/HIGH severity
                        if (det.threatLevel == com.flockyou.data.model.ThreatLevel.CRITICAL ||
                            det.threatLevel == com.flockyou.data.model.ThreatLevel.HIGH) {
                            alertUser(det)
                        }

                        ScanningServiceState.lastDetection.value = det
                        ScanningServiceState.detectionCount.value = repository.getTotalDetectionCount()
                        broadcastLastDetection()
                        broadcastStateToClients()
                        broadcastDetectionRefresh()

                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "SHANNON SDM ANOMALY: ${anomaly.type.displayName} - ${anomaly.description}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving Shannon detection: ${e.message}", e)
                    }
                }
            }
        }
    }
}

internal fun ScanningService.stopShannonDiagMonitoring() {
    shannonStatusJob?.cancel()
    shannonStatusJob = null
    shannonAnomalyJob?.cancel()
    shannonAnomalyJob = null
    shannonDiagMonitor?.stopMonitoring()
    ScanningServiceState.shannonDiagStatus.value = SubsystemStatus.Idle
    Log.d(TAG, "Shannon SDM diagnostic monitoring stopped")
}

internal fun ScanningService.hasLocationPermissions(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun ScanningService.hasBluetoothPermissions(): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
}
