package com.flockyou.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.flockyou.BuildConfig
import com.flockyou.MainActivity
import com.flockyou.R
import com.flockyou.ui.EmergencyAlertActivity
import com.flockyou.data.model.*
import com.flockyou.detection.handler.BleDetectionContext
import com.flockyou.detection.handler.BleDetectionResult
import com.flockyou.worker.BackgroundAnalysisWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Detection result handling and database storage for ScanningService.
 *
 * Contains:
 * - Detection processing pipeline (handleDetection, applyPrivacySettings)
 * - User alerts (alertUser, showEmergencyPopup, sendDetectionNotification)
 * - Automation broadcasts (sendDetectionBroadcast, send*AnomalyBroadcast)
 * - Handler delegation methods (processBleWithHandler, processWifiWithHandler, etc.)
 * - LLM warm-up and analysis triggers
 * - Cross-domain correlation alerts
 * - Database error handling
 *
 * All functions here are extension functions on [ScanningService].
 *
 * Related files:
 * - ScanningService.kt: Service lifecycle, core scan loop
 * - ScanningServiceBroadcaster.kt: IPC broadcast methods
 * - SubsystemManager.kt: Subsystem start/stop logic
 */

private const val TAG = "ScanningService"

// ==================== Detection Handling ====================

/**
 * Apply privacy settings to a detection before storing.
 * - Strips location data if storeLocationWithDetections is disabled (Priority 4)
 */
internal fun ScanningService.applyPrivacySettings(detection: Detection): Detection {
    return if (!currentPrivacySettings.storeLocationWithDetections) {
        // Strip location data for privacy
        detection.copy(latitude = null, longitude = null)
    } else {
        detection
    }
}

