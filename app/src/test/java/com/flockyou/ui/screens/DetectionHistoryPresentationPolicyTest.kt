package com.flockyou.ui.screens

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class DetectionHistoryPresentationPolicyTest {

    @Test
    fun filterAndSort_matchesLegacySemanticsAcrossMixedDataset() {
        val now = 1_800_000_000_000L
        val random = Random(0xCA7)
        val detections = List(300) { index ->
            Detection(
                id = "d$index",
                timestamp = now - random.nextLong(0L, 4_000_000L),
                protocol = DetectionProtocol.entries[random.nextInt(DetectionProtocol.entries.size)],
                detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
                deviceType = listOf(DeviceType.TRACKING_DEVICE, DeviceType.ROGUE_AP, DeviceType.DRONE)[random.nextInt(3)],
                deviceName = if (index % 5 == 0) "Beacon-$index" else null,
                macAddress = if (index % 3 == 0) "AA:${index % 17}" else null,
                ssid = if (index % 7 == 0) "TestNet-$index" else null,
                rssi = random.nextInt(-100, -30),
                signalStrength = SignalStrength.entries[random.nextInt(SignalStrength.entries.size)],
                threatLevel = ThreatLevel.entries[random.nextInt(ThreatLevel.entries.size)],
                threatScore = random.nextInt(0, 101),
                manufacturer = if (index % 11 == 0) "Vendor-$index" else null,
                isActive = index % 4 != 0,
                seenCount = random.nextInt(1, 20),
                lastSeenTimestamp = now - random.nextLong(0L, 4_000_000L),
                fpScore = if (index % 6 == 0) 0.9f else if (index % 8 == 0) 0.3f else null,
                userNote = if (index % 13 == 0) "watch this beacon" else null
            )
        }
        val query = DetectionHistoryQuery(
            filterThreatLevel = ThreatLevel.HIGH,
            filterDeviceTypes = setOf(DeviceType.TRACKING_DEVICE, DeviceType.ROGUE_AP),
            filterMatchAll = false,
            hideFalsePositives = true,
            fpFilterThreshold = 0.6f,
            filterProtocols = setOf(DetectionProtocol.BLUETOOTH_LE, DetectionProtocol.WIFI),
            filterTimeRange = TimeRange.LAST_HOUR,
            filterSignalStrength = setOf(SignalStrength.GOOD, SignalStrength.MEDIUM),
            filterActiveOnly = true,
            sortOrder = SortOrder.THREAT_SCORE_DESC,
            searchQuery = "beacon"
        )

        assertEquals(
            legacyFilterAndSort(detections, query, now).map { it.id },
            DetectionHistoryPresentationPolicy.filterAndSort(detections, query, now).map { it.id }
        )
    }

    @Test
    fun customRange_isInclusiveAndNewestSortIsStableByTimestampBehavior() {
        val query = DetectionHistoryQuery(
            filterTimeRange = TimeRange.CUSTOM,
            filterCustomStartTime = 100L,
            filterCustomEndTime = 200L,
            sortOrder = SortOrder.NEWEST_FIRST
        )
        val detections = listOf(
            detection("before", 99L),
            detection("start", 100L),
            detection("middle", 150L),
            detection("end", 200L),
            detection("after", 201L)
        )

        assertEquals(
            listOf("end", "middle", "start"),
            DetectionHistoryPresentationPolicy.filterAndSort(detections, query, 999L).map { it.id }
        )
    }

    private fun legacyFilterAndSort(
        detections: List<Detection>,
        state: DetectionHistoryQuery,
        nowMillis: Long
    ): List<Detection> {
        val filtered = detections.filter { detection ->
            val fpPass = if (state.hideFalsePositives) {
                (detection.fpScore ?: 0f) < state.fpFilterThreshold
            } else true
            val threatPass = state.filterThreatLevel?.let { detection.threatLevel == it } ?: true
            val typePass = state.filterDeviceTypes.isEmpty() || detection.deviceType in state.filterDeviceTypes
            val protocolPass = state.filterProtocols.isEmpty() || detection.protocol in state.filterProtocols
            val timePass = when (state.filterTimeRange) {
                TimeRange.ALL_TIME -> true
                TimeRange.CUSTOM -> detection.timestamp in
                    (state.filterCustomStartTime ?: 0L)..(state.filterCustomEndTime ?: Long.MAX_VALUE)
                else -> detection.timestamp >= nowMillis - (state.filterTimeRange.durationMs ?: 0L)
            }
            val signalPass = state.filterSignalStrength.isEmpty() || detection.signalStrength in state.filterSignalStrength
            val activePass = !state.filterActiveOnly || detection.isActive
            val searchPass = if (state.searchQuery.isBlank()) true else {
                val query = state.searchQuery.lowercase()
                detection.deviceType.displayName.lowercase().contains(query) ||
                    (detection.macAddress?.lowercase()?.contains(query) == true) ||
                    (detection.ssid?.lowercase()?.contains(query) == true) ||
                    (detection.deviceName?.lowercase()?.contains(query) == true) ||
                    (detection.manufacturer?.lowercase()?.contains(query) == true) ||
                    (detection.userNote?.lowercase()?.contains(query) == true)
            }
            val threatTypePass = if (state.filterMatchAll) {
                threatPass && typePass
            } else if (state.filterThreatLevel != null && state.filterDeviceTypes.isNotEmpty()) {
                threatPass || typePass
            } else {
                threatPass && typePass
            }
            fpPass && threatTypePass && protocolPass && timePass && signalPass && activePass && searchPass
        }
        return when (state.sortOrder) {
            SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestamp }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
            SortOrder.THREAT_SCORE_DESC -> filtered.sortedByDescending { it.threatScore }
            SortOrder.SIGNAL_STRENGTH_DESC -> filtered.sortedByDescending { it.rssi }
            SortOrder.SEEN_COUNT_DESC -> filtered.sortedByDescending { it.seenCount }
        }
    }

    private fun detection(id: String, timestamp: Long) = Detection(
        id = id,
        timestamp = timestamp,
        protocol = DetectionProtocol.BLUETOOTH_LE,
        detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
        deviceType = DeviceType.TRACKING_DEVICE,
        rssi = -55,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.LOW,
        lastSeenTimestamp = timestamp
    )
}
