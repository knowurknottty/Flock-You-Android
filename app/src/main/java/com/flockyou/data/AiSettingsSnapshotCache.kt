package com.flockyou.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository-owned hot cache for a non-null settings snapshot.
 *
 * Hydration, mutation, and invalidation are serialized through one mutex. This gives callers
 * a linearizable boundary: once a mutation completes, an older in-flight hydration cannot
 * publish stale state after it.
 */
internal class AiSettingsSnapshotCache<T : Any> {
    private val mutex = Mutex()
    private var snapshot: T? = null

    suspend fun get(loader: suspend () -> T): T = mutex.withLock {
        snapshot ?: loader().also { snapshot = it }
    }

    suspend fun mutate(writer: suspend () -> Unit) = mutex.withLock {
        writer()
        snapshot = null
    }

    suspend fun invalidate() = mutex.withLock {
        snapshot = null
    }
}