internal suspend fun ScanningService.handleDetection(detection: Detection) {
    // Check if database is available (might be unavailable during nuke)
    if (!ScanningServiceState.isDatabaseAvailable.value) {
        Log.w(TAG, "Database unavailable - skipping detection save")
        return
    }

    try {
        // Apply privacy settings (strip location if disabled)
        val privacyAwareDetection = applyPrivacySettings(detection)

        // Run quick false positive check before storage
        val detectionWithFp = try {
            val quickFpResult = falsePositiveAnalyzer.quickRuleBasedCheck(privacyAwareDetection)
            privacyAwareDetection.copy(
                fpScore = quickFpResult.confidence,
                fpReason = quickFpResult.primaryReason,
                fpCategory = quickFpResult.category?.name,
                analyzedAt = System.currentTimeMillis(),
                llmAnalyzed = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "FP analysis failed, proceeding without FP score: ${e.message}")
            privacyAwareDetection
        }

        // Choose storage based on ephemeral mode (Priority 1)
        val isNew = if (currentPrivacySettings.ephemeralModeEnabled) {
            // Ephemeral mode: store in RAM only
            ephemeralRepository.upsertDetection(detectionWithFp)
        } else {
            // Normal mode: persist to encrypted database
            repository.upsertDetection(detectionWithFp)
        }

        if (isNew) {
            // New detection
            ScanningServiceState.detectionCount.value++
            ScanningServiceState.scanStats.value = ScanningServiceState.scanStats.value.copy(
                detectionsCreated = ScanningServiceState.scanStats.value.detectionsCreated + 1
            )
            ScanningServiceState.lastDetection.value = detectionWithFp
            broadcastLastDetection()
            broadcastStateToClients()

            // Register with cross-domain analyzer for correlation analysis
            try {
                crossDomainAnalyzer.registerDetection(detectionWithFp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register detection with correlation analyzer: ${e.message}")
            }

            Log.d(TAG, "New detection: ${detectionWithFp.deviceType} - ${detectionWithFp.macAddress ?: detectionWithFp.ssid} (FP score: ${detectionWithFp.fpScore ?: "N/A"})")

            // Expensive per-detection analysis is opt-in. AI disabled means no
            // WorkManager/foreground-process churn from the detection hot path.
            if (!currentPrivacySettings.ephemeralModeEnabled) {
                val aiSettings = aiSettingsRepository.settings.first()
                if (aiSettings.enabled && aiSettings.autoAnalyzeNewDetections) {
                    try {
                        BackgroundAnalysisWorker.triggerForDetections(
                            this@handleDetection,
                            listOf(detectionWithFp.id)
                        )
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Queued detection for LLM analysis: ${detectionWithFp.id}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to queue detection for LLM analysis: ${e.message}")
                    }
                }
            }

            // Only alert user if FP score is below HIGH_CONFIDENCE_FP_THRESHOLD (0.8f)
            // High FP confidence means it's likely a false positive - suppress the alert
            val fpScore = detectionWithFp.fpScore
            if (fpScore == null || fpScore < com.flockyou.ai.FalsePositiveAnalyzer.HIGH_CONFIDENCE_FP_THRESHOLD) {
                alertUser(detectionWithFp)
            } else {
                Log.i(TAG, "Alert suppressed due to high FP confidence: ${detectionWithFp.deviceType} - " +
                        "${detectionWithFp.macAddress ?: detectionWithFp.ssid} " +
                        "(FP score: $fpScore, reason: ${detectionWithFp.fpReason})")
            }
        } else {
            // Existing detection - update lastDetection to refresh UI
            ScanningServiceState.lastDetection.value = detectionWithFp
            broadcastLastDetection()
            Log.d(TAG, "Updated detection: ${detectionWithFp.deviceType} - ${detectionWithFp.macAddress ?: detectionWithFp.ssid}")
        }

        // Emit refresh event to ensure UI updates even if Room Flow doesn't trigger
        broadcastDetectionRefresh()
    } catch (e: android.database.sqlite.SQLiteException) {
        // Database error - likely corrupted or wiped
        Log.e(TAG, "SQLite error handling detection: ${e.message}", e)
        handleDatabaseError(e)
    } catch (e: Exception) {
        // Check for wrapped database errors
        if (isDatabaseError(e)) {
            Log.e(TAG, "Database error handling detection: ${e.message}", e)
            handleDatabaseError(e)
        } else {
            Log.e(TAG, "Error handling detection: ${e.message}", e)
            logError("Detection", 1001, "Failed to save detection: ${e.message}")
        }
    }
}

/**
 * Check if an exception is a database-related error.
 */
internal fun isDatabaseError(e: Exception): Boolean {
    val message = e.message?.lowercase() ?: ""
    return message.contains("database") ||
            message.contains("sqlite") ||
            message.contains("sqlcipher") ||
            message.contains("file is not a database") ||
            message.contains("out of memory") ||
            e.cause is android.database.sqlite.SQLiteException
}

/**
 * Handle database errors gracefully.
 * This typically happens when the database is wiped during a nuke operation.
 */
internal fun ScanningService.handleDatabaseError(e: Exception) {
    // Mark database as unavailable
    ScanningServiceState.isDatabaseAvailable.value = false

    // Log the error
    logError("Database", 26, "Database error: ${e.message}", recoverable = false)

    // Switch to ephemeral mode to continue operation without persistent storage
    Log.w(TAG, "Switching to ephemeral storage due to database error")

    // Update scan status to indicate degraded operation
    ScanningServiceState.scanStatus.value = ScanStatus.Error("Database unavailable - using memory storage", recoverable = true)
}

/**
 * Insert a detection and trigger immediate LLM analysis.
 * This ensures detections are enriched as they come in rather than waiting for batch processing.
 */
internal suspend fun ScanningService.insertDetectionWithAnalysis(detection: Detection) {
    repository.insertDetection(detection)

    val aiSettings = aiSettingsRepository.settings.first()
    if (aiSettings.enabled && aiSettings.autoAnalyzeNewDetections) {
        try {
            BackgroundAnalysisWorker.triggerForDetections(
                this@insertDetectionWithAnalysis,
                listOf(detection.id)
            )
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Triggered immediate LLM analysis for detection: ${detection.id}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger immediate LLM analysis: ${e.message}")
        }
    }
}

// ==================== User Alerts ====================

internal fun ScanningService.alertUser(detection: Detection) {
    val notifSettings = currentNotificationSettings

    // Check if we should show emergency popup for CRITICAL threats
    if (detection.threatLevel == ThreatLevel.CRITICAL &&
        notifSettings.emergencyPopupEnabled &&
        Settings.canDrawOverlays(this)
    ) {
        showEmergencyPopup(detection)
        // Emergency popup handles its own sound and vibration, skip regular alert
        sendDetectionBroadcast(detection)
        return
    }

    // Vibrate based on threat level
    val pattern = when (detection.threatLevel) {
        ThreatLevel.CRITICAL -> longArrayOf(0, 200, 100, 200, 100, 200)
        ThreatLevel.HIGH -> longArrayOf(0, 150, 100, 150, 100, 150)
        ThreatLevel.MEDIUM -> longArrayOf(0, 100, 100, 100)
        else -> longArrayOf(0, 100, 100)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }

    // Send notification
    sendDetectionNotification(detection)

    // Send broadcast for automation apps
    sendDetectionBroadcast(detection)
}

/**
 * Shows a full-screen CMAS/WEA-style emergency alert popup for critical threats.
 * This displays above the lock screen and plays an alarm sound.
 */
internal fun ScanningService.showEmergencyPopup(detection: Detection) {
    Log.w(TAG, "Showing emergency popup for CRITICAL detection: ${detection.deviceType}")

    val notifSettings = currentNotificationSettings

    val title = "SURVEILLANCE ALERT"
    val message = buildString {
        append("A ")
        append(detection.deviceType.name.replace("_", " "))
        append(" has been detected in your vicinity.\n\n")
        if (!detection.deviceName.isNullOrBlank()) {
            append("Device: ${detection.deviceName}\n")
        }
        if (!detection.ssid.isNullOrBlank()) {
            append("Network: ${detection.ssid}\n")
        }
        append("\nTake appropriate security measures.")
    }

    val intent = EmergencyAlertActivity.createIntent(
        context = this,
        title = title,
        message = message,
        deviceType = detection.deviceType.name.replace("_", " "),
        threatLevel = detection.threatLevel,
        detectionId = detection.id,
        playSound = notifSettings.sound,
        vibrate = notifSettings.vibrate
    )

    startActivity(intent)
}

internal fun ScanningService.sendDetectionNotification(detection: Detection) {
    val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, ScanningService.CHANNEL_ID)
        .setContentTitle("\u26A0\uFE0F Surveillance Device Detected!")
        .setContentText("${detection.deviceType.name.replace("_", " ")} - ${detection.threatLevel}")
        .setSmallIcon(R.drawable.ic_warning)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(detection.id.hashCode(), notification)
}

