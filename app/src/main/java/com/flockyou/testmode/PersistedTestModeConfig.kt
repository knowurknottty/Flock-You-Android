package com.flockyou.testmode

/** Pure persistence projection used by the DataStore adapter. */
internal data class PersistedTestModeConfig(
    val enabled: Boolean,
    val activeScenarioId: String?,
    val autoAdvanceScenario: Boolean,
    val dataEmissionIntervalMs: Long,
    val simulateSignalVariation: Boolean,
    val showTestModeBanner: Boolean
) {
    fun toConfig(): TestModeConfig = TestModeConfig(
        enabled = enabled,
        activeScenarioId = activeScenarioId,
        autoAdvanceScenario = autoAdvanceScenario,
        dataEmissionIntervalMs = dataEmissionIntervalMs,
        simulateSignalVariation = simulateSignalVariation,
        showTestModeBanner = showTestModeBanner
    )

    companion object {
        fun fromConfig(config: TestModeConfig): PersistedTestModeConfig = PersistedTestModeConfig(
            enabled = config.enabled,
            activeScenarioId = config.activeScenarioId,
            autoAdvanceScenario = config.autoAdvanceScenario,
            dataEmissionIntervalMs = config.dataEmissionIntervalMs,
            simulateSignalVariation = config.simulateSignalVariation,
            showTestModeBanner = config.showTestModeBanner
        )
    }
}
