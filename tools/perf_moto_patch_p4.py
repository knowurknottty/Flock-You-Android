#!/usr/bin/env python3
"""P4 constrained-device pass: eliminate remaining radio hot-path allocation churn.

The patch is fail-closed: every mutation must match the verified P3 source exactly.
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
# 1) Service registries: keyed bounded state instead of list-copy + linear scan
# on every BLE advertisement / Wi-Fi result.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/ScanningService.kt"

exact(
    path,
    """        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L
        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L
        private const val HEARTBEAT_RECORD_INTERVAL_MS = 60_000L""",
    """        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L
        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L
        private const val SEEN_WIFI_PUBLISH_DELAY_MS = 250L
        private const val MAX_SEEN_DEVICE_REGISTRY_SIZE = 100
        private const val HEARTBEAT_RECORD_INTERVAL_MS = 60_000L""",
)

exact(
    path,
    """    private var bleProcessorJob: Job? = null
    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private var lastHeartbeatRecordElapsed = 0L""",
    """    private var bleProcessorJob: Job? = null
    private val seenBleRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private val seenWifiRegistry = java.util.LinkedHashMap<String, SeenDevice>(128, 0.75f, true)
    private var seenWifiPublishJob: Job? = null
    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private var lastHeartbeatRecordElapsed = 0L""",
)

exact(
    path,
    """    // Cross-domain correlation analysis job
    private var correlationAnalysisJob: Job? = null
    private val CORRELATION_ANALYSIS_INTERVAL_MS = 60_000L  // Run correlation analysis every 60 seconds""",
    """    // Cross-domain correlation analysis job
    private var correlationAnalysisJob: Job? = null
    private var lastCorrelationDetectionCount: Int = -1
    private val CORRELATION_ANALYSIS_INTERVAL_MS = 60_000L  // Run correlation analysis every 60 seconds""",
)

exact(
    path,
    """                    ScanningServiceIpc.MSG_CLEAR_SEEN_DEVICES -> {
                        clearSeenDevices()
                        broadcastSeenBleDevices()
                        broadcastSeenWifiNetworks()
                    }""",
    """                    ScanningServiceIpc.MSG_CLEAR_SEEN_DEVICES -> {
                        clearSeenDeviceRegistries()
                        broadcastSeenBleDevices()
                        broadcastSeenWifiNetworks()
                    }""",
)

old_seen_block = """    private fun cleanupSeenDevices(timeout: Long) {
        val cutoff = System.currentTimeMillis() - timeout
        val bleCountBefore = seenBleDevices.value.size
        val wifiCountBefore = seenWifiNetworks.value.size
        synchronized(ScanningServiceState) {
            seenBleDevices.value = seenBleDevices.value.filter { it.lastSeen > cutoff }
            seenWifiNetworks.value = seenWifiNetworks.value.filter { it.lastSeen > cutoff }
        }
        // Broadcast if devices were removed
        if (seenBleDevices.value.size != bleCountBefore) {
            broadcastSeenBleDevices()
        }
        if (seenWifiNetworks.value.size != wifiCountBefore) {
            broadcastSeenWifiNetworks()
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
        // Synchronize to prevent race conditions when multiple scan results arrive concurrently
        synchronized(ScanningServiceState) {
            val currentList = seenBleDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == macAddress }

            if (existingIndex >= 0) {
                // Update existing
                val existing = currentList[existingIndex]
                currentList[existingIndex] = existing.copy(
                    name = deviceName ?: existing.name,
                    rssi = rssi,
                    lastSeen = System.currentTimeMillis(),
                    seenCount = existing.seenCount + 1,
                    manufacturerData = if (manufacturerData.isNotEmpty()) manufacturerData else existing.manufacturerData,
                    advertisingRate = advertisingRate
                )
            } else {
                // Add new
                val manufacturer = try {
                    // Try to identify manufacturer from MAC OUI
                    val oui = macAddress.take(8).uppercase()
                    DetectionPatterns.getManufacturerFromOui(oui)
                } catch (e: Exception) { null }

                currentList.add(0, SeenDevice(
                    id = macAddress,
                    name = deviceName,
                    type = "BLE",
                    rssi = rssi,
                    manufacturer = manufacturer,
                    serviceUuids = serviceUuids.map { it.toString() },
                    manufacturerData = manufacturerData,
                    advertisingRate = advertisingRate
                ))

                // Limit list size
                if (currentList.size > 100) {
                    currentList.removeAt(currentList.lastIndex)
                }
            }

            seenBleDevices.value = currentList
            val now = System.currentTimeMillis()
            if (now - lastSeenBleBroadcastTime >= SEEN_DEVICE_BROADCAST_THROTTLE_MS) {
                lastSeenBleBroadcastTime = now
                broadcastSeenBleDevices()
            }
        }
    }

    private fun trackSeenWifiNetwork(bssid: String, ssid: String, rssi: Int) {
        val currentList = seenWifiNetworks.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == bssid }

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(
                name = ssid,
                rssi = rssi,
                lastSeen = System.currentTimeMillis(),
                seenCount = existing.seenCount + 1
            )
        } else {
            val manufacturer = try {
                val oui = bssid.take(8).uppercase()
                DetectionPatterns.getManufacturerFromOui(oui)
            } catch (e: Exception) { null }

            currentList.add(0, SeenDevice(
                id = bssid,
                name = ssid,
                type = "WiFi",
                rssi = rssi,
                manufacturer = manufacturer
            ))

            if (currentList.size > 100) {
                currentList.removeAt(currentList.lastIndex)
            }
        }

        seenWifiNetworks.value = currentList
        broadcastSeenWifiNetworks()
    }"""

