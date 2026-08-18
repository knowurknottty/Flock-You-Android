package com.flockyou.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Repository-owned hot cache for a non-null settings snapshot.
 *
 * This initial extraction intentionally preserves the repository's existing
 * hydrate-then-publish / write-then-invalidate semantics so concurrency behavior
 * can be regression-tested before synchronization is changed.
 */
internal class AiSettingsSnapshotCache<T : Any> {
    private val snapshot = MutableStateFlow<T?>(null)

    suspend fun get(loader: suspend () -> T): T {
        snapshot.value?.let { return it }
        val loaded = loader()
        snapshot.value = loaded
        return loaded
    }

    suspend fun mutate(writer: suspend () -> Unit) {
        writer()
        snapshot.value = null
    }

    suspend fun invalidate() {
        snapshot.value = null
    }
}
