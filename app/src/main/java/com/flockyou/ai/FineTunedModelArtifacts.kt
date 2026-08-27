package com.flockyou.ai

enum class HostedModelFormat {
    GGUF
}

data class HostedModelArtifact(
    val id: String,
    val displayName: String,
    val format: HostedModelFormat,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val runtimeCompatible: Boolean,
    val compatibilityReason: String
)

object FineTunedModelArtifacts {
    val GEMMA_FLOCK_Q8_0 = HostedModelArtifact(
        id = "gemma-flock-q8-0",
        displayName = "Flock Fine-Tuned Gemma Q8_0",
        format = HostedModelFormat.GGUF,
        downloadUrl = "https://mega.nz/file/WzAiwIba#-lYBgLIkxmAgzmd_CXcKEjMIhuuYlvpfWFUeVXMnxlc",
        sizeBytes = 291_545_376L,
        sha256 = "82b323bf05eba698b87a39d1eca8ea31506222aff25b415f6388135069725b57",
        runtimeCompatible = false,
        compatibilityReason = "Current MediaPipe backend does not support raw GGUF models."
    )
}
