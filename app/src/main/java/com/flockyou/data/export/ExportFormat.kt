package com.flockyou.data.export

/**
 * Supported local export formats.
 *
 * All formats are generated entirely on-device. None of them contact any network endpoint,
 * and none require a Google account or Google Play Services to produce.
 */
enum class ExportFormat(val fileExtension: String, val mimeType: String) {
    KML("kml", "application/vnd.google-earth.kml+xml"),
    GEOJSON("geojson", "application/geo+json"),
    CSV("csv", "text/csv"),
    GPX("gpx", "application/gpx+xml");
}
