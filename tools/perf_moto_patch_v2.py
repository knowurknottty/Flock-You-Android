#!/usr/bin/env python3
"""Apply verified low-RAM / constrained-device optimizations.

Every edit is guarded by an expected match count. Whitespace is deliberately
handled with regex where Kotlin indentation may vary.
"""
from pathlib import Path
import re
import textwrap


def patch(path: str, pattern: str, replacement: str, *, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    out, n = re.subn(pattern, replacement, text, count=count, flags=re.MULTILINE | re.DOTALL)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {pattern[:140]!r}")
    p.write_text(out)
    print(f"patched {path}: {pattern[:55]}")


def exact(path: str, old: str, new: str, *, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} exact matches, got {n}: {old[:140]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:55]!r}")


# ===========================================================================
# Battery / scan policy
# ===========================================================================
S = "app/src/main/java/com/flockyou/data/ScanSettings.kt"
exact(S, "import android.content.Context\n", "import android.app.ActivityManager\nimport android.content.Context\n")

patch(
    S,
    r"(?m)^        fun forBatteryLevel\(batteryPercent: Int\): BatteryAdaptiveMode = when \{\n"
    r"            batteryPercent <= MINIMAL\.batteryThreshold -> MINIMAL\n"
    r"            batteryPercent <= BATTERY_SAVER\.batteryThreshold -> BATTERY_SAVER\n"
    r"            batteryPercent <= BALANCED\.batteryThreshold -> BALANCED\n"
    r"            else -> PERFORMANCE\n"
    r"        \}",
    """        fun forBatteryLevel(batteryPercent: Int): BatteryAdaptiveMode = when {
            batteryPercent <= MINIMAL.batteryThreshold -> MINIMAL
            batteryPercent <= BATTERY_SAVER.batteryThreshold -> BATTERY_SAVER
            // AUTO is conservation-only. PERFORMANCE is explicit opt-in.
            // Battery charge is not a proxy for device performance.
            else -> BALANCED
        }""",
)

exact(
    S,
    """class ScanSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {""",
    """class ScanSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val constrainedDeviceDefaults: ScanSettings by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val fourGiB = 4L * 1024L * 1024L * 1024L
        val constrained = am.isLowRamDevice || am.memoryClass <= 256 || memoryInfo.totalMem <= fourGiB

        if (constrained) {
            ScanSettings(
                wifiScanIntervalSeconds = 45,
                bleScanDurationSeconds = 8,
                inactiveTimeoutSeconds = 90,
                rfScanIntervalSeconds = 60,
                ultrasonicScanIntervalSeconds = 60,
                ultrasonicScanDurationSeconds = 4,
                gnssScanIntervalSeconds = 10,
                satelliteScanIntervalSeconds = 30,
                cellularScanIntervalSeconds = 10,
                batteryAdaptiveMode = "balanced",
                autoBatteryAdaptive = true
            )
        } else {
            ScanSettings()
        }
    }

    private object PreferencesKeys {""",
)

