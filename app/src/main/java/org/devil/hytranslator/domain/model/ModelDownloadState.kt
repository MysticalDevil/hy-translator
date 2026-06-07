package org.devil.hytranslator.domain.model

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(
        val model: ModelOption,
        val progress: DownloadProgress?,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : ModelDownloadState()
    data class Completed(
        val model: ModelOption,
        val path: String,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : ModelDownloadState()
    data class Error(
        val model: ModelOption,
        val message: String,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : ModelDownloadState()
}
