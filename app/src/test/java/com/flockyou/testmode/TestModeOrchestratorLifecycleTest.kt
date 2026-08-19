package com.flockyou.testmode

import android.content.Context
import android.util.Log
import com.flockyou.data.model.Detection
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.testmode.scanner.MockBleDevice
import com.flockyou.testmode.scanner.MockBleScanResult
import com.flockyou.testmode.scanner.MockBleScanner
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

class TestModeOrchestratorLifecycleTest {
    private var orchestrator: TestModeOrchestrator? = null

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        orchestrator?.destroy()
        unmockkStatic(Log::class)
    }

    @Test
    fun `restarting scenario leaves exactly one active BLE collector`() = runBlocking {
        val repository = mockk<DetectionRepository>(relaxed = true)
        coEvery { repository.upsertDetection(any()) } returns true
        val subject = TestModeOrchestrator(
            context = mockk<Context>(relaxed = true),
            scenarioProvider = TestScenarioProvider(),
            detectionRepository = repository
        )
        orchestrator = subject

        subject.updateConfig(TestModeConfig(dataEmissionIntervalMs = 30_000L))
        subject.startScenario(TestScenario.TrackerFollowing.id)
        delay(150)

        val bleScanner = subject.privateBleScanner()
        bleScanner.stop()
        clearMocks(repository, answers = false, recordedCalls = true)

        subject.startScenario(TestScenario.TrackerFollowing.id)
        delay(150)
        bleScanner.stop()
        clearMocks(repository, answers = false, recordedCalls = true)

        bleScanner.emitResult(
            MockBleScanResult(
                device = MockBleDevice(
                    name = "manual",
                    address = "AA:BB:CC:DD:EE:FF",
                    rssi = -60
                ),
                rssi = -60
            )
        )
        delay(150)

        coVerify(exactly = 1) { repository.upsertDetection(any()) }
    }


    @Test
    fun `explicit synthetic location geo-tags scenario detections`() = runBlocking {
        val repository = mockk<DetectionRepository>(relaxed = true)
        coEvery { repository.upsertDetection(any()) } returns true
        val subject = TestModeOrchestrator(
            context = mockk<Context>(relaxed = true),
            scenarioProvider = TestScenarioProvider(),
            detectionRepository = repository
        )
        orchestrator = subject

        subject.updateConfig(
            TestModeConfig(
                dataEmissionIntervalMs = 30_000L,
                syntheticLatitude = 34.7304,
                syntheticLongitude = -86.5861
            )
        )
        subject.startScenario(TestScenario.TrackerFollowing.id)
        delay(150)

        val bleScanner = subject.privateBleScanner()
        bleScanner.stop()
        clearMocks(repository, answers = false, recordedCalls = true)

        bleScanner.emitResult(
            MockBleScanResult(
                device = MockBleDevice(
                    name = "geo-fixture",
                    address = "AA:BB:CC:DD:EE:01",
                    rssi = -58
                ),
                rssi = -58
            )
        )
        delay(150)

        val detection = slot<Detection>()
        coVerify(exactly = 1) { repository.upsertDetection(capture(detection)) }
        assertEquals(34.7304, detection.captured.latitude ?: Double.NaN, 0.0000001)
        assertEquals(-86.5861, detection.captured.longitude ?: Double.NaN, 0.0000001)
    }


    @Test
    fun `clearing synthetic location prevents stale geotagging`() = runBlocking {
        val repository = mockk<DetectionRepository>(relaxed = true)
        coEvery { repository.upsertDetection(any()) } returns true
        val subject = TestModeOrchestrator(
            context = mockk<Context>(relaxed = true),
            scenarioProvider = TestScenarioProvider(),
            detectionRepository = repository
        )
        orchestrator = subject

        subject.updateConfig(
            TestModeConfig(
                dataEmissionIntervalMs = 30_000L,
                syntheticLatitude = 34.7304,
                syntheticLongitude = -86.5861
            )
        )
        subject.updateConfig(TestModeConfig(dataEmissionIntervalMs = 30_000L))
        subject.startScenario(TestScenario.TrackerFollowing.id)
        delay(150)

        val bleScanner = subject.privateBleScanner()
        bleScanner.stop()
        clearMocks(repository, answers = false, recordedCalls = true)
        bleScanner.emitResult(
            MockBleScanResult(
                device = MockBleDevice(
                    name = "cleared-location",
                    address = "AA:BB:CC:DD:EE:02",
                    rssi = -58
                ),
                rssi = -58
            )
        )
        delay(150)

        val detection = slot<Detection>()
        coVerify(exactly = 1) { repository.upsertDetection(capture(detection)) }
        assertEquals(null, detection.captured.latitude)
        assertEquals(null, detection.captured.longitude)
    }

    @Test
    fun `out of range synthetic location is rejected`() = runBlocking {
        val repository = mockk<DetectionRepository>(relaxed = true)
        coEvery { repository.upsertDetection(any()) } returns true
        val subject = TestModeOrchestrator(
            context = mockk<Context>(relaxed = true),
            scenarioProvider = TestScenarioProvider(),
            detectionRepository = repository
        )
        orchestrator = subject

        subject.updateConfig(
            TestModeConfig(
                dataEmissionIntervalMs = 30_000L,
                syntheticLatitude = 123.0,
                syntheticLongitude = -222.0
            )
        )
        subject.startScenario(TestScenario.TrackerFollowing.id)
        delay(150)

        val bleScanner = subject.privateBleScanner()
        bleScanner.stop()
        clearMocks(repository, answers = false, recordedCalls = true)
        bleScanner.emitResult(
            MockBleScanResult(
                device = MockBleDevice(
                    name = "invalid-location",
                    address = "AA:BB:CC:DD:EE:03",
                    rssi = -58
                ),
                rssi = -58
            )
        )
        delay(150)

        val detection = slot<Detection>()
        coVerify(exactly = 1) { repository.upsertDetection(capture(detection)) }
        assertEquals(null, detection.captured.latitude)
        assertEquals(null, detection.captured.longitude)
    }

    private fun TestModeOrchestrator.privateBleScanner(): MockBleScanner {
        val field = TestModeOrchestrator::class.java.getDeclaredField("mockBleScanner")
        field.isAccessible = true
        return field.get(this) as MockBleScanner
    }
}