// ==================== Automation Broadcasts ====================

/**
 * Send a broadcast for a detection event to automation apps (Tasker, Automate, etc.)
 */
internal fun ScanningService.sendDetectionBroadcast(detection: Detection) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnDetection) return

    // Check minimum threat level
    if (!meetsMinThreatLevel(detection.threatLevel, settings.minThreatLevel)) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_DETECTION).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_DETECTION_ID, detection.id)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_DEVICE_TYPE, detection.deviceType.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_DEVICE_NAME, detection.deviceName)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_MAC_ADDRESS, detection.macAddress)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_SSID, detection.ssid)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, detection.threatLevel.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_SCORE, detection.threatScore)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_PROTOCOL, detection.protocol.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_DETECTION_METHOD, detection.detectionMethod.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_SIGNAL_STRENGTH, detection.signalStrength.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_RSSI, detection.rssi)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, detection.timestamp)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_MANUFACTURER, detection.manufacturer)

        if (settings.includeLocation) {
            detection.latitude?.let { putExtra(com.flockyou.data.BroadcastSettings.EXTRA_LATITUDE, it) }
            detection.longitude?.let { putExtra(com.flockyou.data.BroadcastSettings.EXTRA_LONGITUDE, it) }
        }

        // Allow explicit receivers
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_DETECTION} for ${detection.deviceType}")
}

