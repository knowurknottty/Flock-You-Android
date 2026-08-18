package com.flockyou.service

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Runtime-policy regressions for the scanning service.
 *
 * These tests deliberately exercise pure service-owned policy so failures are
 * independent of Android framework timing and radio hardware.
 */
class ScanningRuntimePolicyTest {

    @Test
    fun defaultScanConfig_isConservativeUntilPersistedSettingsAreAdmitted() {
        assertFalse(
            "The pre-admission ScanConfig must never opt into aggressive/LOW_LATENCY BLE",
            ScanConfig().aggressiveBleMode
        )
    }
}
