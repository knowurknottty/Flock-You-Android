package com.flockyou.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset

class ExportFilenamePolicyTest {

    @Test
    fun `suggested filename is deterministic for an explicit instant`() {
        val instant = Instant.parse("2026-08-18T16:00:01Z")

        assertEquals(
            "flockyou-detections_2026-08-18_16-00-01.geojson",
            ExportFilenamePolicy.suggestedFilename("geojson", instant, ZoneOffset.UTC),
        )
    }

    @Test
    fun `concurrent export file creation cannot collide within one second`() = runBlocking {
        val directory = Files.createTempDirectory("flock-export-test").toFile()
        try {
            val instant = Instant.parse("2026-08-18T16:00:01Z")
            val files = (0 until 200).map {
                async(Dispatchers.Default) {
                    ExportFilenamePolicy.createUniqueFile(
                        directory = directory,
                        extension = "csv",
                        instant = instant,
                        zoneId = ZoneOffset.UTC,
                    )
                }
            }.awaitAll()

            assertEquals(200, files.map { it.name }.toSet().size)
            assertTrue(files.all { it.exists() })
            assertTrue(files.all { it.name.startsWith("flockyou-detections_2026-08-18_16-00-01_") })
            assertTrue(files.all { it.name.endsWith(".csv") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