new_seen_block = """    private fun trimSeenRegistry(registry: java.util.LinkedHashMap<String, SeenDevice>) {
        while (registry.size > MAX_SEEN_DEVICE_REGISTRY_SIZE) {
            val iterator = registry.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    private fun clearSeenDeviceRegistries() {
        seenWifiPublishJob?.cancel()
        seenWifiPublishJob = null
        synchronized(ScanningServiceState) {
            seenBleRegistry.clear()
            seenWifiRegistry.clear()
            clearSeenDevices()
        }
    }

    private fun cleanupSeenDevices(timeout: Long) {
        val cutoff = System.currentTimeMillis() - timeout
        var bleChanged = false
        var wifiChanged = false

        synchronized(ScanningServiceState) {
            val bleBefore = seenBleRegistry.size
            val bleIterator = seenBleRegistry.entries.iterator()
            while (bleIterator.hasNext()) {
                if (bleIterator.next().value.lastSeen <= cutoff) bleIterator.remove()
            }
            bleChanged = seenBleRegistry.size != bleBefore
            if (bleChanged) {
                seenBleDevices.value = seenBleRegistry.values.toList().asReversed()
            }

            val wifiBefore = seenWifiRegistry.size
            val wifiIterator = seenWifiRegistry.entries.iterator()
            while (wifiIterator.hasNext()) {
                if (wifiIterator.next().value.lastSeen <= cutoff) wifiIterator.remove()
            }
            wifiChanged = seenWifiRegistry.size != wifiBefore
            if (wifiChanged) {
                seenWifiNetworks.value = seenWifiRegistry.values.toList().asReversed()
            }
        }

        if (bleChanged) broadcastSeenBleDevices()
        if (wifiChanged) broadcastSeenWifiNetworks()
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

    private fun scheduleSeenWifiSnapshot() {
        synchronized(ScanningServiceState) {
            if (seenWifiPublishJob?.isActive == true) return
            seenWifiPublishJob = serviceScope.launch {
                delay(SEEN_WIFI_PUBLISH_DELAY_MS)
                synchronized(ScanningServiceState) {
                    seenWifiNetworks.value = seenWifiRegistry.values.toList().asReversed()
                    seenWifiPublishJob = null
                }
                broadcastSeenWifiNetworks()
            }
        }
    }

    private fun trackSeenWifiNetwork(bssid: String, ssid: String, rssi: Int) {
        val now = System.currentTimeMillis()
        synchronized(ScanningServiceState) {
            val existing = seenWifiRegistry[bssid]
            val updated = if (existing != null) {
                existing.copy(
                    name = ssid,
                    rssi = rssi,
                    lastSeen = now,
                    seenCount = existing.seenCount + 1
                )
            } else {
                val manufacturer = try {
                    DetectionPatterns.getManufacturerFromOui(bssid.take(8).uppercase())
                } catch (e: Exception) { null }

                SeenDevice(
                    id = bssid,
                    name = ssid,
                    type = "WiFi",
                    rssi = rssi,
                    manufacturer = manufacturer
                )
            }

            seenWifiRegistry[bssid] = updated
            trimSeenRegistry(seenWifiRegistry)
        }
        scheduleSeenWifiSnapshot()
    }"""