patch(
    S,
    r"    val settings: Flow<ScanSettings> = context\.dataStore\.data\.map \{ preferences ->\n"
    r"        ScanSettings\(\n"
    r"(?P<body>.*?)"
    r"        \)\n"
    r"    \}",
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


# ===========================================================================
# Scanning service hot path
# ===========================================================================
S = "app/src/main/java/com/flockyou/service/ScanningService.kt"
exact(
    S,
    "import kotlinx.coroutines.*\nimport kotlinx.coroutines.flow.first\n",
    "import kotlinx.coroutines.*\nimport kotlinx.coroutines.channels.BufferOverflow\nimport kotlinx.coroutines.channels.Channel\nimport kotlinx.coroutines.flow.first\n",
)

exact(
    S,
    """        private const val BLE_HEALTH_UPDATE_THROTTLE_MS = 5_000L
        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
    """        private const val BLE_HEALTH_UPDATE_THROTTLE_MS = 5_000L
        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L
        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L
        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
)

exact(
    S,
    """    internal val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()""",
    """    internal val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bleResultChannel = Channel<ScanResult>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var bleProcessorJob: Job? = null
    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private val gson = Gson()""",
)

exact(
    S,
    """    @Inject
    lateinit var privacySettingsRepository: com.flockyou.data.PrivacySettingsRepository

    @Inject
    lateinit var scanSettingsRepository: com.flockyou.data.ScanSettingsRepository""",
    """    @Inject
    lateinit var privacySettingsRepository: com.flockyou.data.PrivacySettingsRepository

    @Inject
    lateinit var aiSettingsRepository: com.flockyou.data.AiSettingsRepository

    @Inject
    lateinit var scanSettingsRepository: com.flockyou.data.ScanSettingsRepository""",
)

exact(
    S,
    """        // Initialize RF Signal Analyzer with error callback
        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl)

        // Initialize Ultrasonic Detector with error callback
        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)

        // Initialize GNSS Satellite Monitor with error callback
        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)
""",
    """        // RF, ultrasonic and GNSS monitors are intentionally lazy.
        // Disabled subsystems should not consume startup RAM or threads.
""",
)

exact(
    S,
    """        // Start periodic IPC refresh to keep UI updated
        startIpcRefreshJob()
""",
    """        // IPC refresh is client-driven. Registration starts it when the first
        // UI client attaches and unregister stops it when the last client leaves.
""",
)

exact(
    S,
    """        // Start continuous scanning with burst pattern (25s on, 5s cooldown)
        scanJob = serviceScope.launch {""",
    """        // One bounded consumer replaces one coroutine per BLE advertisement.
        // Under overload we prefer fresh observations instead of unbounded scheduler pressure.
        if (bleProcessorJob?.isActive != true) {
            bleProcessorJob = serviceScope.launch {
                for (result in bleResultChannel) {
                    processBleScanResult(result)
                }
            }
        }

        // Start continuous scanning with burst pattern.
        scanJob = serviceScope.launch {""",
)

exact(
    S,
    "startBleScan(scanConfig.aggressiveBleMode)",
    """val aggressiveBle = scanConfig.aggressiveBleMode &&
                            batteryMode == com.flockyou.data.BatteryAdaptiveMode.PERFORMANCE
                        startBleScan(aggressiveBle)""",
)

exact(
    S,
    """                    // Mark old detections as inactive
                    try {
                        val inactiveThreshold = System.currentTimeMillis() - scanConfig.inactiveTimeout
                        repository.markOldInactive(inactiveThreshold)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking old detections inactive", e)
                    }

                    // Clean up old seen devices
                    if (scanConfig.trackSeenDevices) {
                        try {
                            cleanupSeenDevices(scanConfig.seenDeviceTimeout)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cleaning up seen devices", e)
                        }
                    }""",
    """                    // Housekeeping is not radio-hot-path work. Every fifth cycle is
                    // sufficient and avoids repeated SQLCipher writes and allocation churn.
                    if (scanCycleCount % 5 == 0) {
                        try {
                            val inactiveThreshold = System.currentTimeMillis() - scanConfig.inactiveTimeout
                            repository.markOldInactive(inactiveThreshold)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error marking old detections inactive", e)
                        }

                        if (scanConfig.trackSeenDevices) {
                            try {
                                cleanupSeenDevices(scanConfig.seenDeviceTimeout)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error cleaning up seen devices", e)
                            }
                        }
                    }""",
)

exact(
    S,
    """                    // Broadcast all data to IPC clients every scan cycle
                    try {
                        broadcastAllDataToClients()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error broadcasting to IPC clients", e)
                    }""",
    """                    // No attached UI means no broad IPC serialization work.
                    if (ipcClients.isNotEmpty()) {
                        try {
                            broadcastAllDataToClients()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error broadcasting to IPC clients", e)
                        }
                    }""",
)

exact(
    S,
    """        scanJob?.cancel()
        stopBleScan()""",
    """        scanJob?.cancel()
        bleProcessorJob?.cancel()
        bleProcessorJob = null
        while (bleResultChannel.tryReceive().isSuccess) { /* drain stale callbacks */ }
        stopBleScan()""",
)

exact(
    S,
    """            seenBleDevices.value = currentList
            broadcastSeenBleDevices()""",
    """            seenBleDevices.value = currentList
            val now = System.currentTimeMillis()
            if (now - lastSeenBleBroadcastTime >= SEEN_DEVICE_BROADCAST_THROTTLE_MS) {
                lastSeenBleBroadcastTime = now
                broadcastSeenBleDevices()
            }""",
)

exact(
    S,
    """        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)""",
    """        val scanSettings = ScanSettings.Builder()
            .setScanMode(
                if (aggressiveMode) ScanSettings.SCAN_MODE_LOW_LATENCY
                else ScanSettings.SCAN_MODE_BALANCED
            )
            .setReportDelay(if (aggressiveMode) 0L else 500L)""",
)

patch(
    S,
    r"        override fun onScanResult\(callbackType: Int, result: ScanResult\) \{.*?"
    r"        override fun onScanFailed\(errorCode: Int\) \{",
    """        override fun onScanResult(callbackType: Int, result: ScanResult) {
            lastBleScanResultTime = System.currentTimeMillis()
            bleWatchdogFailures = 0
            bleResultChannel.trySend(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            lastBleScanResultTime = System.currentTimeMillis()
            bleWatchdogFailures = 0
            results.forEach { bleResultChannel.trySend(it) }
        }

        override fun onScanFailed(errorCode: Int) {""",
)

exact(
    S,
    """        scanStats.value = scanStats.value.copy(
            bleDevicesSeen = scanStats.value.bleDevicesSeen + 1,
            lastBleSuccessTime = System.currentTimeMillis()
        )
        broadcastScanStats()

        // Report successful scan for health monitoring (throttled to avoid excessive updates)
        val now = System.currentTimeMillis()""",
    """        scanStats.value = scanStats.value.copy(
            bleDevicesSeen = scanStats.value.bleDevicesSeen + 1,
            lastBleSuccessTime = System.currentTimeMillis()
        )
        val statsNow = System.currentTimeMillis()
        if (statsNow - lastScanStatsBroadcastTime >= SCAN_STATS_BROADCAST_THROTTLE_MS) {
            lastScanStatsBroadcastTime = statsNow
            broadcastScanStats()
        }

        // Report successful scan for health monitoring (throttled to avoid excessive updates)
        val now = System.currentTimeMillis()""",
)


# ===========================================================================
# Lazy optional detector creation
# ===========================================================================
S = "app/src/main/java/com/flockyou/service/SubsystemManager.kt"

patch(
    S,
    r"internal fun ScanningService\.startRfSignalAnalysis\(\) \{\n"
    r"    // Only start RF analysis if RF detection is enabled\n"
    r"    if \(currentDetectionSettings\?\.enableRfDetection != true\) \{\n"
    r"        Log\.d\(TAG, \"RF signal analysis disabled by settings, skipping\"\)\n"
    r"        return\n"
    r"    \}\n"
    r"    Log\.d\(TAG, \"Starting RF signal analysis\"\)",
    """internal fun ScanningService.startRfSignalAnalysis() {
    if (!currentDetectionSettings.enableRfDetection) {
        Log.d(TAG, "RF signal analysis disabled by settings, skipping")
        return
    }
    if (rfSignalAnalyzer == null) {
        rfSignalAnalyzer = RfSignalAnalyzer(applicationContext, detectorCallbackImpl)
    }
    Log.d(TAG, "Starting RF signal analysis")""",
)

exact(
    S,
    """    Log.d(TAG, "Starting ultrasonic beacon detection (user consented, audio encrypted in memory)")

    ultrasonicDetector?.startMonitoring()""",
    """    if (ultrasonicDetector == null) {
        ultrasonicDetector = UltrasonicDetector(applicationContext, detectorCallbackImpl)
    }

    Log.d(TAG, "Starting ultrasonic beacon detection (user consented, audio encrypted in memory)")

    ultrasonicDetector?.startMonitoring()""",
)

exact(
    S,
    """    Log.d(TAG, "Starting GNSS satellite monitoring for spoofing/jamming detection")
    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Active

    gnssSatelliteMonitor?.startMonitoring()""",
    """    if (gnssSatelliteMonitor == null) {
        gnssSatelliteMonitor = com.flockyou.monitoring.GnssSatelliteMonitor(applicationContext, detectorCallbackImpl)
    }

    Log.d(TAG, "Starting GNSS satellite monitoring for spoofing/jamming detection")
    ScanningServiceState.gnssMonitorStatus.value = SubsystemStatus.Active

    gnssSatelliteMonitor?.startMonitoring()""",
)


# ===========================================================================
# AI / WorkManager gating
# ===========================================================================
S = "app/src/main/java/com/flockyou/service/DetectionProcessor.kt"
exact(S, "import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch\n")

exact(
    S,
    """            // Queue for enhanced LLM analysis if not in ephemeral mode
            // This runs in background and will enhance the quick rule-based analysis
            if (!currentPrivacySettings.ephemeralModeEnabled) {
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
            }""",
    """            // Expensive per-detection analysis is opt-in. AI disabled means no
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
            }""",
)

patch(
    S,
    r"internal suspend fun ScanningService\.insertDetectionWithAnalysis\(detection: Detection\) \{\n"
    r"    repository\.insertDetection\(detection\)\n\n"
    r"    // Trigger immediate LLM analysis for this detection\n"
    r"    try \{.*?\n"
    r"    \}\n"
    r"\}",
    """internal suspend fun ScanningService.insertDetectionWithAnalysis(detection: Detection) {
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
}""",
)

exact(
    S,
    """    serviceScope.launch {
        try {
            Log.i(TAG, "Starting LLM warm-up in background...")
            val startTime = System.currentTimeMillis()

            // Check if LLM engine is available""",
    """    serviceScope.launch {
        try {
            val aiSettings = aiSettingsRepository.settings.first()
            if (!aiSettings.enabled || !aiSettings.autoAnalyzeNewDetections) {
                Log.d(TAG, "LLM warm-up skipped - AI auto-analysis disabled")
                return@launch
            }

            Log.i(TAG, "Starting LLM warm-up in background...")
            val startTime = System.currentTimeMillis()

            // Check if LLM engine is available""",
)

S = "app/src/main/java/com/flockyou/worker/BackgroundAnalysisWorker.kt"
exact(
    S,
    """        val specificDetectionIds = inputData.getStringArray(KEY_DETECTION_IDS)?.toList()

        // For high-priority analysis or specific detections, run as foreground service""",
    """        val specificDetectionIds = inputData.getStringArray(KEY_DETECTION_IDS)?.toList()

        val aiSettings = aiSettingsRepository.settings.first()
        if (!aiSettings.enabled) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "AI analysis disabled; skipping worker before foreground promotion")
            }
            return@withContext Result.success()
        }

        // For high-priority analysis or specific detections, run as foreground service""",
)

patch(
    S,
    r"            // Check if AI analysis is enabled\n"
    r"            // Note: Specific detection triggers bypass the master AI switch since rule-based\n"
    r"            // analysis is always useful and the LLM will fall back to rule-based if needed\n"
    r"            val aiSettings = aiSettingsRepository\.settings\.first\(\)\n"
    r"            val isSpecificDetectionTrigger = !specificDetectionIds\.isNullOrEmpty\(\)\n\n"
    r"            if \(!aiSettings\.enabled && !isSpecificDetectionTrigger\) \{.*?\n"
    r"            \}\n\n"
    r"            // Check if FP filtering is enabled \(but allow specific triggers to proceed\)",
    """            val isSpecificDetectionTrigger = !specificDetectionIds.isNullOrEmpty()

            // Check if FP filtering is enabled (but allow specific triggers to proceed)""",
)


# ===========================================================================
# Regression tests
# ===========================================================================
test = Path("app/src/test/java/com/flockyou/data/ScanSettingsTest.kt")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(textwrap.dedent("""\
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
        val settings = ScanSettings(batteryAdaptiveMode = "performance", autoBatteryAdaptive = false)
        assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
    }
}
"""))

print("Moto/low-RAM performance patch applied successfully")
