package com.flockyou.data.export

import com.flockyou.data.model.Detection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Locale

/**
 * Pure, deterministic serializer that turns a list of [Detection]s plus an [ExportRequest]
 * into a local-format document (KML, GeoJSON, CSV, or GPX).
 *
 * Design invariants:
 * - No Android framework dependencies (unit-testable on the JVM).
 * - No network access, no file IO, no hidden upload. This object only returns a [String].
 * - Deterministic output: identical input + request produces byte-identical output (the sort
 *   key is `(timestamp, id)` and coordinate/JSON/XML emission is fixed-precision and ordered).
 * - Identifier redaction and location precision/rounding are applied here, never at the
 *   persistence layer, so the encrypted database always retains full data while exports are
 *   minimized according to the request.
 *
 * Thread-safety: all functions are pure and stateless; instances may be shared freely.
 */
object DetectionExportSerializer {

    fun serialize(request: ExportRequest, detections: List<Detection>): String {
        val filtered = filter(detections, request)
        val sorted = filtered.sortedWith(
            compareBy<Detection> { it.timestamp }.thenBy { it.id }
        )
        return when (request.format) {
            ExportFormat.KML -> buildKml(request, sorted)
            ExportFormat.GEOJSON -> buildGeoJson(request, sorted)
            ExportFormat.CSV -> buildCsv(request, sorted)
            ExportFormat.GPX -> buildGpx(request, sorted)
        }
    }

    // ===========================================================================================
    // Filtering / projection
    // ===========================================================================================

    /**
     * Apply time-range, protocol, and threat-level filters.
     */
    fun filter(detections: List<Detection>, request: ExportRequest): List<Detection> {
        return detections.filter { d ->
            val inTimeRange = (request.startTime == null || d.timestamp >= request.startTime) &&
                (request.endTime == null || d.timestamp <= request.endTime)
            val inProtocol = request.protocols.isEmpty() || d.protocol in request.protocols
            val inThreat = request.threatLevels.isEmpty() || d.threatLevel in request.threatLevels
            inTimeRange && inProtocol && inThreat
        }
    }

    private fun hasLocation(d: Detection): Boolean =
        d.latitude != null && d.longitude != null

    // ===========================================================================================
    // Redaction
    // ===========================================================================================

    /**
     * Return [value] unless identifier redaction is requested, in which case return null.
     */
    private fun identifier(request: ExportRequest, value: String?): String? =
        if (request.redactIdentifiers) null else value

    /**
     * Return the internal id unless identifier redaction is requested.
     */
    private fun exportId(request: ExportRequest, d: Detection): String? =
        if (request.redactIdentifiers) null else d.id

    // ===========================================================================================
    // Coordinate precision
    // ===========================================================================================