exact(path, old_seen_block, new_seen_block)

# ---------------------------------------------------------------------------
# 2) Honor the configured/battery-adaptive Wi-Fi interval. Previously the scanner
# always used a fixed 20 s base, making constrained-device defaults ineffective.
# ---------------------------------------------------------------------------
exact(
    path,
    """    private val MIN_WIFI_SCAN_INTERVAL_MS = 20_000L
    private val MAX_WIFI_SCAN_INTERVAL_MS = 120_000L

    @SuppressLint("MissingPermission")""",
    """    private val MIN_WIFI_SCAN_INTERVAL_MS = 20_000L
    private val MAX_WIFI_SCAN_INTERVAL_MS = 120_000L

    private fun getEffectiveWifiBaseIntervalMs(): Long {
        val configuredMs = currentScanSettings
            .getEffectiveWifiInterval(currentBatteryPercent)
            .toLong() * 1000L
        val boostedMs = if (isBoostModeActive) (configuredMs * 0.6f).toLong() else configuredMs
        return boostedMs.coerceIn(MIN_WIFI_SCAN_INTERVAL_MS, MAX_WIFI_SCAN_INTERVAL_MS)
    }

    @SuppressLint("MissingPermission")""",
)

exact(
    path,
    """        val adaptiveInterval = if (wifiScanAttemptsSinceSuccess > 0) {
            (MIN_WIFI_SCAN_INTERVAL_MS * (1 shl wifiScanAttemptsSinceSuccess.coerceAtMost(3)))
                .coerceAtMost(MAX_WIFI_SCAN_INTERVAL_MS)
        } else {
            MIN_WIFI_SCAN_INTERVAL_MS
        }""",
    """        val baseInterval = getEffectiveWifiBaseIntervalMs()
        val adaptiveInterval = if (wifiScanAttemptsSinceSuccess > 0) {
            (baseInterval * (1L shl wifiScanAttemptsSinceSuccess.coerceAtMost(3)))
                .coerceAtMost(MAX_WIFI_SCAN_INTERVAL_MS)
        } else {
            baseInterval
        }""",
)

exact(
    path,
    """                            val nextAllowedIn = MIN_WIFI_SCAN_INTERVAL_MS * (1 shl wifiScanAttemptsSinceSuccess.coerceAtMost(3))
                            wifiStatus.value = SubsystemStatus.Error(-2, "Throttled (backoff: ${nextAllowedIn/1000}s)")""",
    """                            val nextAllowedIn = (getEffectiveWifiBaseIntervalMs() *
                                (1L shl wifiScanAttemptsSinceSuccess.coerceAtMost(3)))
                                .coerceAtMost(MAX_WIFI_SCAN_INTERVAL_MS)
                            wifiStatus.value = SubsystemStatus.Error(-2, "Throttled (backoff: ${nextAllowedIn/1000}s)")""",
)