/**
 * Send a broadcast for cellular anomaly events
 */
internal fun ScanningService.sendCellularAnomalyBroadcast(anomaly: CellularMonitor.CellularAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnCellularAnomaly) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_CELLULAR_ANOMALY).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_CELLULAR_ANOMALY}")
}

/**
 * Send a broadcast for Shannon SDM anomaly events.
 * Reuses the broadcastOnCellularAnomaly settings gate since SDM anomalies
 * are cellular-layer detections.
 */
internal fun ScanningService.broadcastShannonAnomaly(anomaly: com.flockyou.shannon.ShannonAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnCellularAnomaly) return

    val intent = Intent("com.flockyou.SHANNON_SDM_ANOMALY").apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        putExtra("confidence", anomaly.confidence)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: com.flockyou.SHANNON_SDM_ANOMALY (${anomaly.type.name})")
}

/**
 * Send a broadcast for satellite anomaly events
 */
internal fun ScanningService.sendSatelliteAnomalyBroadcast(anomaly: com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnSatelliteAnomaly) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_SATELLITE_ANOMALY).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_SATELLITE_ANOMALY}")
}

/**
 * Send a broadcast for WiFi anomaly events
 */
internal fun ScanningService.sendWifiAnomalyBroadcast(anomaly: RogueWifiMonitor.WifiAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnWifiAnomaly) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_WIFI_ANOMALY).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_SSID, anomaly.ssid)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_MAC_ADDRESS, anomaly.bssid)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_WIFI_ANOMALY}")
}

/**
 * Send a broadcast for RF anomaly events
 */
internal fun ScanningService.sendRfAnomalyBroadcast(anomaly: RfSignalAnalyzer.RfAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnRfAnomaly) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_RF_ANOMALY).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_RF_ANOMALY}")
}

/**
 * Send a broadcast for ultrasonic anomaly events
 */
internal fun ScanningService.sendUltrasonicAnomalyBroadcast(anomaly: UltrasonicDetector.UltrasonicAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnUltrasonic) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_ULTRASONIC).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_ULTRASONIC}")
}

/**
 * Send a broadcast for GNSS anomaly events (spoofing/jamming detection)
 */
internal fun ScanningService.sendGnssAnomalyBroadcast(anomaly: com.flockyou.monitoring.GnssSatelliteMonitor.GnssAnomaly) {
    val settings = currentBroadcastSettings
    if (!settings.enabled || !settings.broadcastOnGnssAnomaly) return

    val intent = Intent(com.flockyou.data.BroadcastSettings.ACTION_GNSS_ANOMALY).apply {
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_TYPE, anomaly.type.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_ANOMALY_DESCRIPTION, anomaly.description)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_THREAT_LEVEL, anomaly.severity.name)
        putExtra(com.flockyou.data.BroadcastSettings.EXTRA_TIMESTAMP, anomaly.timestamp)
        putExtra("technical_details", anomaly.technicalDetails)
        putExtra("affected_constellations", anomaly.affectedConstellations.joinToString(",") { it.code })
        putExtra("confidence", anomaly.confidence.name)
        if (settings.includeLocation) {
            anomaly.latitude?.let { putExtra(com.flockyou.data.BroadcastSettings.EXTRA_LATITUDE, it) }
            anomaly.longitude?.let { putExtra(com.flockyou.data.BroadcastSettings.EXTRA_LONGITUDE, it) }
        }
        setPackage(null)
    }

    sendBroadcast(intent)
    Log.d(TAG, "Broadcast sent: ${com.flockyou.data.BroadcastSettings.ACTION_GNSS_ANOMALY}")
}

