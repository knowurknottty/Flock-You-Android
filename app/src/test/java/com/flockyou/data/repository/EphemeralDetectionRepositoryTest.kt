package com.flockyou.data.repository

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralDetectionRepositoryTest {

    private fun repo() = EphemeralDetectionRepository(DetectionDeduplicator())

    // =====================================================================================
    // Ordering / insertion
    // =====================================================================================

    @Test
    fun `insertDetection prepends so newest is first`() = runBlocking {
        val r = repo()
        r.insertDetection(detection(id = "first"))
        r.insertDetection(detection(id = "second"))
        val ids = r.getAllDetectionsSnapshot().map { it.id }
        assertEquals(listOf("second", "first"), ids)
    }

    @Test
    fun `insertDetections prepends batch preserving internal order`() = runBlocking {
        val r = repo()
        r.insertDetection(detection(id = "existing"))
        r.insertDetections(listOf(detection(id = "a"), detection(id = "b"), detection(id = "c")))
        val ids = r.getAllDetectionsSnapshot().map { it.id }
        assertEquals(listOf("a", "b", "c", "existing"), ids)
    }

    // =====================================================================================
    // Hard memory bound
    // =====================================================================================

    @Test
    fun `insertDetections enforces the hard capacity bound`() = runBlocking {
        val r = repo()
        val batch = (0..10_000).map { detection(id = "d$it") }
        r.insertDetections(batch)
        assertEquals(10_000, r.getAllDetectionsSnapshot().size)
        val ids = r.getAllDetectionsSnapshot().map { it.id }.toSet()
        assertTrue(ids.contains("d0"))
        assertFalse(ids.contains("d10000")) // oldest evicted
    }

    // =====================================================================================
    // Upsert / deduplication
    // =====================================================================================

    @Test
    fun `upsertDetection returns true for new detection`() = runBlocking {
        val r = repo()
        assertTrue(r.upsertDetection(detection(id = "new", mac = "AA:BB:CC:DD:EE:FF")))
    }

    @Test
    fun `upsertDetection dedupes by MAC and increments seenCount`() = runBlocking {
        val r = repo()
        val mac = "AA:BB:CC:DD:EE:FF"
        // Seed the list directly (insertDetection does not touch the throttle cache), then
        // upsert a matching detection. The first upsert is not throttled and dedupes by MAC.
        r.insertDetection(detection(id = "a", mac = mac, seenCount = 1))
        val isNew = r.upsertDetection(detection(id = "b", mac = mac, seenCount = 1))
        assertFalse(isNew)
        val stored = r.getDetectionByMacAddress(mac)!!
        assertEquals(2, stored.seenCount)
    }

    @Test
    fun `upsertDetection throttles a rapid duplicate of the same MAC`() = runBlocking {
        val r = repo()
        val mac = "AA:BB:CC:DD:EE:FF"
        assertTrue(r.upsertDetection(detection(id = "a", mac = mac)))
        // Immediate second detection of the same MAC falls inside the throttle window.
        assertFalse(r.upsertDetection(detection(id = "b", mac = mac)))
        assertEquals(1, r.getAllDetectionsSnapshot().size)
    }

    // =====================================================================================
    // Thread safety
    // =====================================================================================

    @Test
    fun `concurrent inserts do not lose detections`() = runBlocking {
        val r = repo()
        val n = 500
        (0 until n).map { i ->
            async(Dispatchers.Default) { r.insertDetection(detection(id = "c$i")) }
        }.awaitAll()
        assertEquals(n, r.getAllDetectionsSnapshot().size)
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    @Test
    fun `markOldInactive only deactivates stale detections`() = runBlocking {
        val r = repo()
        r.insertDetection(detection(id = "old", lastSeen = 1000L))
        r.insertDetection(detection(id = "recent", lastSeen = 5000L))
        r.markOldInactive(3000L)
        val byId = r.getAllDetectionsSnapshot().associateBy { it.id }
        assertFalse(byId["old"]!!.isActive)
        assertTrue(byId["recent"]!!.isActive)
    }

    @Test
    fun `clearAll empties the repository`() = runBlocking {
        val r = repo()
        r.insertDetection(detection(id = "a"))
        r.insertDetection(detection(id = "b"))
        r.clearAll()
        assertEquals(0, r.getAllDetectionsSnapshot().size)
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private fun detection(
        id: String,
        mac: String? = null,
        seenCount: Int = 1,
        lastSeen: Long = System.currentTimeMillis(),
    ): Detection = Detection(
        id = id,
        timestamp = lastSeen,
        protocol = DetectionProtocol.WIFI,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
        rssi = -60,
        signalStrength = SignalStrength.GOOD,
        threatLevel = ThreatLevel.HIGH,
        macAddress = mac,
        seenCount = seenCount,
        lastSeenTimestamp = lastSeen,
    )
}
