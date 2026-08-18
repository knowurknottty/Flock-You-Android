package com.flockyou.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.flockyou.data.repository.DetectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only detection export backend.
 *
 * This is the narrow facade that presentation/UI lanes consume. It produces a local file in the
 * requested [ExportFormat] and returns a shareable [Uri]; it never performs any network activity,
 * never contacts Google, and never uploads data.
 *
 * Internals (Room DAO, SQLCipher, serialization) are fully hidden from callers.
 */
@Singleton
class DetectionExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionRepository,
) {
    companion object {
        private const val TAG = "DetectionExportService"
        private const val EXPORT_DIR_NAME = "exports"
        private const val EXPORT_RETENTION_MS = 24L * 60L * 60L * 1000L // 24h
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Generate a local export for [request] and return a shareable [Uri] to the written file.
     *
     * Runs on [Dispatchers.IO]. Returns [Result.failure] if serialization or file IO fails.
     * The produced file lives under the app's cache directory and is subject to 24-hour retention.
     */
    suspend fun export(request: ExportRequest): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Pull the detection snapshot. When both time bounds are present, use a bounded query
            // to avoid materializing and decrypting the whole table.
            val detections = if (request.startTime != null && request.endTime != null) {
                repository.getDetectionsBetween(request.startTime, request.endTime)
            } else {
                repository.getAllDetectionsSnapshot()
            }

            val content = DetectionExportSerializer.serialize(request, detections)
            val file = writeExport(content, request.format.fileExtension)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for format ${request.format}", e)
            Result.failure(e)
        }
    }

    /**
     * The suggested display filename for a pending export (deterministic, contains no identifiers).
     */
    fun suggestedFilename(request: ExportRequest): String {
        val timestamp = dateFormat.format(Date())
        return "flockyou-detections_$timestamp.${request.format.fileExtension}"
    }

    private fun writeExport(content: String, extension: String): File {
        val exportDir = File(context.cacheDir, EXPORT_DIR_NAME)
        exportDir.mkdirs()
        cleanupOldExports(exportDir)
        val file = File(exportDir, suggestedBaseName(extension))
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun suggestedBaseName(extension: String): String {
        return "flockyou-detections_${dateFormat.format(Date())}.$extension"
    }

    private fun cleanupOldExports(exportDir: File) {
        exportDir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > EXPORT_RETENTION_MS) {
                file.delete()
            }
        }
    }
}
