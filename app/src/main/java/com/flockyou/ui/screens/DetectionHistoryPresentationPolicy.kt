package com.flockyou.ui.screens

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel

/**
 * Narrow, immutable projection of the MainUiState fields that can actually change detection-history
 * filtering/sorting. Keeping this separate prevents unrelated scanner/RF/GNSS/Flipper telemetry
 * updates from forcing a full history filter + sort pass.
 */
data class DetectionHistoryQuery(
    val filterThreatLevel: ThreatLevel? = null,
    val filterDeviceTypes: Set<DeviceType> = emptySet(),
    val filterMatchAll: Boolean = true,
    val hideFalsePositives: Boolean = true,
    val fpFilterThreshold: Float = 0.6f,
    val filterProtocols: Set<DetectionProtocol> = emptySet(),
    val filterTimeRange: TimeRange = TimeRange.ALL_TIME,
    val filterCustomStartTime: Long? = null,
    val filterCustomEndTime: Long? = null,
    val filterSignalStrength: Set<SignalStrength> = emptySet(),
    val filterActiveOnly: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val searchQuery: String = ""
) {
    companion object {
        fun from(state: MainUiState): DetectionHistoryQuery = DetectionHistoryQuery(
            filterThreatLevel = state.filterThreatLevel,
            filterDeviceTypes = state.filterDeviceTypes,
            filterMatchAll = state.filterMatchAll,
            hideFalsePositives = state.hideFalsePositives,
            fpFilterThreshold = state.fpFilterThreshold,
            filterProtocols = state.filterProtocols,
            filterTimeRange = state.filterTimeRange,
            filterCustomStartTime = state.filterCustomStartTime,
            filterCustomEndTime = state.filterCustomEndTime,
            filterSignalStrength = state.filterSignalStrength,
            filterActiveOnly = state.filterActiveOnly,
            sortOrder = state.sortOrder,
            searchQuery = state.searchQuery
        )
    }
}

object DetectionHistoryPresentationPolicy {
    fun filterAndSort(
        detections: List<Detection>,
        query: DetectionHistoryQuery,
        nowMillis: Long
    ): List<Detection> {
        val cutoff = when (query.filterTimeRange) {
            TimeRange.ALL_TIME, TimeRange.CUSTOM -> null
            else -> nowMillis - (query.filterTimeRange.durationMs ?: 0L)
        }
        val customStart = query.filterCustomStartTime ?: 0L
        val customEnd = query.filterCustomEndTime ?: Long.MAX_VALUE
        val normalizedSearch = query.searchQuery.takeIf { it.isNotBlank() }?.lowercase()

        val filtered = detections.filter { detection ->
            val fpPass = !query.hideFalsePositives ||
                (detection.fpScore ?: 0f) < query.fpFilterThreshold
            val threatPass = query.filterThreatLevel?.let { detection.threatLevel == it } ?: true
            val typePass = query.filterDeviceTypes.isEmpty() || detection.deviceType in query.filterDeviceTypes
            val protocolPass = query.filterProtocols.isEmpty() || detection.protocol in query.filterProtocols
            val timePass = when (query.filterTimeRange) {
                TimeRange.ALL_TIME -> true
                TimeRange.CUSTOM -> detection.timestamp in customStart..customEnd
                else -> detection.timestamp >= (cutoff ?: Long.MIN_VALUE)
            }
            val signalPass = query.filterSignalStrength.isEmpty() ||
                detection.signalStrength in query.filterSignalStrength
            val activePass = !query.filterActiveOnly || detection.isActive
            val searchPass = normalizedSearch == null ||
                detection.deviceType.displayName.lowercase().contains(normalizedSearch) ||
                (detection.macAddress?.lowercase()?.contains(normalizedSearch) == true) ||
                (detection.ssid?.lowercase()?.contains(normalizedSearch) == true) ||
                (detection.deviceName?.lowercase()?.contains(normalizedSearch) == true) ||
                (detection.manufacturer?.lowercase()?.contains(normalizedSearch) == true) ||
                (detection.userNote?.lowercase()?.contains(normalizedSearch) == true)

            val threatTypePass = if (query.filterMatchAll) {
                threatPass && typePass
            } else if (query.filterThreatLevel != null && query.filterDeviceTypes.isNotEmpty()) {
                threatPass || typePass
            } else {
                threatPass && typePass
            }

            fpPass && threatTypePass && protocolPass && timePass && signalPass && activePass && searchPass
        }

        return when (query.sortOrder) {
            SortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestamp }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
            SortOrder.THREAT_SCORE_DESC -> filtered.sortedByDescending { it.threatScore }
            SortOrder.SIGNAL_STRENGTH_DESC -> filtered.sortedByDescending { it.rssi }
            SortOrder.SEEN_COUNT_DESC -> filtered.sortedByDescending { it.seenCount }
        }
    }
}
