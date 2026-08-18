#!/usr/bin/env python3
"""One-shot, assertion-guarded MAXSTATS R4 Syn runtime patch.

This script is intentionally temporary. Every replacement asserts the exact
pinned/source-derived shape before editing so a drifted branch fails closed.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text()


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text)


def replace_once(rel: str, old: str, new: str) -> None:
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected exactly one occurrence, found {count}: {old[:80]!r}")
    write(rel, text.replace(old, new, 1))


def replace_between(rel: str, start: str, end: str, replacement: str) -> None:
    text = read(rel)
    if text.count(start) != 1:
        raise RuntimeError(f"{rel}: start marker count != 1: {start!r}")
    if text.count(end) != 1:
        raise RuntimeError(f"{rel}: end marker count != 1: {end!r}")
    i = text.index(start)
    j = text.index(end, i)
    if j <= i:
        raise RuntimeError(f"{rel}: invalid marker order")
    write(rel, text[:i] + replacement + text[j:])


# ---------------------------------------------------------------------------
# Pure runtime policy: conservative before settings admission; PERFORMANCE
# remains available only after persisted ScanSettings have been admitted.
# ---------------------------------------------------------------------------
policy_path = ROOT / "app/src/main/java/com/flockyou/service/ScanningRuntimePolicy.kt"
if policy_path.exists():
    raise RuntimeError(f"{policy_path}: already exists")
policy_path.write_text('''package com.flockyou.service

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
''')

replace_once(
    "app/src/main/java/com/flockyou/service/ScanningServiceModels.kt",
    "    val aggressiveBleMode: Boolean = true\n",
    "    val aggressiveBleMode: Boolean = false\n",
)

replace_once(
    "app/src/main/java/com/flockyou/service/ScanningServiceState.kt",
    "            enableCellular = enableCellular,\n            trackSeenDevices = trackSeenDevices\n",
    "            enableCellular = enableCellular,\n            trackSeenDevices = trackSeenDevices,\n            // This API represents an admitted operator configuration. The\n            // effective battery mode still gates actual LOW_LATENCY BLE.\n            aggressiveBleMode = true\n",
)

# ---------------------------------------------------------------------------
# Restart liveness must execute in the same :scanning process as process-local
# ScanningServiceState. Otherwise isScanning is necessarily the wrong instance.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''        <receiver
            android:name=".service.ServiceRestartReceiver"
            android:enabled="true"
            android:exported="false">''',
    '''        <receiver
            android:name=".service.ServiceRestartReceiver"
            android:enabled="true"
            android:exported="false"
            android:process=":scanning">''',
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''        <service
            android:name=".service.ServiceRestartJobService"
            android:enabled="true"
            android:exported="false"
            android:permission="android.permission.BIND_JOB_SERVICE" />''',
    '''        <service
            android:name=".service.ServiceRestartJobService"
            android:enabled="true"
            android:exported="false"
            android:process=":scanning"
            android:permission="android.permission.BIND_JOB_SERVICE" />''',
)

# Retire the recurring one-minute exact heartbeat. Existing scheduled heartbeat
# intents can fire once after an upgrade, but are intentionally not rescheduled.
replace_once(
    "app/src/main/java/com/flockyou/service/ServiceRestartReceiver.kt",
    "        private const val HEARTBEAT_INTERVAL_MS = 60 * 1000L // 1 minute heartbeat\n",
    "",
)
replace_between(
    "app/src/main/java/com/flockyou/service/ServiceRestartReceiver.kt",
    '''        /**
         * Schedule a heartbeat alarm - more frequent check
         */
        fun scheduleHeartbeat(context: Context) {''',
    '''        /**
         * Record service heartbeat - call this from ScanningService periodically
         */''',
    "",
)
replace_once(
    "app/src/main/java/com/flockyou/service/ServiceRestartReceiver.kt",
    '''                // Schedule next heartbeat
                scheduleHeartbeat(context)
''',
    '''                // Do not reschedule this legacy heartbeat. The in-process scanner
                // records liveness every minute; the five-minute inexact watchdog and
                // JobScheduler backup consume that signal without a one-minute wake alarm.
''',
)

# ---------------------------------------------------------------------------
# ScanningService lifecycle, settings admission, recovery, and BLE policy.
# ---------------------------------------------------------------------------
svc = "app/src/main/java/com/flockyou/service/ScanningService.kt"

replace_once(
    svc,
    "        private const val HEARTBEAT_RECORD_INTERVAL_MS = 60_000L\n",
    "        private const val HEARTBEAT_RECORD_INTERVAL_MS = 60_000L\n        private const val SETTINGS_ADMISSION_TIMEOUT_MS = 10_000L\n",
)

replace_once(
    svc,
    "    // Scan job\n    private var scanJob: Job? = null\n",
    '''    // Scan lifecycle jobs
    private var scanJob: Job? = null
    private var startupJob: Job? = null
    private val detectorRestartJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
''',
)

replace_once(
    svc,
    '''                    ScanningServiceIpc.MSG_STOP_SCANNING -> {
                        if (isScanning.value) {
                            stopScanning()
                        }
                    }
''',
    '''                    ScanningServiceIpc.MSG_STOP_SCANNING -> {
                        if (isScanning.value || startupJob?.isActive == true) {
                            stopScanning()
                        }
                    }
''',
)

start_wrapper = '''    private fun applyScanSettings(settings: com.flockyou.data.ScanSettings) {
        Log.d(TAG, "Scan settings updated - applying to detectors")
        currentScanSettings = settings

        ultrasonicDetector?.updateScanTiming(
            intervalSeconds = settings.ultrasonicScanIntervalSeconds,
            durationSeconds = settings.ultrasonicScanDurationSeconds
        )
        gnssSatelliteMonitor?.updateScanTiming(settings.gnssScanIntervalSeconds)
        satelliteMonitor?.updateScanTiming(settings.satelliteScanIntervalSeconds)
        cellularMonitor?.updateScanTiming(settings.cellularScanIntervalSeconds)

        currentSettings.value = ScanningRuntimePolicy.toRuntimeScanConfig(settings)
        updateEffectiveBatteryMode()
    }

    private fun applyDetectionSettings(settings: com.flockyou.data.DetectionSettings) {
        currentDetectionSettings = settings
        rfSignalAnalyzer?.enableHiddenNetworkRfAnomaly = settings.enableHiddenNetworkRfAnomaly
        rogueWifiMonitor?.minTrackingDistanceMeters = settings.wifiThresholds.minTrackingDistanceMeters
        Log.d(
            TAG,
            "Detection settings updated - hidden network RF anomaly: ${settings.enableHiddenNetworkRfAnomaly}, " +
                "min tracking distance: ${settings.wifiThresholds.minTrackingDistanceMeters}m"
        )
    }

    private fun stopSettingsCollectionJobs() {
        broadcastSettingsJob?.cancel()
        broadcastSettingsJob = null
        privacySettingsJob?.cancel()
        privacySettingsJob = null
        scanSettingsJob?.cancel()
        scanSettingsJob = null
        notificationSettingsJob?.cancel()
        notificationSettingsJob = null
        detectionSettingsJob?.cancel()
        detectionSettingsJob = null
    }

    private fun startSettingsCollectionJobs() {
        stopSettingsCollectionJobs()

        broadcastSettingsJob = serviceScope.launch {
            broadcastSettingsRepository.settings.collect { settings ->
                currentBroadcastSettings = settings
            }
        }

        privacySettingsJob = serviceScope.launch {
            privacySettingsRepository.settings.collect { settings ->
                val previous = currentPrivacySettings
                val wasUltrasonicEnabled = previous.ultrasonicDetectionEnabled &&
                    previous.ultrasonicConsentAcknowledged
                val isUltrasonicEnabled = settings.ultrasonicDetectionEnabled &&
                    settings.ultrasonicConsentAcknowledged
                val ephemeralJustEnabled = settings.ephemeralModeEnabled && !previous.ephemeralModeEnabled

                currentPrivacySettings = settings

                if (ephemeralJustEnabled) {
                    ephemeralRepository.clearAll()
                    enrichedDataCache.clear()
                    Log.d(TAG, "Ephemeral mode enabled - cleared in-memory analysis state")
                }
                cellularMonitor?.setEphemeralMode(settings.ephemeralModeEnabled)

                if (isUltrasonicEnabled != wasUltrasonicEnabled) {
                    if (isUltrasonicEnabled) {
                        Log.i(TAG, "Ultrasonic detection enabled by admitted privacy settings")
                        startUltrasonicDetection()
                    } else {
                        Log.i(TAG, "Ultrasonic detection disabled by privacy settings")
                        stopUltrasonicDetection()
                    }
                }
            }
        }

        scanSettingsJob = serviceScope.launch {
            scanSettingsRepository.settings.collect(::applyScanSettings)
        }

        notificationSettingsJob = serviceScope.launch {
            notificationSettingsRepository.settings.collect { settings ->
                currentNotificationSettings = settings
                Log.d(TAG, "Notification settings updated - emergency popup: ${settings.emergencyPopupEnabled}")
            }
        }

        detectionSettingsJob = serviceScope.launch {
            detectionSettingsRepository.settings.collect(::applyDetectionSettings)
        }
    }

    /**
     * Admit persisted settings before any active scanner/subsystem starts.
     *
     * Failure is fail-closed: constrained-device and privacy policy are never
     * replaced with generic in-memory defaults merely because DataStore was
     * temporarily unavailable (for example during Direct Boot).
     */
    private fun startScanning() {
        if (isScanning.value || startupJob?.isActive == true) return

        scanStatus.value = ScanStatus.Starting
        startupJob = serviceScope.launch {
            try {
                withTimeout(SETTINGS_ADMISSION_TIMEOUT_MS) {
                    currentBroadcastSettings = broadcastSettingsRepository.settings.first()
                    currentPrivacySettings = privacySettingsRepository.settings.first()
                    applyScanSettings(scanSettingsRepository.settings.first())
                    currentNotificationSettings = notificationSettingsRepository.settings.first()
                    applyDetectionSettings(detectionSettingsRepository.settings.first())

                    cellularMonitor?.setEphemeralMode(currentPrivacySettings.ephemeralModeEnabled)
                    if (currentPrivacySettings.ephemeralModeEnabled) {
                        ephemeralRepository.clearAll()
                        enrichedDataCache.clear()
                    }
                }

                if (!isActive) return@launch
                acquireWakeLock()
                startScanningAdmitted()
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Persisted scanner settings were not available before admission timeout")
                scanStatus.value = ScanStatus.Error(
                    "Scanner settings unavailable; waiting for a later start/restart",
                    recoverable = true
                )
                releaseWakeLock()
            } catch (e: CancellationException) {
                scanStatus.value = ScanStatus.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to admit persisted scanner settings", e)
                scanStatus.value = ScanStatus.Error(
                    "Failed to load scanner settings: ${e.message ?: e.javaClass.simpleName}",
                    recoverable = true
                )
                releaseWakeLock()
            } finally {
                startupJob = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanningAdmitted() {
'''
replace_once(
    svc,
    '''    @SuppressLint("MissingPermission")
    private fun startScanning() {
''',
    start_wrapper,
)

replace_between(
    svc,
    "        // Collect broadcast settings\n",
    "        // Register screen lock receiver for auto-purge feature (Priority 5)\n",
    "        startSettingsCollectionJobs()\n\n",
)

replace_once(
    svc,
    '''        // Note: Ultrasonic detection is started by the privacy settings collector above
        // when it receives the first emission (handles the race condition between settings
        // loading and this point in the code). This ensures ultrasonic starts even if
        // settings are already enabled when the service restarts.
''',
    '''        // Persisted privacy settings were admitted before active scanning. Start the
        // opt-in detector explicitly; later settings transitions are handled by the collector.
        if (currentPrivacySettings.ultrasonicDetectionEnabled &&
            currentPrivacySettings.ultrasonicConsentAcknowledged) {
            startUltrasonicDetection()
        }
''',
)

replace_once(
    svc,
    '''        // Start heartbeat monitoring - sends periodic heartbeats to watchdog
        ServiceRestartReceiver.scheduleHeartbeat(this)
        ServiceRestartReceiver.scheduleJobSchedulerBackup(this)
''',
    '''        // Persist liveness in-process; the inexact watchdog and JobScheduler backup
        // consume this signal without a recurring one-minute exact wake alarm.
        ServiceRestartReceiver.scheduleJobSchedulerBackup(this)
''',
)

replace_once(
    svc,
    '''                        try {
                            val aggressiveBle = scanConfig.aggressiveBleMode &&
                            batteryMode == com.flockyou.data.BatteryAdaptiveMode.PERFORMANCE
                        startBleScan(aggressiveBle)
''',
    '''                        try {
                            val aggressiveBle = ScanningRuntimePolicy.shouldUseAggressiveBle(
                                scanConfig,
                                batteryMode
                            )
                            startBleScan(aggressiveBle)
''',
)

replace_once(
    svc,
    '''    private fun stopScanning() {
        scanStatus.value = ScanStatus.Stopping
        isScanning.value = false

        // Notify IPC clients that scanning has stopped
        broadcastScanningStopped()

        // Cancel settings collector jobs
        broadcastSettingsJob?.cancel()
        broadcastSettingsJob = null
        privacySettingsJob?.cancel()
        privacySettingsJob = null
        scanSettingsJob?.cancel()
        scanSettingsJob = null
        notificationSettingsJob?.cancel()
        notificationSettingsJob = null
        detectionSettingsJob?.cancel()
        detectionSettingsJob = null

        scanJob?.cancel()
''',
    '''    private fun stopScanning() {
        scanStatus.value = ScanStatus.Stopping
        isScanning.value = false

        val pendingStartup = startupJob
        startupJob = null
        pendingStartup?.cancel()

        detectorRestartJobs.values.forEach { it.cancel() }
        detectorRestartJobs.clear()

        // Notify IPC clients that scanning has stopped
        broadcastScanningStopped()

        stopSettingsCollectionJobs()

        scanJob?.cancel()
        scanJob = null
''',
)

replace_once(
    svc,
    "    private fun startBleScan(aggressiveMode: Boolean = true) {\n",
    "    private fun startBleScan(aggressiveMode: Boolean) {\n",
)

# Critical-job recovery must use the exact same settings semantics as normal
# startup, and a dead main loop must be fully torn down before re-admission.
health_replacement = '''    private fun checkAndRestartCriticalJobs() {
        if (isScanning.value && (scanJob == null || scanJob?.isActive != true)) {
            Log.w(TAG, "WATCHDOG: Main scan job stopped unexpectedly, restarting...")
            restartScanningLoopIfNeeded()
        }

        if (cellularMonitor != null &&
            (cellularAnomalyJob == null || cellularAnomalyJob?.isActive != true)) {
            Log.w(TAG, "WATCHDOG: Cellular anomaly job stopped, restarting...")
            restartCellularMonitoringJobs()
        }

        val settingsHealthy = listOf(
            broadcastSettingsJob,
            privacySettingsJob,
            scanSettingsJob,
            notificationSettingsJob,
            detectionSettingsJob
        ).all { it?.isActive == true }
        if (!settingsHealthy) {
            Log.w(TAG, "WATCHDOG: One or more settings collectors stopped, restarting canonical collectors...")
            restartSettingsCollectionJobs()
        }

        if (ipcClients.isNotEmpty()) {
            if (ipcRefreshJob == null || ipcRefreshJob?.isActive != true) {
                Log.w(TAG, "WATCHDOG: IPC refresh job stopped with active clients, restarting...")
                startIpcRefreshJob()
            }
        } else if (ipcRefreshJob?.isActive == true) {
            Log.d(TAG, "WATCHDOG: no IPC clients; stopping unnecessary refresh job")
            stopIpcRefreshJob()
        }

        if (throttleCleanupJob == null || throttleCleanupJob?.isActive != true) {
            Log.w(TAG, "WATCHDOG: Throttle cleanup job stopped, restarting...")
            startThrottleCleanup()
        }
    }

    private fun restartCellularMonitoringJobs() {
        try {
            stopCellularMonitoring()
            if (currentSettings.value.enableCellular && isScanning.value) {
                startCellularMonitoring()
            }
            Log.i(TAG, "Cellular monitoring jobs restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart cellular monitoring jobs", e)
        }
    }

    private fun restartSettingsCollectionJobs() {
        try {
            startSettingsCollectionJobs()
            Log.i(TAG, "Canonical settings collection jobs restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart settings collection jobs", e)
        }
    }

    private fun restartScanningLoopIfNeeded() {
        if (!isScanning.value) {
            Log.d(TAG, "Scanning is stopped, not restarting loop")
            return
        }

        Log.i(TAG, "Restarting scanner through coherent teardown and settings re-admission")
        serviceScope.launch {
            stopScanning()
            delay(1000)
            startScanning()
        }
    }

    private fun handleDetectorError(detectorName: String, error: String, recoverable: Boolean) {
        Log.e(TAG, "Detector error [$detectorName]: $error (recoverable=$recoverable)")

        updateDetectorHealth(detectorName) { current ->
            val newFailures = current.consecutiveFailures + 1
            current.copy(
                consecutiveFailures = newFailures,
                lastError = error,
                lastErrorTime = System.currentTimeMillis(),
                isHealthy = newFailures < MAX_CONSECUTIVE_FAILURES
            )
        }

        logError(detectorName, -1, error, recoverable)

        val currentStatus = detectorHealth.value[detectorName]
        if (recoverable && currentStatus != null &&
            currentStatus.consecutiveFailures < MAX_CONSECUTIVE_FAILURES &&
            currentStatus.restartCount < MAX_RESTART_ATTEMPTS) {
            val delayMs = (1000L * (1 shl currentStatus.consecutiveFailures.coerceAtMost(4)))
                .coerceAtMost(30_000L)
            detectorRestartJobs.compute(detectorName) { _, existing ->
                if (existing?.isActive == true) {
                    existing
                } else {
                    serviceScope.launch {
                        delay(delayMs)
                        if (isScanning.value) {
                            attemptDetectorRestart(detectorName)
                        } else {
                            Log.d(TAG, "Skipping delayed $detectorName restart because scanning stopped")
                        }
                    }
                }
            }
        }

        broadcastDetectorHealth()
    }

    private fun handleDetectorSuccess(detectorName: String) {
        detectorRestartJobs.remove(detectorName)?.cancel()
        updateDetectorHealth(detectorName) { current ->
            current.copy(
                lastSuccessfulScan = System.currentTimeMillis(),
                consecutiveFailures = 0,
                isHealthy = true
            )
        }
        broadcastDetectorHealth()
    }

'''
replace_between(
    svc,
    "    private fun checkAndRestartCriticalJobs() {\n",
    "    private fun updateDetectorHealth(detectorName: String, transform: (DetectorHealthStatus) -> DetectorHealthStatus) {\n",
    health_replacement,
)

attempt_replacement = '''    private fun attemptDetectorRestart(detectorName: String) {
        if (!isScanning.value) {
            Log.d(TAG, "Skipping $detectorName restart because scanning is stopped")
            return
        }

        if (detectorName == DetectorHealthStatus.DETECTOR_BLE && !currentSettings.value.enableBle) {
            Log.d(TAG, "Skipping BLE restart because BLE scanning is disabled")
            return
        }

        Log.i(TAG, "Attempting to restart detector: $detectorName")
        updateDetectorHealth(detectorName) { current ->
            current.copy(restartCount = current.restartCount + 1)
        }

        when (detectorName) {
            DetectorHealthStatus.DETECTOR_ULTRASONIC -> {
                try {
                    stopUltrasonicDetection()
                    startUltrasonicDetection()
                    Log.i(TAG, "Ultrasonic detector restarted through policy-aware lifecycle")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart ultrasonic detector", e)
                }
            }
            DetectorHealthStatus.DETECTOR_ROGUE_WIFI -> {
                try {
                    stopRogueWifiMonitoring()
                    startRogueWifiMonitoring()
                    Log.i(TAG, "Rogue WiFi monitor restarted")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart rogue WiFi monitor", e)
                }
            }
            DetectorHealthStatus.DETECTOR_RF_SIGNAL -> {
                try {
                    stopRfSignalAnalysis()
                    startRfSignalAnalysis()
                    Log.i(TAG, "RF signal analyzer restarted through settings-aware lifecycle")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart RF signal analyzer", e)
                }
            }
            DetectorHealthStatus.DETECTOR_CELLULAR -> {
                try {
                    stopCellularMonitoring()
                    if (currentSettings.value.enableCellular) startCellularMonitoring()
                    Log.i(TAG, "Cellular monitor restarted")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart cellular monitor", e)
                }
            }
            DetectorHealthStatus.DETECTOR_GNSS -> {
                try {
                    stopGnssMonitoring()
                    startGnssMonitoring()
                    Log.i(TAG, "GNSS monitor restarted through settings-aware lifecycle")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart GNSS monitor", e)
                }
            }
            DetectorHealthStatus.DETECTOR_SATELLITE -> {
                try {
                    stopSatelliteMonitoring()
                    startSatelliteMonitoring()
                    Log.i(TAG, "Satellite monitor restarted")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart satellite monitor", e)
                }
            }
            DetectorHealthStatus.DETECTOR_BLE -> {
                try {
                    stopBleScan()
                    val aggressive = ScanningRuntimePolicy.shouldUseAggressiveBle(
                        currentSettings.value,
                        currentBatteryMode.value
                    )
                    startBleScan(aggressive)
                    Log.i(TAG, "BLE scanner restarted with policy-preserving mode (aggressive=$aggressive)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart BLE scanner", e)
                }
            }
            DetectorHealthStatus.DETECTOR_WIFI -> {
                Log.i(TAG, "WiFi scanner restart requested (system-triggered)")
            }
        }

        broadcastDetectorHealth()
    }

'''
replace_between(
    svc,
    "    private fun attemptDetectorRestart(detectorName: String) {\n",
    "    // ==================== Utility ====================\n",
    attempt_replacement,
)

# Lazy monitors must receive already-admitted settings before their first start.
sub = "app/src/main/java/com/flockyou/service/SubsystemManager.kt"
replace_once(
    sub,
    '''    if (rfSignalAnalyzer == null) {
        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl)
    }
''',
    '''    if (rfSignalAnalyzer == null) {
        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl).also {
            it.enableHiddenNetworkRfAnomaly = currentDetectionSettings.enableHiddenNetworkRfAnomaly
        }
    }
''',
)
replace_once(
    sub,
    '''    if (ultrasonicDetector == null) {
        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)
    }

    Log.d(TAG, "Starting ultrasonic beacon detection (user consented, audio encrypted in memory)")
''',
    '''    if (ultrasonicDetector == null) {
        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)
    }
    ultrasonicDetector?.updateScanTiming(
        intervalSeconds = currentScanSettings.ultrasonicScanIntervalSeconds,
        durationSeconds = currentScanSettings.ultrasonicScanDurationSeconds
    )

    Log.d(TAG, "Starting ultrasonic beacon detection (user consented, audio encrypted in memory)")
''',
)
replace_once(
    sub,
    '''    if (gnssSatelliteMonitor == null) {
        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)
    }

    Log.d(TAG, "Starting GNSS satellite monitoring for spoofing/jamming detection")
''',
    '''    if (gnssSatelliteMonitor == null) {
        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)
    }
    gnssSatelliteMonitor?.updateScanTiming(currentScanSettings.gnssScanIntervalSeconds)

    Log.d(TAG, "Starting GNSS satellite monitoring for spoofing/jamming detection")
''',
)

# The periodic exact heartbeat is retired; liveness persistence continues from
# the scan loop and the five-minute watchdog/15-minute JobScheduler backup.
replace_once(
    svc,
    "        ServiceRestartReceiver.scheduleHeartbeat(this)\n",
    "",
) if "        ServiceRestartReceiver.scheduleHeartbeat(this)\n" in read(svc) else None

print("MAXSTATS Syn runtime patch applied successfully")
