#!/usr/bin/env python3
"""P5 constrained-device cleanup: lazy LLM engine, guaranteed BLE snapshots,
and sane first-scan defaults.

Fail closed: every edit must match the verified P4 source exactly once.
"""
from pathlib import Path


def exact(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {old[:180]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:72]!r}")


# ---------------------------------------------------------------------------
# 1) Scanning process: do not construct the LLM manager/clients at service
# injection time when AI is disabled. Resolve it only after the AI settings gate.
# ---------------------------------------------------------------------------
service = "app/src/main/java/com/flockyou/service/ScanningService.kt"

exact(
    service,
    "import dagger.hilt.android.AndroidEntryPoint\n",
    "import dagger.Lazy\nimport dagger.hilt.android.AndroidEntryPoint\n",
)

exact(
    service,
    """    @Inject
    lateinit var llmEngineManager: com.flockyou.ai.LlmEngineManager""",
    """    @Inject
    lateinit var llmEngineManager: Lazy<com.flockyou.ai.LlmEngineManager>""",
)

# ---------------------------------------------------------------------------
# 2) Guaranteed coalesced BLE publication. A pure timestamp throttle could leave
# the final advertisement in a burst only in the registry if it arrived inside
# the 750 ms window. Mirror Wi-Fi's scheduled snapshot model so the final state
# is always eventually materialized and sent.
# ---------------------------------------------------------------------------
exact(
    service,
    """    private val seenBleRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private val seenWifiRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private var seenWifiPublishJob: Job? = null
    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private var lastHeartbeatRecordElapsed = 0L""",
    """    private val seenBleRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private val seenWifiRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private var seenBlePublishJob: Job? = null
    private var seenWifiPublishJob: Job? = null
    private var lastScanStatsBroadcastTime = 0L
    private var lastHeartbeatRecordElapsed = 0L""",
)

exact(
    service,
    """    private fun clearSeenDeviceRegistries() {
        seenWifiPublishJob?.cancel()
        seenWifiPublishJob = null
        synchronized(ScanningServiceState) {
            seenBleRegistry.clear()
            seenWifiRegistry.clear()
            clearSeenDevices()
        }
    }""",
    """    private fun clearSeenDeviceRegistries() {
        seenBlePublishJob?.cancel()
        seenBlePublishJob = null
        seenWifiPublishJob?.cancel()
        seenWifiPublishJob = null
        synchronized(ScanningServiceState) {
            seenBleRegistry.clear()
            seenWifiRegistry.clear()
            clearSeenDevices()
        }
    }""",
)

old_ble = """    private fun trackSeenBleDevice(
        macAddress: String,
        deviceName: String?,
        rssi: Int,
        serviceUuids: List<java.util.UUID>,
        manufacturerData: Map<Int, String> = emptyMap(),
        advertisingRate: Float = 0f
    ) {
        val now = System.currentTimeMillis()
        var publishSnapshot = false

        synchronized(ScanningServiceState) {
            val existing = seenBleRegistry[macAddress]
            val updated = if (existing != null) {
                existing.copy(
                    name = deviceName ?: existing.name,
                    rssi = rssi,
                    lastSeen = now,
                    seenCount = existing.seenCount + 1,
                    manufacturerData = if (manufacturerData.isNotEmpty()) manufacturerData else existing.manufacturerData,
                    advertisingRate = advertisingRate
                )
            } else {
                val manufacturer = try {
                    DetectionPatterns.getManufacturerFromOui(macAddress.take(8).uppercase())
                } catch (e: Exception) { null }

                SeenDevice(
                    id = macAddress,
                    name = deviceName,
                    type = "BLE",
                    rssi = rssi,
                    manufacturer = manufacturer,
                    serviceUuids = serviceUuids.map { it.toString() },
                    manufacturerData = manufacturerData,
                    advertisingRate = advertisingRate
                )
            }

            seenBleRegistry[macAddress] = updated
            trimSeenRegistry(seenBleRegistry)

            if (now - lastSeenBleBroadcastTime >= SEEN_DEVICE_BROADCAST_THROTTLE_MS) {
                lastSeenBleBroadcastTime = now
                seenBleDevices.value = seenBleRegistry.values.toList().asReversed()
                publishSnapshot = true
            }
        }

        if (publishSnapshot) broadcastSeenBleDevices()
    }

    private fun scheduleSeenWifiSnapshot() {"""

