package com.flockyou.service

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Linearizes scanner lifecycle ownership across service-main, IPC, watchdog, and teardown threads.
 * Every lifecycle transition is bound to an immutable claim so stale work cannot unlock a newer run.
 */
internal class ScanLifecycleGate {
    @JvmInline
    value class Claim internal constructor(val generation: Long)

    private enum class Phase { IDLE, STARTING, ACTIVE, STOPPING }
    private data class State(val phase: Phase, val claim: Claim?)

    private val nextGeneration = AtomicLong(0L)
    private val state = AtomicReference(State(Phase.IDLE, null))

    fun tryBeginStart(): Claim? {
        while (true) {
            val current = state.get()
            if (current.phase != Phase.IDLE) return null
            val claim = Claim(nextGeneration.incrementAndGet())
            if (state.compareAndSet(current, State(Phase.STARTING, claim))) return claim
        }
    }

    fun markActive(claim: Claim): Boolean =
        transition(claim, from = setOf(Phase.STARTING), to = Phase.ACTIVE)

    fun tryBeginStop(claim: Claim): Boolean =
        transition(claim, from = setOf(Phase.STARTING, Phase.ACTIVE), to = Phase.STOPPING)

    fun failStart(claim: Claim): Boolean =
        transition(claim, from = setOf(Phase.STARTING), to = Phase.IDLE, clearClaim = true)

    fun markStopped(claim: Claim): Boolean =
        transition(claim, from = setOf(Phase.STOPPING), to = Phase.IDLE, clearClaim = true)

    /**
     * Releases lifecycle ownership only after teardown completed coherently.
     * Failed teardown remains STOPPING so no replacement scanner can overlap stale resources.
     */
    fun completeStop(claim: Claim, teardownSucceeded: Boolean): Boolean {
        if (!teardownSucceeded) return false
        return markStopped(claim)
    }

    fun isOwnedBy(claim: Claim): Boolean = state.get().claim == claim

    private fun transition(
        claim: Claim,
        from: Set<Phase>,
        to: Phase,
        clearClaim: Boolean = false
    ): Boolean {
        while (true) {
            val current = state.get()
            if (current.claim != claim || current.phase !in from) return false
            val replacement = State(to, if (clearClaim) null else claim)
            if (state.compareAndSet(current, replacement)) return true
        }
    }
}
