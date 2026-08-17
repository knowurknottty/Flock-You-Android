#!/usr/bin/env python3
"""Apply the first constrained-device performance pass to Flock-You.

This script is intentionally assertion-heavy. It refuses to patch if upstream source
no longer matches the reviewed baseline instead of silently producing a partial edit.
"""

from pathlib import Path
import re
import textwrap


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    Path(path).write_text(text)


def must_replace(path: str, old: str, new: str, count: int = 1) -> None:
    text = read(path)
    actual = text.count(old)
    if actual != count:
        raise SystemExit(
            f"{path}: expected {count} exact matches, found {actual}: {old[:120]!r}"
        )
    write(path, text.replace(old, new, count))


def must_regex(path: str, pattern: str, replacement: str, count: int = 1) -> None:
    text = read(path)
    new_text, actual = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if actual != count:
        raise SystemExit(
            f"{path}: expected {count} regex matches, found {actual}: {pattern[:120]!r}"
        )
    write(path, new_text)


# ---------------------------------------------------------------------------
# 1. Battery policy + constrained-device defaults
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/data/ScanSettings.kt"

must_replace(
    path,
    "import android.content.Context\n",
    "import android.app.ActivityManager\nimport android.content.Context\n",
)

must_replace(
    path,
    textwrap.dedent(
        """\
                fun forBatteryLevel(batteryPercent: Int): BatteryAdaptiveMode = when {
                    batteryPercent <= MINIMAL.batteryThreshold -> MINIMAL
                    batteryPercent <= BATTERY_SAVER.batteryThreshold -> BATTERY_SAVER
                    batteryPercent <= BALANCED.batteryThreshold -> BALANCED
                    else -> PERFORMANCE
                }
        """
    ),
    textwrap.dedent(
        """\
                fun forBatteryLevel(batteryPercent: Int): BatteryAdaptiveMode = when {
                    batteryPercent <= MINIMAL.batteryThreshold -> MINIMAL
                    batteryPercent <= BATTERY_SAVER.batteryThreshold -> BATTERY_SAVER
                    // AUTO is conservation-only. PERFORMANCE remains an explicit opt-in;
                    // a charged phone is not automatically a fast phone.
                    else -> BALANCED
                }
        """
    ),
)

must_replace(
    path,
    """class ScanSettingsRepository @Inject constructor(\n    @ApplicationContext private val context: Context\n) {\n    private object PreferencesKeys {""",
    """class ScanSettingsRepository @Inject constructor(\n    @ApplicationContext private val context: Context\n) {\n    private val constrainedDeviceDefaults: ScanSettings by lazy {\n        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager\n        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)\n        val fourGiB = 4L * 1024L * 1024L * 1024L\n        val constrained = activityManager.isLowRamDevice ||\n            activityManager.memoryClass <= 256 ||\n            memoryInfo.totalMem <= fourGiB\n\n        if (constrained) {\n            ScanSettings(\n                wifiScanIntervalSeconds = 45,\n                bleScanDurationSeconds = 8,\n                inactiveTimeoutSeconds = 90,\n                rfScanIntervalSeconds = 60,\n                ultrasonicScanIntervalSeconds = 60,\n                ultrasonicScanDurationSeconds = 4,\n                gnssScanIntervalSeconds = 10,\n                satelliteScanIntervalSeconds = 30,\n                cellularScanIntervalSeconds = 10,\n                batteryAdaptiveMode = \"balanced\",\n                autoBatteryAdaptive = true\n            )\n        } else {\n            ScanSettings()\n        }\n    }\n\n    private object PreferencesKeys {""",
)

