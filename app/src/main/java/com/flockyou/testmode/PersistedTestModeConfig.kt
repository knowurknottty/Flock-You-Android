package com.flockyou.testmode

/** Pure persistence projection used by the DataStore adapter. */
internal data class PersistedTestModeConfig(
    val enabled: Boolean,
    val dataEmissionIntervalMs: Long,
    val simulateSignalVariation: Boolean,
    val showTestModeBanner: Boolean,
    val syntheticLatitude: Double?,
    val syntheticLongitude: Double?
) {
    fun toConfig(): TestModeConfig = TestModeConfig(
        enabled = enabled,
        activeScenarioId = null,
        dataEmissionIntervalMs = dataEmissionIntervalMs,
        simulateSignalVariation = simulateSignalVariation,
        showTestModeBanner = showTestModeBanner,
        syntheticLatitude = syntheticLatitude,
        syntheticLongitude = syntheticLongitude
    )

    companion object {
        fun fromConfig(config: TestModeConfig): PersistedTestModeConfig = PersistedTestModeConfig(
            enabled = config.enabled,
            dataEmissionIntervalMs = config.dataEmissionIntervalMs,
            simulateSignalVariation = config.simulateSignalVariation,
            showTestModeBanner = config.showTestModeBanner,
            syntheticLatitude = config.syntheticLatitude,
            syntheticLongitude = config.syntheticLongitude
        )
    }
}
