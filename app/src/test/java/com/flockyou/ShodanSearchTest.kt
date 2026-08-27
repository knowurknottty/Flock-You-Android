package com.flockyou.network

import com.flockyou.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ShodanSearchTest {
    private fun detection() = Detection(
        protocol = DetectionProtocol.BLUETOOTH_LE, detectionMethod = DetectionMethod.BLE_SERVICE_UUID,
        deviceType = DeviceType.UNKNOWN_SURVEILLANCE, deviceName = "Road Sensor 7",
        macAddress = "AA:BB:CC:11:22:33", ssid = "Fleet Cam", rssi = -55,
        signalStrength = SignalStrength.GOOD, threatLevel = ThreatLevel.INFO,
        manufacturer = "Acme Vision", serviceUuids = "00001234-0000-1000-8000-00805f9b34fb"
    )

    @Test fun `keywords use observable identifiers without raw payload`() {
        val q = ShodanSearch.buildKeywords(detection())
        assertTrue(q.contains("Acme Vision")); assertTrue(q.contains("Road Sensor 7"))
        assertTrue(q.contains("AA:BB:CC:11:22:33")); assertTrue(q.contains("Fleet Cam"))
        assertTrue(q.contains("00001234-0000-1000-8000-00805f9b34fb"))
        assertFalse(q.contains("rawData"))
    }

    @Test fun `search URL targets Shodan and safely encodes keywords`() {
        val url = ShodanSearch.buildSearchUrl(detection())
        assertTrue(url.startsWith("https://www.shodan.io/search?query="))
        val encoded = url.substringAfter("query=")
        val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8)
        assertEquals(ShodanSearch.buildKeywords(detection()), decoded)
    }

    @Test fun `sparse detection still creates useful search`() {
        val d = Detection(protocol=DetectionProtocol.WIFI, detectionMethod=DetectionMethod.SSID_PATTERN,
            deviceType=DeviceType.FLEET_VEHICLE, rssi=-70, signalStrength=SignalStrength.MEDIUM, threatLevel=ThreatLevel.INFO)
        val q = ShodanSearch.buildKeywords(d)
        assertTrue(q.contains(DeviceType.FLEET_VEHICLE.displayName)); assertTrue(q.contains("WiFi"))
    }
}