must_regex(
    path,
    r"    val settings: Flow<ScanSettings> = context\.dataStore\.data\.map \{ preferences ->\n"
    r"        ScanSettings\(.*?\n        \)\n    \}",
    """    val settings: Flow<ScanSettings> = context.dataStore.data.map { preferences ->
        val defaults = constrainedDeviceDefaults
        ScanSettings(
            wifiScanIntervalSeconds = preferences[PreferencesKeys.WIFI_SCAN_INTERVAL] ?: defaults.wifiScanIntervalSeconds,
            bleScanDurationSeconds = preferences[PreferencesKeys.BLE_SCAN_DURATION] ?: defaults.bleScanDurationSeconds,
            inactiveTimeoutSeconds = preferences[PreferencesKeys.INACTIVE_TIMEOUT] ?: defaults.inactiveTimeoutSeconds,
            seenDeviceTimeoutMinutes = preferences[PreferencesKeys.SEEN_DEVICE_TIMEOUT] ?: defaults.seenDeviceTimeoutMinutes,
            enableBleScanning = preferences[PreferencesKeys.ENABLE_BLE] ?: defaults.enableBleScanning,
            enableWifiScanning = preferences[PreferencesKeys.ENABLE_WIFI] ?: defaults.enableWifiScanning,
            trackSeenDevices = preferences[PreferencesKeys.TRACK_SEEN_DEVICES] ?: defaults.trackSeenDevices,
            rfScanIntervalSeconds = preferences[PreferencesKeys.RF_SCAN_INTERVAL] ?: defaults.rfScanIntervalSeconds,
            ultrasonicScanIntervalSeconds = preferences[PreferencesKeys.ULTRASONIC_SCAN_INTERVAL] ?: defaults.ultrasonicScanIntervalSeconds,
            ultrasonicScanDurationSeconds = preferences[PreferencesKeys.ULTRASONIC_SCAN_DURATION] ?: defaults.ultrasonicScanDurationSeconds,
            gnssScanIntervalSeconds = preferences[PreferencesKeys.GNSS_SCAN_INTERVAL] ?: defaults.gnssScanIntervalSeconds,
            satelliteScanIntervalSeconds = preferences[PreferencesKeys.SATELLITE_SCAN_INTERVAL] ?: defaults.satelliteScanIntervalSeconds,
            cellularScanIntervalSeconds = preferences[PreferencesKeys.CELLULAR_SCAN_INTERVAL] ?: defaults.cellularScanIntervalSeconds,
            batteryAdaptiveMode = preferences[PreferencesKeys.BATTERY_ADAPTIVE_MODE] ?: defaults.batteryAdaptiveMode,
            autoBatteryAdaptive = preferences[PreferencesKeys.AUTO_BATTERY_ADAPTIVE] ?: defaults.autoBatteryAdaptive
        )
    }""",
)


# ---------------------------------------------------------------------------
# 2. Scanning hot path
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/ScanningService.kt"

must_replace(
    path,
    "import kotlinx.coroutines.*\nimport kotlinx.coroutines.flow.first\n",
    "import kotlinx.coroutines.*\nimport kotlinx.coroutines.channels.BufferOverflow\nimport kotlinx.coroutines.channels.Channel\nimport kotlinx.coroutines.flow.first\n",
)

must_replace(
    path,
    """        private const val BLE_HEALTH_UPDATE_THROTTLE_MS = 5_000L\n        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
    """        private const val BLE_HEALTH_UPDATE_THROTTLE_MS = 5_000L\n        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L\n        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L\n        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
)

must_replace(
    path,
    """    internal val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n    private val gson = Gson()""",
    """    internal val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n    private val bleResultChannel = Channel<ScanResult>(\n        capacity = 128,\n        onBufferOverflow = BufferOverflow.DROP_OLDEST\n    )\n    private var bleProcessorJob: Job? = null\n    private var lastScanStatsBroadcastTime: Long = 0L\n    private var lastSeenBleBroadcastTime: Long = 0L\n    private val gson = Gson()""",
)