/**
 * Check if a threat level meets the minimum threshold
 */
internal fun meetsMinThreatLevel(actual: ThreatLevel, minimum: String): Boolean {
    val levels = listOf("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL")
    val actualIndex = levels.indexOf(actual.name)
    val minIndex = levels.indexOf(minimum)
    return actualIndex >= minIndex
}

// ==================== Detection Handler Delegation ====================
//
// These helper methods delegate detection analysis to the standardized
// DetectionHandler system. This allows:
// - Consistent detection logic across all protocols
// - Better testability and separation of concerns
// - Extensibility through the DetectionRegistry
// - AI prompt generation for detected devices

/**
 * Process a BLE scan result using the BleDetectionHandler.
 *
 * Converts the ScanResult to a BleDetectionContext and delegates to the
 * handler for pattern matching and detection generation.
 *
 * @param scanResult The raw BLE scan result from Android
 * @return BleDetectionResult if a detection was made, null otherwise
 */
@SuppressLint("MissingPermission")
internal fun ScanningService.processBleWithHandler(scanResult: android.bluetooth.le.ScanResult): BleDetectionResult? {
    val device = scanResult.device
    val macAddress = device.address ?: return null
    val deviceName = device.name
    val rssi = scanResult.rssi

    // Extract service UUIDs
    val serviceUuids = scanResult.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()

    // Extract manufacturer data
    val manufacturerData = mutableMapOf<Int, String>()
    scanResult.scanRecord?.manufacturerSpecificData?.let { data ->
        for (i in 0 until data.size()) {
            val key = data.keyAt(i)
            val value = data.valueAt(i)
            manufacturerData[key] = value.joinToString("") { "%02X".format(it) }
        }
    }

    // Track packet rate for Signal trigger spike detection
    val advertisingRate = ScanningServiceState.trackPacket(macAddress)

    // Build the detection context
    val context = BleDetectionContext(
        macAddress = macAddress,
        deviceName = deviceName,
        rssi = rssi,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        advertisingRate = advertisingRate,
        timestamp = System.currentTimeMillis(),
        latitude = currentLocation?.latitude,
        longitude = currentLocation?.longitude
    )

    // Delegate to the handler
    return bleDetectionHandler.handleDetection(context)
}

/**
 * Process WiFi scan results using the RogueWifiMonitor and RfSignalAnalyzer.
 *
 * This method handles the legacy RogueWifiMonitor integration for advanced
 * detection (evil twins, deauth attacks, following networks). The primary
 * SSID/MAC pattern matching is now handled by WifiDetectionHandler.processData()
 * which is called separately in processWifiScanResults().
 *
 * @param scanResults The list of WiFi scan results
 */
internal fun ScanningService.processWifiWithHandler(scanResults: List<android.net.wifi.ScanResult>) {
    // Feed results to Rogue WiFi Monitor for evil twin/rogue AP detection
    // Note: WifiDetectionHandler now also integrates with RogueWifiMonitor
    // but we keep this for backward compatibility with existing rogueWifiMonitor instance
    rogueWifiMonitor?.processScanResults(scanResults)

    // Feed results to RF Signal Analyzer for spectrum analysis (only if RF detection enabled)
    if (currentDetectionSettings?.enableRfDetection == true) {
        rfSignalAnalyzer?.analyzeWifiScan(scanResults)
    }
}

/**
 * Process a cellular anomaly using the CellularDetectionHandler.
 *
 * Converts the CellularAnomaly to a detection using the handler's
 * analysis logic and settings-based filtering.
 *
 * @param anomaly The cellular anomaly from CellularMonitor
 * @return Detection if the anomaly warrants a detection, null otherwise
 */
internal suspend fun ScanningService.processCellularWithHandler(
    anomaly: CellularMonitor.CellularAnomaly
): Detection? {
    return cellularDetectionHandler.convertAnomalyToDetection(anomaly)
}

