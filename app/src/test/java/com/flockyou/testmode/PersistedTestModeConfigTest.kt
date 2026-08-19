package com.flockyou.testmode

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedTestModeConfigTest {
    @Test
    fun `synthetic location survives persistence projection round trip`() {
        val original = TestModeConfig(
            enabled = true,
            activeScenarioId = TestScenario.SurveillanceCamera.id,
            syntheticLatitude = 34.7304,
            syntheticLongitude = -86.5861
        )

        val restored = PersistedTestModeConfig.fromConfig(original).toConfig()

        assertEquals(34.7304, restored.syntheticLatitude ?: Double.NaN, 0.0000001)
        assertEquals(-86.5861, restored.syntheticLongitude ?: Double.NaN, 0.0000001)
    }
}