must_replace(
    path,
    """    @Inject\n    lateinit var privacySettingsRepository: com.flockyou.data.PrivacySettingsRepository\n\n    @Inject\n    lateinit var scanSettingsRepository: com.flockyou.data.ScanSettingsRepository""",
    """    @Inject\n    lateinit var privacySettingsRepository: com.flockyou.data.PrivacySettingsRepository\n\n    @Inject\n    lateinit var aiSettingsRepository: com.flockyou.data.AiSettingsRepository\n\n    @Inject\n    lateinit var scanSettingsRepository: com.flockyou.data.ScanSettingsRepository""",
)

must_replace(
    path,
    """        // Initialize RF Signal Analyzer with error callback\n        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl)\n\n        // Initialize Ultrasonic Detector with error callback\n        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)\n\n        // Initialize GNSS Satellite Monitor with error callback\n        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)\n""",
    """        // RF, ultrasonic and GNSS monitors are intentionally lazy.\n        // Disabled subsystems should not consume startup RAM/threads.\n""",
)

must_replace(
    path,
    """        // Start periodic IPC refresh to keep UI updated\n        startIpcRefreshJob()\n""",
    """        // IPC refresh is client-driven: registration starts it for the first\n        // attached UI client and unregister stops it for the last one.\n""",
)

must_replace(
    path,
    """        // Start continuous scanning with burst pattern (25s on, 5s cooldown)\n        scanJob = serviceScope.launch {""",
    """        // One bounded BLE consumer replaces one-coroutine-per-advertisement.\n        // DROP_OLDEST preserves current observations under dense radio traffic.\n        if (bleProcessorJob?.isActive != true) {\n            bleProcessorJob = serviceScope.launch {\n                for (result in bleResultChannel) {\n                    processBleScanResult(result)\n                }\n            }\n        }\n\n        // Start continuous scanning with burst pattern.\n        scanJob = serviceScope.launch {""",
)

must_replace(
    path,
    "startBleScan(scanConfig.aggressiveBleMode)",
    """val aggressiveBle = scanConfig.aggressiveBleMode &&\n                            batteryMode == com.flockyou.data.BatteryAdaptiveMode.PERFORMANCE\n                        startBleScan(aggressiveBle)""",
)

must_replace(
    path,
    """                    // Mark old detections as inactive\n                    try {\n                        val inactiveThreshold = System.currentTimeMillis() - scanConfig.inactiveTimeout\n                        repository.markOldInactive(inactiveThreshold)\n                    } catch (e: Exception) {\n                        Log.e(TAG, \"Error marking old detections inactive\", e)\n                    }\n\n                    // Clean up old seen devices\n                    if (scanConfig.trackSeenDevices) {\n                        try {\n                            cleanupSeenDevices(scanConfig.seenDeviceTimeout)\n                        } catch (e: Exception) {\n                            Log.e(TAG, \"Error cleaning up seen devices\", e)\n                        }\n                    }""",
    """                    // Housekeeping is not radio-hot-path work. Run it every fifth\n                    // scan cycle to reduce SQLCipher writes, allocation and lock churn.\n                    if (scanCycleCount % 5 == 0) {\n                        try {\n                            val inactiveThreshold = System.currentTimeMillis() - scanConfig.inactiveTimeout\n                            repository.markOldInactive(inactiveThreshold)\n                        } catch (e: Exception) {\n                            Log.e(TAG, \"Error marking old detections inactive\", e)\n                        }\n\n                        if (scanConfig.trackSeenDevices) {\n                            try {\n                                cleanupSeenDevices(scanConfig.seenDeviceTimeout)\n                            } catch (e: Exception) {\n                                Log.e(TAG, \"Error cleaning up seen devices\", e)\n                            }\n                        }\n                    }""",
)

must_replace(
    path,
    """                    // Broadcast all data to IPC clients every scan cycle\n                    try {\n                        broadcastAllDataToClients()\n                    } catch (e: Exception) {\n                        Log.e(TAG, \"Error broadcasting to IPC clients\", e)\n                    }""",
    """                    // Do not serialize broad IPC payloads when no UI client is attached.\n                    if (ipcClients.isNotEmpty()) {\n                        try {\n                            broadcastAllDataToClients()\n                        } catch (e: Exception) {\n                            Log.e(TAG, \"Error broadcasting to IPC clients\", e)\n                        }\n                    }""",
)

