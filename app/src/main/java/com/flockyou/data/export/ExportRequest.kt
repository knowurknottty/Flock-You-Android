package com.flockyou.data.export

import com.flockyou.data.model.DetectionProtocol
import com.flockyou.data.model.ThreatLevel

/**
 * Immutable, self-contained description of a local detection export.
 *
 * This is the narrow contract exposed to presentation/UI lanes: a caller builds an
 * [ExportRequest] and hands it to [DetectionExportService]; it never needs to know about
 * Room, DAOs, or serialization internals.
 *
 * All fields are optional filters/controls with privacy-first defaults where applicable.
 * No field triggers any network activity.
 *
 * @param format            output format.
 * @param startTime         inclusive lower bound on [com.flockyou.data.model.Detection.timestamp]
 *                          (epoch millis). `null` means no lower bound.
 * @param endTime           inclusive upper bound on timestamp (epoch millis). `null` means no
 *                          upper bound.
 * @param protocols         protocol whitelist; empty set means "all protocols".
 * @param threatLevels      threat-level whitelist; empty set means "all threat levels".
 * @param locationPrecisionDecimals  number of decimal places to round lat/lon to before writing.
 *                          6 ≈ 0.11 m (effectively exact for GPS); lower values (e.g. 3 ≈ 110 m)
 *                          deliberately coarsen the exported location for privacy.
 * @param redactIdentifiers when true, strip per-device identifiers (MAC, SSID, device name,
 *                          service UUIDs, manufacturer, raw advertisement data, matched patterns,
 *                          and the internal detection id) so the export contains only categories,
 *                          scores, timestamps, and (optionally) coarsened coordinates.
 * @param includeLocation   when false, omit lat/lon entirely even for spatial formats (which then
 *                          contain no geometry).
 */
data class ExportRequest(
    val format: ExportFormat,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val protocols: Set<DetectionProtocol> = emptySet(),
    val threatLevels: Set<ThreatLevel> = emptySet(),
    val locationPrecisionDecimals: Int = 6,
    val redactIdentifiers: Boolean = false,
    val includeLocation: Boolean = true,
) {
    init {
        require(locationPrecisionDecimals in 0..12) {
            "locationPrecisionDecimals must be in 0..12, was $locationPrecisionDecimals"
        }
        require(startTime == null || endTime == null || startTime!! <= endTime!!) {
            "startTime must be <= endTime (start=$startTime, end=$endTime)"
        }
    }
}
