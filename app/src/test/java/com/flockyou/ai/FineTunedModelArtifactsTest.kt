package com.flockyou.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FineTunedModelArtifactsTest {
    @Test
    fun `hosted Gemma GGUF metadata is exact and non selectable`() {
        val artifact = FineTunedModelArtifacts.GEMMA_FLOCK_Q8_0

        assertEquals("gemma-flock-q8-0", artifact.id)
        assertEquals(HostedModelFormat.GGUF, artifact.format)
        assertEquals(291_545_376L, artifact.sizeBytes)
        assertEquals(
            "82b323bf05eba698b87a39d1eca8ea31506222aff25b415f6388135069725b57",
            artifact.sha256
        )
        assertTrue(artifact.downloadUrl.startsWith("https://mega.nz/file/WzAiwIba#"))
        assertFalse(artifact.runtimeCompatible)
        assertTrue(artifact.compatibilityReason.contains("MediaPipe"))
    }
}