must_replace(
    path,
    """        scanJob?.cancel()\n        stopBleScan()""",
    """        scanJob?.cancel()\n        bleProcessorJob?.cancel()\n        bleProcessorJob = null\n        while (bleResultChannel.tryReceive().isSuccess) { /* drain stale callbacks */ }\n        stopBleScan()""",
)

must_replace(
    path,
    """            seenBleDevices.value = currentList\n            broadcastSeenBleDevices()""",
    """            seenBleDevices.value = currentList\n            val now = System.currentTimeMillis()\n            if (now - lastSeenBleBroadcastTime >= SEEN_DEVICE_BROADCAST_THROTTLE_MS) {\n                lastSeenBleBroadcastTime = now\n                broadcastSeenBleDevices()\n            }""",
)

must_replace(
    path,
    """        val scanSettings = ScanSettings.Builder()\n            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)\n            .setReportDelay(0)""",
    """        val scanSettings = ScanSettings.Builder()\n            .setScanMode(\n                if (aggressiveMode) ScanSettings.SCAN_MODE_LOW_LATENCY\n                else ScanSettings.SCAN_MODE_BALANCED\n            )\n            .setReportDelay(if (aggressiveMode) 0L else 500L)""",
)

must_replace(
    path,
    """        override fun onScanResult(callbackType: Int, result: ScanResult) {\n            // Update watchdog timestamp - we're receiving results\n            lastBleScanResultTime = System.currentTimeMillis()\n            bleWatchdogFailures = 0\n            serviceScope.launch {\n                processBleScanResult(result)\n            }\n        }\n\n        override fun onBatchScanResults(results: MutableList<ScanResult>) {\n            lastBleScanResultTime = System.currentTimeMillis()\n            bleWatchdogFailures = 0\n            serviceScope.launch {\n                results.forEach { processBleScanResult(it) }\n            }\n        }""",
    """        override fun onScanResult(callbackType: Int, result: ScanResult) {\n            lastBleScanResultTime = System.currentTimeMillis()\n            bleWatchdogFailures = 0\n            bleResultChannel.trySend(result)\n        }\n\n        override fun onBatchScanResults(results: MutableList<ScanResult>) {\n            lastBleScanResultTime = System.currentTimeMillis()\n            bleWatchdogFailures = 0\n            results.forEach { bleResultChannel.trySend(it) }\n        }""",
)

must_replace(
    path,
    """        scanStats.value = scanStats.value.copy(\n            bleDevicesSeen = scanStats.value.bleDevicesSeen + 1,\n            lastBleSuccessTime = System.currentTimeMillis()\n        )\n        broadcastScanStats()\n\n        // Report successful scan for health monitoring (throttled to avoid excessive updates)\n        val now = System.currentTimeMillis()""",
    """        scanStats.value = scanStats.value.copy(\n            bleDevicesSeen = scanStats.value.bleDevicesSeen + 1,\n            lastBleSuccessTime = System.currentTimeMillis()\n        )\n        val statsNow = System.currentTimeMillis()\n        if (statsNow - lastScanStatsBroadcastTime >= SCAN_STATS_BROADCAST_THROTTLE_MS) {\n            lastScanStatsBroadcastTime = statsNow\n            broadcastScanStats()\n        }\n\n        // Report successful scan for health monitoring (throttled to avoid excessive updates)\n        val now = System.currentTimeMillis()""",
)


# ---------------------------------------------------------------------------
# 3. Lazy optional detector construction
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/SubsystemManager.kt"