/**
 * Process a satellite anomaly using the SatelliteDetectionHandler.
 *
 * Converts the SatelliteAnomaly to a detection with appropriate threat
 * levels and NTN-specific analysis.
 *
 * @param anomaly The satellite anomaly from SatelliteMonitor
 * @param connectionState Current satellite connection state
 * @return Detection if the anomaly meets severity thresholds, null otherwise
 */
internal suspend fun ScanningService.processSatelliteWithHandler(
    anomaly: com.flockyou.monitoring.SatelliteMonitor.SatelliteAnomaly,
    connectionState: com.flockyou.monitoring.SatelliteMonitor.SatelliteConnectionState
): Detection? {
    return satelliteDetectionHandler.handleAnomaly(
        anomaly = anomaly,
        connectionState = connectionState,
        latitude = currentLocation?.latitude,
        longitude = currentLocation?.longitude
    )
}

// ==================== LLM Warm-up ====================

/**
 * Warm up the LLM engine in the background.
 * This pre-initializes the engine and runs a simple test inference to ensure
 * the first real FP analysis is fast.
 *
 * Runs asynchronously in serviceScope so it doesn't block scanning startup.
 * Failures are logged but don't crash the service - we fall back to rule-based.
 */
internal fun ScanningService.warmUpLlmEngine() {
    serviceScope.launch {
        try {
            val aiSettings = aiSettingsRepository.settings.first()
            if (!aiSettings.enabled || !aiSettings.autoAnalyzeNewDetections) {
                Log.d(TAG, "LLM warm-up skipped - AI auto-analysis disabled")
                return@launch
            }

            Log.i(TAG, "Starting LLM warm-up in background...")
            val startTime = System.currentTimeMillis()
            val engineManager = llmEngineManager.get()

            // Check if LLM engine is available
            val activeEngine = engineManager.activeEngine.value
            if (activeEngine == com.flockyou.ai.LlmEngine.RULE_BASED) {
                Log.d(TAG, "LLM warm-up skipped - using rule-based engine only")
                return@launch
            }

            // Run a simple test prompt to warm up the model
            // This forces the model to load into memory and JIT compile
            val warmupPrompt = "Classify: Is a device named 'TestDevice' a surveillance device? Answer YES or NO."
            val response = engineManager.generateResponse(warmupPrompt)

            val elapsed = System.currentTimeMillis() - startTime
            if (response != null) {
                Log.i(TAG, "LLM warm-up completed in ${elapsed}ms (engine: ${engineManager.activeEngine.value})")
            } else {
                Log.w(TAG, "LLM warm-up completed but returned null response (engine may fall back to rule-based)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM warm-up failed (will use rule-based FP analysis): ${e.message}")
            // Don't rethrow - warm-up failure is not critical
        }
    }
}

// ==================== Cross-Domain Correlation Alerts ====================

/**
 * Alert user of a critical correlated threat.
 */
internal fun ScanningService.alertUserOfCorrelation(correlation: com.flockyou.ai.correlation.CorrelatedThreat) {
    // Create an alert notification for critical correlations
    if (!currentNotificationSettings.enabled || !currentNotificationSettings.criticalAlertsEnabled) return

    try {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Create high-priority alert channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                "flockyou_correlation_alert",
                "Correlation Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical cross-domain surveillance correlation alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("correlation_id", correlation.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, correlation.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "flockyou_correlation_alert")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("CRITICAL: ${correlation.correlationType.displayName}")
            .setContentText(correlation.primaryThreatIndicator)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${correlation.primaryThreatIndicator}\n\n" +
                "Domains: ${correlation.involvedDomains.joinToString(", ")}\n" +
                "Confidence: ${(correlation.correlationScore * 100).toInt()}%\n\n" +
                "Tap for details and recommendations."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(correlation.id.hashCode(), notification)
        Log.i(TAG, "Sent correlation alert notification: ${correlation.correlationType.displayName}")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send correlation alert: ${e.message}", e)
    }
}
