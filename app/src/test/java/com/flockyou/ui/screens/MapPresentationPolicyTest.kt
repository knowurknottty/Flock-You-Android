package com.flockyou.ui.screens

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPresentationPolicyTest {

    @Test
    fun zoomBucket_changesOnlyAtMeaningfulThresholds() {
        assertEquals(MapZoomBucket.WORLD, MapZoomBucket.forZoom(4.0))
        assertEquals(MapZoomBucket.WORLD, MapZoomBucket.forZoom(7.99))
        assertEquals(MapZoomBucket.REGIONAL, MapZoomBucket.forZoom(8.0))
        assertEquals(MapZoomBucket.REGIONAL, MapZoomBucket.forZoom(11.99))
        assertEquals(MapZoomBucket.LOCAL, MapZoomBucket.forZoom(12.0))
        assertEquals(MapZoomBucket.LOCAL, MapZoomBucket.forZoom(14.99))
        assertEquals(MapZoomBucket.DETAIL, MapZoomBucket.forZoom(15.0))
    }

    @Test
    fun clusterDetections_groupsNearbyGridCellsWithoutDroppingRows() {
        val detections = listOf(
            detection("a", 34.1001, 1.0001, ThreatLevel.LOW),
            detection("b", 34.1002, 1.0002, ThreatLevel.CRITICAL),
            detection("c", 35.0, 2.0, ThreatLevel.MEDIUM)
        )

        val clusters = MapPresentationPolicy.clusterDetections(
            detections = detections,
            zoomBucket = MapZoomBucket.LOCAL
        )

        assertEquals(2, clusters.size)
        assertEquals(3, clusters.sumOf { it.detections.size })
        val first = clusters.first { it.detections.any { detection -> detection.id == "a" } }
        assertEquals(setOf("a", "b"), first.detections.map { it.id }.toSet())
        assertEquals(ThreatLevel.CRITICAL, first.highestThreatLevel)
    }

    @Test
    fun detailZoom_returnsSingleDetectionClusters() {
        val detections = listOf(
            detection("a", 34.1001, 1.0001, ThreatLevel.LOW),
            detection("b", 34.1002, 1.0002, ThreatLevel.HIGH)
        )

        val clusters = MapPresentationPolicy.clusterDetections(
            detections = detections,
            zoomBucket = MapZoomBucket.DETAIL
        )

        assertEquals(2, clusters.size)
        assertTrue(clusters.all { it.detections.size == 1 })
    }

    @Test
    fun filterDetections_appliesTimeProtocolThreatAndActiveFiltersAgainstOneClockSnapshot() {
        val now = 1_800_000_000_000L
        val state = MapUiState(
            filterThreatLevel = ThreatLevel.HIGH,
            filterProtocols = setOf(DetectionProtocol.BLUETOOTH_LE),
            filterTimeRange = TimeRange.LAST_24H,
            filterActiveOnly = true
        )
        val detections = listOf(
            detection("keep", 34.0, 1.0, ThreatLevel.HIGH, timestamp = now - 1_000L),
            detection("old", 34.0, 1.0, ThreatLevel.HIGH, timestamp = now - 90_000_000L),
            detection("wrong-threat", 34.0, 1.0, ThreatLevel.LOW, timestamp = now - 1_000L),
            detection("inactive", 34.0, 1.0, ThreatLevel.HIGH, timestamp = now - 1_000L, active = false),
            detection(
                "wrong-protocol",
                34.0,
                1.0,
                ThreatLevel.HIGH,
                timestamp = now - 1_000L,
                protocol = DetectionProtocol.WIFI
            )
        )

        val filtered = MapPresentationPolicy.filterDetections(detections, state, now)

        assertEquals(listOf("keep"), filtered.map { it.id })
    }

    @Test
    fun gpsStatus_isIdleWhenScannerIsNotRunningAndNoDetectionsExist() {
        assertEquals(
            MapGpsStatus.IDLE,
            MapPresentationPolicy.gpsStatus(
                hasLocatedDetections = false,
                hasAnyDetections = false,
                isScanning = false
            )
        )
    }

    @Test
    fun gpsStatus_searchesWhenScannerIsRunningWithoutLocatedDetections() {
        assertEquals(
            MapGpsStatus.SEARCHING,
            MapPresentationPolicy.gpsStatus(
                hasLocatedDetections = false,
                hasAnyDetections = false,
                isScanning = true
            )
        )
    }

    @Test
    fun emptyState_doesNotOfferStartScanningWhenScannerIsAlreadyRunning() {
        val presentation = MapPresentationPolicy.emptyStatePresentation(
            hasDetections = false,
            isScanning = true
        )

        assertEquals("Scanning...", presentation.actionLabel)
        assertFalse(presentation.actionEnabled)
    }

    private fun detection(
        id: String,
        latitude: Double,
        longitude: Double,
        threatLevel: ThreatLevel,
        timestamp: Long = 1_800_000_000_000L,
        active: Boolean = true,
        protocol: DetectionProtocol = DetectionProtocol.BLUETOOTH_LE
    ) = Detection(
        id = id,
        timestamp = timestamp,
        protocol = protocol,
        detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
        deviceType = DeviceType.TRACKING_DEVICE,
        rssi = -55,
        signalStrength = SignalStrength.GOOD,
        latitude = latitude,
        longitude = longitude,
        threatLevel = threatLevel,
        isActive = active,
        lastSeenTimestamp = timestamp
    )
}