must_replace(
    path,
    """internal fun ScanningService.startRfSignalAnalysis() {\n    // Only start RF analysis if RF detection is enabled\n    if (currentDetectionSettings?.enableRfDetection != true) {\n        Log.d(TAG, \"RF signal analysis disabled by settings, skipping\")\n        return\n    }\n    Log.d(TAG, \"Starting RF signal analysis\")\n\n    rfSignalAnalyzer?.startMonitoring()""",
    """internal fun ScanningService.startRfSignalAnalysis() {\n    if (currentDetectionSettings.enableRfDetection != true) {\n        Log.d(TAG, \"RF signal analysis disabled by settings, skipping\")\n        return\n    }\n    if (rfSignalAnalyzer == null) {\n        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl)\n    }\n    Log.d(TAG, \"Starting RF signal analysis\")\n\n    rfSignalAnalyzer?.startMonitoring()""",
)

must_replace(
    path,
    """    Log.d(TAG, \"Starting ultrasonic beacon detection (user consented, audio encrypted in memory)\")\n\n    ultrasonicDetector?.startMonitoring()""",
    """    if (ultrasonicDetector == null) {\n        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)\n    }\n\n    Log.d(TAG, \"Starting ultrasonic beacon detection (user consented, audio encrypted in memory)\")\n\n    ultrasonicDetector?.startMonitoring()""",
)

must_replace(
    path,
    """    Log.d(TAG, \"Starting GNSS satellite monitoring for spoofing/jamming detection\")\n    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Active\n\n    gnssSatelliteMonitor?.startMonitoring()""",
    """    if (gnssSatelliteMonitor == null) {\n        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)\n    }\n\n    Log.d(TAG, \"Starting GNSS satellite monitoring for spoofing/jamming detection\")\n    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Active\n\n    gnssSatelliteMonitor?.startMonitoring()""",
)


# ---------------------------------------------------------------------------
# 4. AI/WorkManager gating
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/DetectionProcessor.kt"

must_replace(
    path,
    "import kotlinx.coroutines.launch\n",
    "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch\n",
)

must_replace(
    path,
    """            // Queue for enhanced LLM analysis if not in ephemeral mode\n            // This runs in background and will enhance the quick rule-based analysis\n            if (!currentPrivacySettings.ephemeralModeEnabled) {\n                try {\n                    BackgroundAnalysisWorker.triggerForDetections(\n                        this@handleDetection,\n                        listOf(detectionWithFp.id)\n                    )\n                    if (BuildConfig.DEBUG) {\n                        Log.d(TAG, \"Queued detection for LLM analysis: ${detectionWithFp.id}\")\n                    }\n                } catch (e: Exception) {\n                    Log.w(TAG, \"Failed to queue detection for LLM analysis: ${e.message}\")\n                }\n            }""",
    """            // Queue expensive analysis only when explicitly enabled. Previously\n            // every detection could enqueue expedited WorkManager work with AI disabled.\n            if (!currentPrivacySettings.ephemeralModeEnabled) {\n                val aiSettings = aiSettingsRepository.settings.first()\n                if (aiSettings.enabled && aiSettings.autoAnalyzeNewDetections) {\n                    try {\n                        BackgroundAnalysisWorker.triggerForDetections(\n                            this@handleDetection,\n                            listOf(detectionWithFp.id)\n                        )\n                        if (BuildConfig.DEBUG) {\n                            Log.d(TAG, \"Queued detection for LLM analysis: ${detectionWithFp.id}\")\n                        }\n                    } catch (e: Exception) {\n                        Log.w(TAG, \"Failed to queue detection for LLM analysis: ${e.message}\")\n                    }\n                }\n            }""",
)