new_ble = """    private fun scheduleSeenBleSnapshot() {
        synchronized(ScanningServiceState) {
            if (seenBlePublishJob?.isActive == true) return
            seenBlePublishJob = serviceScope.launch {
                delay(SEEN_DEVICE_BROADCAST_THROTTLE_MS)
                synchronized(ScanningServiceState) {
                    seenBleDevices.value = seenBleRegistry.values.toList().asReversed()
                    seenBlePublishJob = null
                }
                broadcastSeenBleDevices()
            }
        }
    }

    private fun trackSeenBleDevice(
        macAddress: String,
        deviceName: String?,
        rssi: Int,
        serviceUuids: List<java.util.UUID>,
        manufacturerData: Map<Int, String> = emptyMap(),
        advertisingRate: Float = 0f
    ) {
        val now = System.currentTimeMillis()

        synchronized(ScanningServiceState) {
            val existing = seenBleRegistry[macAddress]
            val updated = if (existing != null) {
                existing.copy(
                    name = deviceName ?: existing.name,
                    rssi = rssi,
                    lastSeen = now,
                    seenCount = existing.seenCount + 1,
                    manufacturerData = if (manufacturerData.isNotEmpty()) manufacturerData else existing.manufacturerData,
                    advertisingRate = advertisingRate
                )
            } else {
                val manufacturer = try {
                    DetectionPatterns.getManufacturerFromOui(macAddress.take(8).uppercase())
                } catch (e: Exception) { null }

                SeenDevice(
                    id = macAddress,
                    name = deviceName,
                    type = "BLE",
                    rssi = rssi,
                    manufacturer = manufacturer,
                    serviceUuids = serviceUuids.map { it.toString() },
                    manufacturerData = manufacturerData,
                    advertisingRate = advertisingRate
                )
            }

            seenBleRegistry[macAddress] = updated
            trimSeenRegistry(seenBleRegistry)
        }

        scheduleSeenBleSnapshot()
    }

    private fun scheduleSeenWifiSnapshot() {"""

exact(service, old_ble, new_ble)

# ---------------------------------------------------------------------------
# 3) First service cycle should not inherit a 25-second BLE burst while the
# DataStore collector races to publish the real 10s (or low-RAM 8s) setting.
# Align the runtime fallback with the ordinary ScanSettings default.
# ---------------------------------------------------------------------------
models = "app/src/main/java/com/flockyou/service/ScanningServiceModels.kt"
exact(
    models,
    "private const val DEFAULT_BLE_SCAN_DURATION = 25000L",
    "private const val DEFAULT_BLE_SCAN_DURATION = 10000L",
)

# ---------------------------------------------------------------------------
# 4) Resolve the LLM manager only after the explicit AI/auto-analysis gate.
# ---------------------------------------------------------------------------
processor = "app/src/main/java/com/flockyou/service/DetectionProcessor.kt"

exact(
    processor,
    """            Log.i(TAG, "Starting LLM warm-up in background...")
            val startTime = System.currentTimeMillis()

            // Check if LLM engine is available
            val activeEngine = llmEngineManager.activeEngine.value
            if (activeEngine == com.flockyou.ai.LlmEngine.RULE_BASED) {
                Log.d(TAG, "LLM warm-up skipped - using rule-based engine only")
                return@launch
            }

            // Run a simple test prompt to warm up the model
            // This forces the model to load into memory and JIT compile
            val warmupPrompt = "Classify: Is a device named 'TestDevice' a surveillance device? Answer YES or NO."
            val response = llmEngineManager.generateResponse(warmupPrompt)

            val elapsed = System.currentTimeMillis() - startTime
            if (response != null) {
                Log.i(TAG, "LLM warm-up completed in ${elapsed}ms (engine: ${llmEngineManager.activeEngine.value})")""",
    """            Log.i(TAG, "Starting LLM warm-up in background...")
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
                Log.i(TAG, "LLM warm-up completed in ${elapsed}ms (engine: ${engineManager.activeEngine.value})")""",
)

print("P5 constrained startup cleanup applied successfully")
