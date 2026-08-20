package com.flockyou.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Linearizes admission into the scanning lifecycle across Android service and IPC threads.
 * A claim spans STARTING through teardown; only markStopped() releases it.
 */
internal class ScanLifecycleGate {
    private val claimed = AtomicBoolean(false)

    fun tryBeginStart(): Boolean = claimed.compareAndSet(false, true)

    fun markStopped() {
        claimed.set(false)
    }
}
