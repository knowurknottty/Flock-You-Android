package com.flockyou.ui.screens

import com.flockyou.data.model.Detection
import com.flockyou.data.model.ThreatLevel
import kotlin.math.floor

/**
 * Coarse map zoom tiers used to avoid rebuilding every marker for every fractional zoom change.
 *
 * Each clustered tier uses a fixed geographic grid size. DETAIL intentionally disables
 * clustering so nearby individual detections remain selectable when the operator zooms in.
 */
enum class MapZoomBucket(val clusterCellSizeDegrees: Double?) {
    WORLD(0.05),      // ~5.5 km latitude cells
    REGIONAL(0.01),   // ~1.1 km
    LOCAL(0.002),     // ~220 m
    DETAIL(null);

    companion object {
        fun forZoom(zoom: Double): MapZoomBucket = when {
            zoom < 8.0 -> WORLD
            zoom < 12.0 -> REGIONAL
            zoom < 15.0 -> LOCAL
            else -> DETAIL
        }
    }
}

data class MapDetectionCluster(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val detections: List<Detection>,
    val highestThreatLevel: ThreatLevel
)

data class MapEmptyStatePresentation(
    val title: String,
    val body: String,
    val actionLabel: String,
    val actionEnabled: Boolean
)

enum class MapGpsStatus {
    ACTIVE, SEARCHING, IDLE, DISABLED
}

/**
 * Pure presentation policy shared by MapViewModel and MapScreen.
 *
 * Keeping filtering and clustering Android-free makes both algorithms deterministic and cheap to
 * unit-test. Grid clustering is O(n) expected work and bounded per detection; unlike the previous
 * pairwise scan, dense histories do not compare every detection with every other detection.
 */
object MapPresentationPolicy {

    fun gpsStatus(
        hasLocatedDetections: Boolean,
        hasAnyDetections: Boolean,
        isScanning: Boolean
    ): MapGpsStatus {
        // Initial extraction intentionally preserves the current behavior.
        @Suppress("UNUSED_VARIABLE")
        val scanningState = isScanning
        return when {
            hasLocatedDetections -> MapGpsStatus.ACTIVE
            hasAnyDetections -> MapGpsStatus.DISABLED
            else -> MapGpsStatus.SEARCHING
        }
    }

    fun emptyStatePresentation(
        hasDetections: Boolean,
        isScanning: Boolean
    ): MapEmptyStatePresentation {
        // Initial extraction intentionally preserves the current behavior.
        // The scanning-state truthfulness regression is pinned separately before changing it.
        @Suppress("UNUSED_VARIABLE")
        val scanningState = isScanning
        return if (hasDetections) {
            MapEmptyStatePresentation(
                title = "No Location Data",
                body = "Detections were found but none have location data. Enable GPS permission to see detections on the map.",
                actionLabel = "Enable Location",
                actionEnabled = true
            )
        } else {
            MapEmptyStatePresentation(
                title = "No Detections Yet",
                body = "Start scanning to detect surveillance devices. They will appear on the map when found.",
                actionLabel = "Start Scanning",
                actionEnabled = true
            )
        }
    }

    fun filterDetections(
        detections: List<Detection>,
        state: MapUiState,
        nowMillis: Long
    ): List<Detection> {
        val cutoff = when (state.filterTimeRange) {
            TimeRange.ALL_TIME, TimeRange.CUSTOM -> null
            else -> nowMillis - (state.filterTimeRange.durationMs ?: 0L)
        }
        val customStart = state.filterCustomStartTime ?: 0L
        val customEnd = state.filterCustomEndTime ?: Long.MAX_VALUE

        return detections.filter { detection ->
            val threatPass = state.filterThreatLevel?.let { detection.threatLevel == it } ?: true
            val typePass = state.filterDeviceTypes.isEmpty() || detection.deviceType in state.filterDeviceTypes
            val protocolPass = state.filterProtocols.isEmpty() || detection.protocol in state.filterProtocols
            val timePass = when (state.filterTimeRange) {
                TimeRange.ALL_TIME -> true
                TimeRange.CUSTOM -> detection.timestamp in customStart..customEnd
                else -> detection.timestamp >= (cutoff ?: Long.MIN_VALUE)
            }
            val signalPass = state.filterSignalStrength.isEmpty() ||
                detection.signalStrength in state.filterSignalStrength
            val activePass = !state.filterActiveOnly || detection.isActive

            val threatTypePass = if (state.filterMatchAll) {
                threatPass && typePass
            } else if (state.filterThreatLevel != null && state.filterDeviceTypes.isNotEmpty()) {
                threatPass || typePass
            } else {
                threatPass && typePass
            }

            threatTypePass && protocolPass && timePass && signalPass && activePass
        }
    }

    fun clusterDetections(
        detections: List<Detection>,
        zoomBucket: MapZoomBucket
    ): List<MapDetectionCluster> {
        if (detections.isEmpty()) return emptyList()

        val cellSize = zoomBucket.clusterCellSizeDegrees
        if (cellSize == null) {
            return detections.mapNotNull { detection ->
                val latitude = detection.latitude ?: return@mapNotNull null
                val longitude = detection.longitude ?: return@mapNotNull null
                MapDetectionCluster(
                    centerLatitude = latitude,
                    centerLongitude = longitude,
                    detections = listOf(detection),
                    highestThreatLevel = detection.threatLevel
                )
            }
        }

        data class CellKey(val latitudeCell: Long, val longitudeCell: Long)
        data class Bucket(
            val detections: MutableList<Detection> = mutableListOf(),
            var latitudeSum: Double = 0.0,
            var longitudeSum: Double = 0.0,
            var highestThreatLevel: ThreatLevel = ThreatLevel.INFO
        )

        // LinkedHashMap preserves first-seen ordering so marker ordering remains deterministic.
        val buckets = LinkedHashMap<CellKey, Bucket>()

        detections.forEach { detection ->
            val latitude = detection.latitude ?: return@forEach
            val longitude = detection.longitude ?: return@forEach
            val key = CellKey(
                latitudeCell = floor(latitude / cellSize).toLong(),
                longitudeCell = floor(longitude / cellSize).toLong()
            )
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.detections += detection
            bucket.latitudeSum += latitude
            bucket.longitudeSum += longitude
            if (threatRank(detection.threatLevel) > threatRank(bucket.highestThreatLevel)) {
                bucket.highestThreatLevel = detection.threatLevel
            }
        }

        return buckets.values.map { bucket ->
            val count = bucket.detections.size.coerceAtLeast(1)
            MapDetectionCluster(
                centerLatitude = bucket.latitudeSum / count,
                centerLongitude = bucket.longitudeSum / count,
                detections = bucket.detections.toList(),
                highestThreatLevel = bucket.highestThreatLevel
            )
        }
    }

    private fun threatRank(level: ThreatLevel): Int = when (level) {
        ThreatLevel.CRITICAL -> 4
        ThreatLevel.HIGH -> 3
        ThreatLevel.MEDIUM -> 2
        ThreatLevel.LOW -> 1
        ThreatLevel.INFO -> 0
    }
}
