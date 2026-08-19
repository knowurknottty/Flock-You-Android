package com.flockyou.testmode

/** Pure persistence projection used by the DataStore adapter. */
internal data class PersistedTestModeConfig(
    val enabled: Boolean,
    val autoAdvanceScenario: Boolean,
    val dataEmissionIntervalMs: Long,
    val simulateSignalVariation: Boolean,
    val showTestModeBanner: Boolean,
    val syntheticLatitude: Double?,
    val syntheticLongitude: Double?
) {
    fun toConfig(): TestModeConfig = TestModeConfig(
        enabled = enabled,
        activeScenarioId = null,
        autoAdvanceScenario = autoAdvanceScenario,
        dataEmissionIntervalMs = dataEmissionIntervalMs,
        simulateSignalVariation = simulateSignalVariation,
        showTestModeBanner = showTestModeBanner,
        syntheticLatitude = syntheticLatitude,
        syntheticLongitude = syntheticLongitude
    )

    companion object {
        fun fromConfig(config: TestModeConfig): PersistedTestModeConfig = PersistedTestModeConfig(
            enabled = config.enabled,
            autoAdvanceScenario = config.autoAdvanceScenario,
            dataEmissionIntervalMs = config.dataEmissionIntervalMs,
            simulateSignalVariation = config.simulateSignalVariation,
            showTestModeBanner = config.showTestModeBanner,
            syntheticLatitude = config.syntheticLatitude,
            syntheticLongitude = config.syntheticLongitude
        )
    }
}
