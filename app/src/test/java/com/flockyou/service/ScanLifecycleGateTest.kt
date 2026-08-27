package com.flockyou.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScanLifecycleGateTest {
    @Test
    fun concurrentStartAdmission_allowsExactlyOneCaller() {
        repeat(100) {
            val gate = ScanLifecycleGate()
            val ready = CountDownLatch(2)
            val fire = CountDownLatch(1)
            val winners = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(2)
            val futures = List(2) {
                executor.submit {
                    ready.countDown()
                    assertTrue(fire.await(2, TimeUnit.SECONDS))
                    if (gate.tryBeginStart() != null) winners.incrementAndGet()
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            fire.countDown()
            futures.forEach { it.get(2, TimeUnit.SECONDS) }
            executor.shutdownNow()
            assertTrue("Exactly one caller may own startup", winners.get() == 1)
        }
    }

    @Test
    fun staleStopFromPreviousLifecycle_doesNotUnlockReplacementLifecycle() {
        val gate = ScanLifecycleGate()
        val first = assertNotNullClaim(gate.tryBeginStart())
        assertTrue(gate.markActive(first))
        assertTrue(gate.tryBeginStop(first))
        assertTrue(gate.markStopped(first))

        val replacement = assertNotNullClaim(gate.tryBeginStart())
        assertFalse("Stale teardown must be rejected", gate.markStopped(first))
        assertNull("Replacement remains owned", gate.tryBeginStart())
        assertTrue(gate.isOwnedBy(replacement))
    }

    @Test
    fun stopDuringStartup_blocksReplacementUntilTeardownCompletes() {
        val gate = ScanLifecycleGate()
        val claim = assertNotNullClaim(gate.tryBeginStart())
        assertTrue(gate.tryBeginStop(claim))
        assertNull(gate.tryBeginStart())
        assertTrue(gate.markStopped(claim))
        assertNotNull(gate.tryBeginStart())
    }

    @Test
    fun failedTeardown_keepsLifecycleFailClosed() {
        val gate = ScanLifecycleGate()
        val claim = assertNotNullClaim(gate.tryBeginStart())
        assertTrue(gate.markActive(claim))
        assertTrue(gate.tryBeginStop(claim))

        assertFalse(gate.completeStop(claim, teardownSucceeded = false))
        assertNull("Failed teardown must keep new starts blocked", gate.tryBeginStart())

        assertTrue(gate.completeStop(claim, teardownSucceeded = true))
        assertNotNull(gate.tryBeginStart())
    }

    @Test
    fun failedStartup_releasesItsOwnClaimOnly() {
        val gate = ScanLifecycleGate()
        val first = assertNotNullClaim(gate.tryBeginStart())
        assertTrue(gate.failStart(first))
        val replacement = assertNotNullClaim(gate.tryBeginStart())
        assertFalse(gate.failStart(first))
        assertTrue(gate.isOwnedBy(replacement))
    }

    private fun assertNotNullClaim(value: ScanLifecycleGate.Claim?): ScanLifecycleGate.Claim {
        assertNotNull(value)
        return value!!
    }
}
