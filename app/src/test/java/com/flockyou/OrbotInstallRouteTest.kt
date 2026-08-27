package com.flockyou.network

import org.junit.Assert.assertEquals
import org.junit.Test

class OrbotInstallRouteTest {
    @Test fun `arm64 devices prefer arm64 Mega mirror`() {
        assertEquals(OrbotHelper.ORBOT_MEGA_ARM64_URL, OrbotHelper.preferredInstallUrl(arrayOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test fun `non arm64 devices prefer universal Mega mirror`() {
        assertEquals(OrbotHelper.ORBOT_MEGA_UNIVERSAL_URL, OrbotHelper.preferredInstallUrl(arrayOf("x86_64")))
    }
}
