package com.flockyou.data.export

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Thread-safe filename policy shared by display suggestions and actual export files. */
internal object ExportFilenamePolicy {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun suggestedFilename(
        extension: String,
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = "flockyou-detections_${timestampFormatter.format(instant.atZone(zoneId))}.$extension"

    /**
     * Atomically create a unique cache file. The timestamp remains operator-readable while
     * File.createTempFile supplies collision resistance for concurrent exports in the same second.
     */
    fun createUniqueFile(
        directory: File,
        extension: String,
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): File {
        val timestamp = timestampFormatter.format(instant.atZone(zoneId))
        return File.createTempFile("flockyou-detections_${timestamp}_", ".$extension", directory)
    }
}