    /**
     * Round a coordinate to [ExportRequest.locationPrecisionDecimals] decimal places and return
     * a clean decimal string (no scientific notation, no trailing zeros). HALF_UP rounding keeps
     * output deterministic across platforms.
     */
    private fun roundCoordinate(request: ExportRequest, value: Double): String =
        BigDecimal.valueOf(value)
            .setScale(request.locationPrecisionDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    // ===========================================================================================
    // KML
    // ===========================================================================================

    private fun buildKml(request: ExportRequest, detections: List<Detection>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Flock-You Detections</name>\n")
        sb.append("    <description>Local surveillance-device detection export</description>\n")

        detections.forEach { d ->
            if (!request.includeLocation || !hasLocation(d)) return@forEach
            val lon = d.longitude
            val lat = d.latitude
            if (lon == null || lat == null) return@forEach

            val styleId = d.threatLevel.name.lowercase(Locale.US)
            sb.append("    <Placemark>\n")
            sb.append("      <name>${xmlEscape(d.deviceType.displayName)}</name>\n")
            sb.append("      <description>")
            sb.append(xmlEscape(descriptionText(request, d)))
            sb.append("</description>\n")
            sb.append("      <styleUrl>#$styleId</styleUrl>\n")
            sb.append("      <Point>\n")
            sb.append("        <coordinates>")
            sb.append(roundCoordinate(request, lon)).append(',')
            sb.append(roundCoordinate(request, lat)).append(",0")
            sb.append("</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("      <TimeStamp><when>")
            sb.append(Instant.ofEpochMilli(d.timestamp).toString())
            sb.append("</when></TimeStamp>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    private fun descriptionText(request: ExportRequest, d: Detection): String {
        val mac = identifier(request, d.macAddress)?.let { "MAC: $it; " } ?: ""
        val ssid = identifier(request, d.ssid)?.let { "SSID: $it; " } ?: ""
        val name = identifier(request, d.deviceName)?.let { "Name: $it; " } ?: ""
        return "Device: ${d.deviceType.displayName}; " +
            "Threat: ${d.threatLevel.displayName}; " +
            "Protocol: ${d.protocol.displayName}; " +
            "RSSI: ${d.rssi} dBm; " +
            "Seen: ${d.seenCount}; " +
            mac + ssid + name +
            "Detected: ${Instant.ofEpochMilli(d.timestamp)}"
    }

    // ===========================================================================================
    // GeoJSON (RFC 7946)
    // ===========================================================================================

    private fun buildGeoJson(request: ExportRequest, detections: List<Detection>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"type\": \"FeatureCollection\",\n")
        sb.append("  \"features\": [")
        var first = true
        detections.forEach { d ->
            if (!request.includeLocation || !hasLocation(d)) return@forEach
            val lon = d.longitude
            val lat = d.latitude
            if (lon == null || lat == null) return@forEach

            if (!first) sb.append(",")
            first = false
            sb.append("\n    {\n")
            sb.append("      \"type\": \"Feature\",\n")
            sb.append("      \"geometry\": {\"type\": \"Point\", \"coordinates\": [")
            sb.append(roundCoordinate(request, lon)).append(", ")
            sb.append(roundCoordinate(request, lat)).append("]},\n")
            sb.append("      \"properties\": {\n")
            exportId(request, d)?.let { sb.append("        \"id\": \"${jsonEscape(it)}\",\n") }
            sb.append("        \"timestamp\": ${d.timestamp},\n")
            sb.append("        \"timestampIso\": \"${Instant.ofEpochMilli(d.timestamp)}\",\n")
            sb.append("        \"deviceType\": \"${jsonEscape(d.deviceType.name)}\",\n")
            sb.append("        \"threatLevel\": \"${jsonEscape(d.threatLevel.name)}\",\n")
            sb.append("        \"protocol\": \"${jsonEscape(d.protocol.name)}\",\n")
            sb.append("        \"detectionMethod\": \"${jsonEscape(d.detectionMethod.name)}\",\n")
            sb.append("        \"rssi\": ${d.rssi},\n")
            sb.append("        \"signalStrength\": \"${jsonEscape(d.signalStrength.name)}\",\n")
            identifier(request, d.macAddress)?.let { sb.append("        \"macAddress\": \"${jsonEscape(it)}\",\n") }
            identifier(request, d.ssid)?.let { sb.append("        \"ssid\": \"${jsonEscape(it)}\",\n") }
            identifier(request, d.deviceName)?.let { sb.append("        \"deviceName\": \"${jsonEscape(it)}\",\n") }
            identifier(request, d.manufacturer)?.let { sb.append("        \"manufacturer\": \"${jsonEscape(it)}\",\n") }
            sb.append("        \"seenCount\": ${d.seenCount},\n")
            sb.append("        \"isActive\": ${d.isActive}\n")
            sb.append("      }\n")
            sb.append("    }")
        }
        sb.append("\n  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ===========================================================================================
    // CSV (RFC 4180)
    // ===========================================================================================

    private fun buildCsv(request: ExportRequest, detections: List<Detection>): String {
        val sb = StringBuilder()
        sb.append("id,timestamp,isoTimestamp,deviceType,threatLevel,protocol,detectionMethod,")
        sb.append("rssi,signalStrength,macAddress,ssid,deviceName,manufacturer,")
        sb.append("latitude,longitude,seenCount,isActive\n")

        detections.forEach { d ->
            val fields = mutableListOf<String>()
            fields.add(exportId(request, d) ?: "")
            fields.add(d.timestamp.toString())
            fields.add(Instant.ofEpochMilli(d.timestamp).toString())
            fields.add(d.deviceType.name)
            fields.add(d.threatLevel.name)
            fields.add(d.protocol.name)
            fields.add(d.detectionMethod.name)
            fields.add(d.rssi.toString())
            fields.add(d.signalStrength.name)
            fields.add(identifier(request, d.macAddress) ?: "")
            fields.add(identifier(request, d.ssid) ?: "")
            fields.add(identifier(request, d.deviceName) ?: "")
            fields.add(identifier(request, d.manufacturer) ?: "")
            fields.add(if (request.includeLocation) d.latitude?.let { roundCoordinate(request, it) } ?: "" else "")
            fields.add(if (request.includeLocation) d.longitude?.let { roundCoordinate(request, it) } ?: "" else "")
            fields.add(d.seenCount.toString())
            fields.add(d.isActive.toString())
            sb.append(fields.joinToString(",") { csvEscape(it) }).append('\n')
        }
        return sb.toString()
    }

    // ===========================================================================================
    // GPX 1.1 (optional format)
    // ===========================================================================================

    private fun buildGpx(request: ExportRequest, detections: List<Detection>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Flock-You\" ")
        sb.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")

        detections.forEach { d ->
            if (!request.includeLocation || !hasLocation(d)) return@forEach
            val lon = d.longitude
            val lat = d.latitude
            if (lon == null || lat == null) return@forEach

            sb.append("  <wpt lat=\"${roundCoordinate(request, lat)}\" lon=\"${roundCoordinate(request, lon)}\">\n")
            sb.append("    <time>${Instant.ofEpochMilli(d.timestamp)}</time>\n")
            sb.append("    <name>${xmlEscape(d.deviceType.displayName)}</name>\n")
            sb.append("    <desc>${xmlEscape(descriptionText(request, d))}</desc>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    // ===========================================================================================
    // Escaping helpers
    // ===========================================================================================

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun csvEscape(s: String): String {
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        return if (needsQuote) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
