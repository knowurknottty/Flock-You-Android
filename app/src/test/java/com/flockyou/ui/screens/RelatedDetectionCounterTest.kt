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

class RelatedDetectionCounterTest {

    @Test
    fun sameMac_isRelatedRegardlessOfTypeProtocolOrTime() {
        val detections = listOf(
            detection("a", 0L, "AA", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE),
            detection("b", 9_999_999L, "AA", DeviceType.ROGUE_AP, DetectionProtocol.WIFI)
        )

        assertEquals(mapOf("a" to 1, "b" to 1), RelatedDetectionCounter.compute(detections))
    }

    @Test
    fun sameTypeAndProtocolWithinTenMinutes_isRelatedWithoutMacMatch() {
        val detections = listOf(
            detection("a", 1_000_000L, "AA", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE),
            detection("b", 1_599_999L, "BB", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE),
            detection("boundary", 1_600_000L, "CC", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE)
        )

        assertEquals(mapOf("a" to 1, "b" to 2, "boundary" to 1), RelatedDetectionCounter.compute(detections))
    }

    @Test
    fun sameMacInsideTimeWindow_isCountedOnceNotTwice() {
        val detections = listOf(
            detection("a", 1_000L, "AA", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE),
            detection("b", 2_000L, "AA", DeviceType.TRACKING_DEVICE, DetectionProtocol.BLUETOOTH_LE)
        )

        assertEquals(mapOf("a" to 1, "b" to 1), RelatedDetectionCounter.compute(detections))
    }

    @Test
    fun optimizedCounter_matchesOriginalNestedLoopSemantics() {
        val random = Random(0xF10C)
        val deviceTypes = listOf(DeviceType.TRACKING_DEVICE, DeviceType.ROGUE_AP, DeviceType.DRONE)
        val protocols = listOf(DetectionProtocol.BLUETOOTH_LE, DetectionProtocol.WIFI, DetectionProtocol.CELLULAR)
        val macs = listOf<String?>(null, "AA", "BB", "CC", "DD")
        val detections = List(250) { index ->
            detection(
                id = "d$index",
                timestamp = 1_800_000_000_000L + random.nextLong(0L, 4_000_000L),
                mac = macs[random.nextInt(macs.size)],
                deviceType = deviceTypes[random.nextInt(deviceTypes.size)],
                protocol = protocols[random.nextInt(protocols.size)]
            )
        }

        assertEquals(bruteForce(detections), RelatedDetectionCounter.compute(detections))
    }

    private fun bruteForce(detections: List<Detection>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (detection in detections) {
            var related = 0
            for (other in detections) {
                if (other.id == detection.id) continue
                if (detection.macAddress != null && detection.macAddress == other.macAddress) {
                    related++
                    continue
                }
                if (
                    detection.deviceType == other.deviceType &&
                    detection.protocol == other.protocol &&
                    kotlin.math.abs(detection.timestamp - other.timestamp) < 600_000L
                ) {
                    related++
                }
            }
            if (related > 0) counts[detection.id] = related
        }
        return counts
    }

    private fun detection(
        id: String,
        timestamp: Long,
        mac: String?,
        deviceType: DeviceType,
        protocol: DetectionProtocol
    ) = Detection(
        id = id,
        timestamp = timestamp,
        protocol = protocol,
        detectionMethod = DetectionMethod.BLE_DEVICE_NAME,
        deviceType = deviceType,
        macAddress = mac,
        rssi = -60,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.LOW,
        lastSeenTimestamp = timestamp
    )
}
