package com.flockyou.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                    assertTrue("Both contenders should rendezvous", fire.await(2, TimeUnit.SECONDS))
                    if (gate.tryBeginStart()) {
                        winners.incrementAndGet()
                    }
                }
            }

            assertTrue("Both contenders should be ready", ready.await(2, TimeUnit.SECONDS))
            fire.countDown()
            futures.forEach { future -> future.get(2, TimeUnit.SECONDS) }
            executor.shutdownNow()

            assertEquals("Exactly one start caller may own the lifecycle", 1, winners.get())
        }
    }

    @Test
    fun stoppedLifecycle_canBeStartedAgain() {
        val gate = ScanLifecycleGate()

        assertTrue(gate.tryBeginStart())
        assertFalse(gate.tryBeginStart())

        gate.markStopped()

        assertTrue(gate.tryBeginStart())
    }
}