# Successful scans were counted once in the BroadcastReceiver and then a second
# time in processWifiScanResults(). Keep the receiver as the single success counter.
exact(
    path,
    """        scanStats.value = scanStats.value.copy(
            wifiNetworksSeen = scanStats.value.wifiNetworksSeen + results.size,
            successfulWifiScans = scanStats.value.successfulWifiScans + 1,
            lastWifiSuccessTime = System.currentTimeMillis()
        )""",
    """        scanStats.value = scanStats.value.copy(
            wifiNetworksSeen = scanStats.value.wifiNetworksSeen + results.size,
            lastWifiSuccessTime = System.currentTimeMillis()
        )""",
)

# ---------------------------------------------------------------------------
# 3) Skip the encrypted correlation query/analyzer when no new detection has
# arrived since the previous successful pass.
# ---------------------------------------------------------------------------
exact(
    path,
    """            while (isActive && isScanning.value) {
                try {
                    val sinceTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L
                    val recentDetections = if (currentPrivacySettings.ephemeralModeEnabled) {""",
    """            while (isActive && isScanning.value) {
                try {
                    val currentCount = detectionCount.value
                    if (currentCount == lastCorrelationDetectionCount) {
                        delay(CORRELATION_ANALYSIS_INTERVAL_MS)
                        continue
                    }

                    val sinceTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L
                    val recentDetections = if (currentPrivacySettings.ephemeralModeEnabled) {""",
)

exact(
    path,
    """                    crossDomainAnalyzer.cleanup()

                } catch (e: Exception) {""",
    """                    crossDomainAnalyzer.cleanup()
                    lastCorrelationDetectionCount = currentCount

                } catch (e: Exception) {""",
)

# ---------------------------------------------------------------------------
# 4) Packet-rate tracking: deque gives O(1) aging instead of scanning/removing a
# mutable array list on every packet.
# ---------------------------------------------------------------------------
state = "app/src/main/java/com/flockyou/service/ScanningServiceState.kt"

exact(
    state,
    """    private val devicePacketCounts = java.util.concurrent.ConcurrentHashMap<String, MutableList<Long>>()""",
    """    private val devicePacketCounts = java.util.concurrent.ConcurrentHashMap<String, java.util.ArrayDeque<Long>>()""",
)

exact(
    state,
    """            val packets = devicePacketCounts.getOrPut(macAddress) { mutableListOf() }
            packets.add(now)

            val iterator = packets.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() < cutoff) {
                    iterator.remove()
                }
            }

            if (packets.size > 1) {""",
    """            val packets = devicePacketCounts.getOrPut(macAddress) { java.util.ArrayDeque() }
            packets.addLast(now)

            while (packets.isNotEmpty() && packets.peekFirst() < cutoff) {
                packets.removeFirst()
            }

            if (packets.size > 1) {""",
)

# ---------------------------------------------------------------------------
# 5) Regression coverage: verify adaptive Wi-Fi timing is no longer decorative.
# ---------------------------------------------------------------------------
test = "app/src/test/java/com/flockyou/data/ScanSettingsTest.kt"
exact(
    test,
    """    @Test
    fun manualPerformance_remainsAvailable() {
        val settings = ScanSettings(batteryAdaptiveMode = "performance", autoBatteryAdaptive = false)
        assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
    }
}""",
    """    @Test
    fun manualPerformance_remainsAvailable() {
        val settings = ScanSettings(batteryAdaptiveMode = "performance", autoBatteryAdaptive = false)
        assertEquals(BatteryAdaptiveMode.PERFORMANCE, settings.getEffectiveMode(100))
    }

    @Test
    fun effectiveWifiInterval_respectsConfiguredBaseAndBatteryMode() {
        val balanced = ScanSettings(
            wifiScanIntervalSeconds = 45,
            batteryAdaptiveMode = "balanced",
            autoBatteryAdaptive = false
        )
        assertEquals(45, balanced.getEffectiveWifiInterval(100))

        val saver = balanced.copy(batteryAdaptiveMode = "battery_saver")
        assertEquals(67, saver.getEffectiveWifiInterval(100))

        val performance = balanced.copy(batteryAdaptiveMode = "performance")
        assertEquals(22, performance.getEffectiveWifiInterval(100))
    }
}""",
)

print("P4 BLE/Wi-Fi hot-path optimization applied successfully")
