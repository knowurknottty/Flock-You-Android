package com.flockyou.network

import com.flockyou.data.model.Detection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Builds an explicit external-browser Shodan search from evidence already shown to the user. */
object ShodanSearch {
    private const val SEARCH_BASE = "https://www.shodan.io/search?query="

    fun buildKeywords(detection: Detection): String {
        val values = listOfNotNull(
            detection.manufacturer?.trim()?.takeIf { it.isNotEmpty() },
            detection.deviceName?.trim()?.takeIf { it.isNotEmpty() },
            detection.deviceType.displayName,
            detection.protocol.displayName,
            detection.macAddress?.trim()?.takeIf { it.isNotEmpty() },
            detection.ssid?.trim()?.takeIf { it.isNotEmpty() },
            detection.serviceUuids?.trim()?.takeIf { it.isNotEmpty() }
        )
        return values.distinctBy { it.lowercase() }.joinToString(" ")
    }

    fun buildSearchUrl(detection: Detection): String =
        SEARCH_BASE + URLEncoder.encode(buildKeywords(detection), StandardCharsets.UTF_8)
}