must_replace(
    path,
    """internal suspend fun ScanningService.insertDetectionWithAnalysis(detection: Detection) {\n    repository.insertDetection(detection)\n\n    // Trigger immediate LLM analysis for this detection\n    try {\n        BackgroundAnalysisWorker.triggerForDetections(\n            this@insertDetectionWithAnalysis,\n            listOf(detection.id)\n        )\n        if (BuildConfig.DEBUG) {\n            Log.d(TAG, \"Triggered immediate LLM analysis for detection: ${detection.id}\")\n        }\n    } catch (e: Exception) {\n        Log.w(TAG, \"Failed to trigger immediate LLM analysis: ${e.message}\")\n    }\n}""",
    """internal suspend fun ScanningService.insertDetectionWithAnalysis(detection: Detection) {\n    repository.insertDetection(detection)\n\n    val aiSettings = aiSettingsRepository.settings.first()\n    if (aiSettings.enabled && aiSettings.autoAnalyzeNewDetections) {\n        try {\n            BackgroundAnalysisWorker.triggerForDetections(\n                this@insertDetectionWithAnalysis,\n                listOf(detection.id)\n            )\n            if (BuildConfig.DEBUG) {\n                Log.d(TAG, \"Triggered immediate LLM analysis for detection: ${detection.id}\")\n            }\n        } catch (e: Exception) {\n            Log.w(TAG, \"Failed to trigger immediate LLM analysis: ${e.message}\")\n        }\n    }\n}""",
)

must_replace(
    path,
    """    serviceScope.launch {\n        try {\n            Log.i(TAG, \"Starting LLM warm-up in background...\")\n            val startTime = System.currentTimeMillis()\n\n            // Check if LLM engine is available""",
    """    serviceScope.launch {\n        try {\n            val aiSettings = aiSettingsRepository.settings.first()\n            if (!aiSettings.enabled || !aiSettings.autoAnalyzeNewDetections) {\n                Log.d(TAG, \"LLM warm-up skipped - AI auto-analysis disabled\")\n                return@launch\n            }\n\n            Log.i(TAG, \"Starting LLM warm-up in background...\")\n            val startTime = System.currentTimeMillis()\n\n            // Check if LLM engine is available""",
)

path = "app/src/main/java/com/flockyou/worker/BackgroundAnalysisWorker.kt"

must_replace(
    path,
    """        val specificDetectionIds = inputData.getStringArray(KEY_DETECTION_IDS)?.toList()\n\n        // For high-priority analysis or specific detections, run as foreground service""",
    """        val specificDetectionIds = inputData.getStringArray(KEY_DETECTION_IDS)?.toList()\n\n        val aiSettings = aiSettingsRepository.settings.first()\n        if (!aiSettings.enabled) {\n            if (BuildConfig.DEBUG) {\n                Log.d(TAG, \"AI analysis disabled; skipping worker before foreground promotion\")\n            }\n            return@withContext Result.success()\n        }\n\n        // For high-priority analysis or specific detections, run as foreground service""",
)

must_replace(
    path,
    """            val aiSettings = aiSettingsRepository.settings.first()\n            val isSpecificDetectionTrigger = !specificDetectionIds.isNullOrEmpty()\n\n            if (!aiSettings.enabled && !isSpecificDetectionTrigger) {\n                if (BuildConfig.DEBUG) {\n                    Log.d(TAG, \"AI analysis is disabled, skipping background analysis\")\n                }\n                return@withContext Result.success()\n            }\n\n            // Check if FP filtering is enabled (but allow specific triggers to proceed)""",
    """            val isSpecificDetectionTrigger = !specificDetectionIds.isNullOrEmpty()\n\n            // Check if FP filtering is enabled (but allow specific triggers to proceed)""",
)


# ---------------------------------------------------------------------------
# 5. Regression coverage
# ---------------------------------------------------------------------------
test_path = Path("app/src/test/java/com/flockyou/data/ScanSettingsTest.kt")
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text(
    textwrap.dedent(
        """\
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
                val settings = ScanSettings(
                    batteryAdaptiveMode = "performance",
                    autoBatteryAdaptive = false
                )
                assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
            }
        }
        """
    )
)

print("Moto/low-RAM performance patch applied successfully")
