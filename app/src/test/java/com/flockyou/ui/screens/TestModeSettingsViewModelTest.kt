package com.flockyou.ui.screens

import com.flockyou.testmode.TestModeConfig
import com.flockyou.testmode.TestModeConfigRepository
import com.flockyou.testmode.TestModeOrchestrator
import com.flockyou.testmode.TestModeStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class TestModeSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `persisted config hydrates orchestrator on startup`() = runTest(dispatcher) {
        val orchestrator = mockk<TestModeOrchestrator>(relaxed = true)
        val repository = mockk<TestModeConfigRepository>()
        val persisted = TestModeConfig(
            enabled = true,
            dataEmissionIntervalMs = 5_000L,
            syntheticLatitude = 34.7304,
            syntheticLongitude = -86.5861
        )
        every { orchestrator.config } returns MutableStateFlow(TestModeConfig())
        every { orchestrator.status } returns MutableStateFlow(TestModeStatus())
        every { orchestrator.getAvailableScenarios() } returns emptyList()
        every { repository.config } returns MutableStateFlow(persisted)

        TestModeSettingsViewModel(orchestrator, repository)
        advanceUntilIdle()

        verify(exactly = 1) { orchestrator.updateConfig(persisted) }
    }
}
