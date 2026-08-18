package com.flockyou.data.export

import com.flockyou.data.model.Detection
import com.flockyou.data.model.DetectionMethod
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.SignalStrength
import com.flockyou.data.model.ThreatLevel
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class DetectionExportSerializerTest {

    @Test
    fun `request defaults protect identifiers and location precision`() {
        val request = ExportRequest(format = ExportFormat.CSV)

        assertTrue("identifiers should be redacted unless explicitly requested", request.redactIdentifiers)
        assertEquals("default location export should be deliberately coarse", 3, request.locationPrecisionDecimals)
        assertTrue(request.includeLocation)
    }

    @Test
    fun `bare export request applies privacy defaults end to end`() {
        val detection = detection(
            id = "private-id",
            mac = "AA:BB:CC:DD:EE:FF",
            ssid = "PrivateNetwork",
            lat = 47.60625,
            lon = -122.33213,
        )

        val geojson = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.GEOJSON),
            listOf(detection),
        )

        assertFalse(geojson.contains("private-id"))
        assertFalse(geojson.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(geojson.contains("PrivateNetwork"))
        assertTrue(geojson.contains("-122.332"))
        assertTrue(geojson.contains("47.606"))
        assertFalse(geojson.contains("-122.33213"))
        assertFalse(geojson.contains("47.60625"))
    }

    // =====================================================================================
    // Filtering
    // =====================================================================================

    @Test
    fun `filter applies time range inclusively`() {
        val detections = listOf(
            detection(id = "a", timestamp = 1000),
            detection(id = "b", timestamp = 2000),
            detection(id = "c", timestamp = 3000),
        )
        val request = ExportRequest(format = ExportFormat.CSV, startTime = 2000, endTime = 3000)
        val result = DetectionExportSerializer.filter(detections, request)
        assertEquals(listOf("b", "c"), result.map { it.id })
    }

    @Test
    fun `filter applies protocol and threat whitelists`() {
        val wifi = detection(id = "w", protocol = DetectionProtocol.WIFI, threatLevel = ThreatLevel.HIGH)
        val ble = detection(id = "b", protocol = DetectionProtocol.BLUETOOTH_LE, threatLevel = ThreatLevel.LOW)
        val request = ExportRequest(
            format = ExportFormat.CSV,
            protocols = setOf(DetectionProtocol.WIFI),
            threatLevels = setOf(ThreatLevel.HIGH),
        )
        val result = DetectionExportSerializer.filter(listOf(wifi, ble), request)
        assertEquals(listOf("w"), result.map { it.id })
    }

    @Test
    fun `filter with empty whitelists keeps everything`() {
        val detections = listOf(
            detection(id = "a", protocol = DetectionProtocol.WIFI, threatLevel = ThreatLevel.LOW),
            detection(id = "b", protocol = DetectionProtocol.BLUETOOTH_LE, threatLevel = ThreatLevel.CRITICAL),
        )
        val result = DetectionExportSerializer.filter(detections, ExportRequest(format = ExportFormat.CSV))
        assertEquals(2, result.size)
    }

    // =====================================================================================
    // Deterministic ordering
    // =====================================================================================

    @Test
    fun `serialize orders deterministically by timestamp then id`() {
        val detections = listOf(
            detection(id = "z", timestamp = 5000),
            detection(id = "a", timestamp = 5000),
            detection(id = "m", timestamp = 1000),
        )
        val csv = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.CSV, redactIdentifiers = false),
            detections,
        )
        val lines = csv.trim().split('\n')
        assertEquals(4, lines.size) // header + 3 rows
        val ids = lines.drop(1).map { it.split(',')[0] }
        assertEquals(listOf("m", "a", "z"), ids)
    }

    // =====================================================================================
    // Redaction
    // =====================================================================================

    @Test
    fun `redaction strips identifiers but keeps categories in GeoJSON`() {
        val d = detection(id = "secret-id", mac = "AA:BB:CC:DD:EE:FF", ssid = "Home", deviceName = "Cam")
        val redacted = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.GEOJSON, redactIdentifiers = true),
            listOf(d),
        )
        assertFalse(redacted.contains("secret-id"))
        assertFalse(redacted.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(redacted.contains("\"ssid\""))
        assertFalse(redacted.contains("\"deviceName\""))
        assertFalse(redacted.contains("\"manufacturer\""))
        // Categories remain
        assertTrue(redacted.contains("FLOCK_SAFETY_CAMERA"))
        assertTrue(redacted.contains("HIGH"))
    }

    @Test
    fun `non-redacted export keeps identifiers`() {
        val d = detection(id = "d1", mac = "AA:BB:CC:DD:EE:FF", ssid = "Home", deviceName = "Cam")
        val csv = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.CSV, redactIdentifiers = false),
            listOf(d),
        )
        assertTrue(csv.contains("d1"))
        assertTrue(csv.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(csv.contains("Home"))
    }

    // =====================================================================================
    // Location precision / rounding
    // =====================================================================================

    @Test
    fun `coordinate precision rounds deterministically`() {
        val d = detection(lat = 47.60625, lon = -122.33213)
        // 3 decimals: HALF_UP rounding
        val coarse = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.KML, locationPrecisionDecimals = 3),
            listOf(d),
        )
        assertTrue(coarse.contains("-122.332,47.606"))

        // 6 decimals: effectively exact, trailing zeros stripped
        val fine = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.KML, locationPrecisionDecimals = 6),
            listOf(d),
        )
        assertTrue(fine.contains("-122.33213,47.60625"))
    }

    @Test
    fun `includeLocation false omits geometry from spatial formats`() {
        val d = detection(lat = 47.6, lon = -122.3)
        val kml = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.KML, includeLocation = false),
            listOf(d),
        )
        assertFalse(kml.contains("<coordinates>"))
        assertFalse(kml.contains("Placemark"))

        val geojson = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.GEOJSON, includeLocation = false),
            listOf(d),
        )
        assertEquals(0, parseGeoJson(geojson).featuresCount())

        val csv = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.CSV, includeLocation = false),
            listOf(d),
        )
        // CSV still includes the row, but latitude/longitude columns are empty
        val row = csv.trim().split('\n')[1]
        val fields = row.split(',')
        assertEquals("", fields[13]) // latitude column empty
        assertEquals("", fields[14]) // longitude column empty
    }

    // =====================================================================================
    // Round-trip / structural validity
    // =====================================================================================

    @Test
    fun `GeoJSON output is valid JSON with correct geometry`() {
        val d = detection(lat = 47.6062, lon = -122.3321)
        val json = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.GEOJSON, locationPrecisionDecimals = 6),
            listOf(d),
        )
        val parsed = parseGeoJson(json)
        assertEquals("FeatureCollection", parsed.type())
        assertEquals(1, parsed.featuresCount())
        val feature = parsed.feature(0)
        assertEquals("Point", feature.geometryType())
        assertEquals(-122.3321, feature.coordinate(0), 1e-9)
        assertEquals(47.6062, feature.coordinate(1), 1e-9)
        assertEquals("FLOCK_SAFETY_CAMERA", feature.property("deviceType"))
        assertEquals("HIGH", feature.property("threatLevel"))
    }

    @Test
    fun `KML output is well-formed XML with one placemark per located detection`() {
        val detections = listOf(
            detection(id = "a", lat = 47.6, lon = -122.3),
            detection(id = "b", lat = null, lon = null), // non-located, must be skipped
        )
        val kml = DetectionExportSerializer.serialize(ExportRequest(format = ExportFormat.KML), detections)
        val doc = parseXml(kml)
        val placemarks = doc.getElementsByTagNameNS(KML_NS, "Placemark")
        assertEquals(1, placemarks.length)
    }

    @Test
    fun `GPX output is well-formed XML with one waypoint per located detection`() {
        val detections = listOf(
            detection(id = "a", lat = 47.6, lon = -122.3),
            detection(id = "b", lat = null, lon = null),
        )
        val gpx = DetectionExportSerializer.serialize(ExportRequest(format = ExportFormat.GPX), detections)
        val doc = parseXml(gpx)
        val wpts = doc.getElementsByTagNameNS(GPX_NS, "wpt")
        assertEquals(1, wpts.length)
        val lat = wpts.item(0).attributes.getNamedItem("lat").nodeValue
        val lon = wpts.item(0).attributes.getNamedItem("lon").nodeValue
        assertEquals("47.6", lat)
        assertEquals("-122.3", lon)
    }

    @Test
    fun `CSV includes non-located detections`() {
        val detections = listOf(
            detection(id = "located", lat = 47.6, lon = -122.3),
            detection(id = "noloc", lat = null, lon = null),
        )
        val csv = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.CSV, redactIdentifiers = false),
            detections,
        )
        val lines = csv.trim().split('\n')
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(csv.contains("located"))
        assertTrue(csv.contains("noloc"))
    }

    @Test
    fun `CSV escapes embedded commas and quotes`() {
        val d = detection(id = "d1", ssid = "Home, Sweet \"Home\"")
        val csv = DetectionExportSerializer.serialize(
            ExportRequest(format = ExportFormat.CSV, redactIdentifiers = false),
            listOf(d),
        )
        assertTrue(csv.contains("\"Home, Sweet \"\"Home\"\"\""))
    }

    // =====================================================================================
    // Request validation
    // =====================================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `request rejects invalid precision`() {
        ExportRequest(format = ExportFormat.KML, locationPrecisionDecimals = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `request rejects inverted time range`() {
        ExportRequest(format = ExportFormat.KML, startTime = 5000, endTime = 1000)
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private fun detection(
        id: String = "d1",
        timestamp: Long = 1000,
        deviceType: DeviceType = DeviceType.FLOCK_SAFETY_CAMERA,
        protocol: DetectionProtocol = DetectionProtocol.WIFI,
        threatLevel: ThreatLevel = ThreatLevel.HIGH,
        mac: String? = "AA:BB:CC:DD:EE:FF",
        ssid: String? = null,
        deviceName: String? = null,
        manufacturer: String? = "Flock Safety",
        lat: Double? = 47.6062,
        lon: Double? = -122.3321,
        rssi: Int = -60,
    ): Detection = Detection(
        id = id,
        timestamp = timestamp,
        protocol = protocol,
        detectionMethod = DetectionMethod.SSID_PATTERN,
        deviceType = deviceType,
        rssi = rssi,
        signalStrength = SignalStrength.GOOD,
        threatLevel = threatLevel,
        macAddress = mac,
        ssid = ssid,
        deviceName = deviceName,
        manufacturer = manufacturer,
        latitude = lat,
        longitude = lon,
    )

    private class ParsedGeoJson(private val root: com.google.gson.JsonObject) {
        fun type(): String = root.get("type").asString
        fun featuresCount(): Int = root.getAsJsonArray("features").size()
        fun feature(index: Int): ParsedFeature =
            ParsedFeature(root.getAsJsonArray("features").get(index).asJsonObject)
    }

    private class ParsedFeature(private val obj: com.google.gson.JsonObject) {
        fun geometryType(): String =
            obj.getAsJsonObject("geometry").get("type").asString
        fun coordinate(index: Int): Double =
            obj.getAsJsonObject("geometry").getAsJsonArray("coordinates").get(index).asDouble
        fun property(name: String): String =
            obj.getAsJsonObject("properties").get(name).asString
    }

    private fun parseGeoJson(json: String): ParsedGeoJson =
        ParsedGeoJson(JsonParser.parseString(json).asJsonObject)

    private fun parseXml(xml: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml)))
    }

    companion object {
        private const val KML_NS = "http://www.opengis.net/kml/2.2"
        private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    }
}
