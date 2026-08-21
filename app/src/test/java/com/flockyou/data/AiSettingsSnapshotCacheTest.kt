package com.flockyou.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiSettingsSnapshotCacheTest {

    @Test
    fun `cache hit does not hydrate twice`() = runBlocking {
        val cache = AiSettingsSnapshotCache<String>()
        var loads = 0

        val first = cache.get { loads++; "value" }
        val second = cache.get { loads++; "other" }

        assertEquals("value", first)
        assertEquals("value", second)
        assertEquals(1, loads)
    }

    @Test
    fun `mutation cannot overtake an in-flight hydration`() = runBlocking {
        val cache = AiSettingsSnapshotCache<String>()
        var persisted = "old"
        val hydrationStarted = CompletableDeferred<Unit>()
        val releaseHydration = CompletableDeferred<Unit>()
        val mutationEntered = CompletableDeferred<Unit>()

        val reader = async(Dispatchers.Default) {
            cache.get {
                val captured = persisted
                hydrationStarted.complete(Unit)
                releaseHydration.await()
                captured
            }
        }

        hydrationStarted.await()
        val writer = async(Dispatchers.Default) {
            cache.mutate {
                mutationEntered.complete(Unit)
                persisted = "new"
            }
        }

        val mutationOvertookHydration = withTimeoutOrNull(250) {
            mutationEntered.await()
            true
        } ?: false

        // Always release/join the children before asserting so a deliberate RED failure
        // cannot strand a runBlocking child and masquerade as a hung test.
        releaseHydration.complete(Unit)
        assertEquals("old", reader.await())
        writer.await()

        assertFalse(
            "a completed settings write must not overtake an in-flight hydration",
            mutationOvertookHydration,
        )
        assertEquals("new", cache.get { persisted })
    }

    @Test
    fun `mutation invalidates the prior cached snapshot`() = runBlocking {
        val cache = AiSettingsSnapshotCache<String>()
        var persisted = "old"
        assertEquals("old", cache.get { persisted })

        cache.mutate { persisted = "new" }

        assertEquals("new", cache.get { persisted })
    }
}
