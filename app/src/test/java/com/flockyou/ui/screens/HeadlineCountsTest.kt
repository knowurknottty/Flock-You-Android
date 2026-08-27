package com.flockyou.ui.screens

import com.flockyou.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadlineCountsTest {
    private fun detection(level: ThreatLevel) = Detection(
        protocol = DetectionProtocol.WIFI,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
        rssi = -50,
        signalStrength = SignalStrength.EXCELLENT,
        threatLevel = level
    )

    @Test fun `headline counts reflect fresh snapshot`() {
        val counts = calculateHeadlineCounts(listOf(
            detection(ThreatLevel.INFO),
            detection(ThreatLevel.HIGH),
            detection(ThreatLevel.CRITICAL),
            detection(ThreatLevel.LOW)
        ))
        assertEquals(4, counts.total)
        assertEquals(2, counts.highThreat)
    }
}
