package org.devil.hytranslator.domain.model

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(
        val model: ModelOption,
        val progress: DownloadProgress?,
    ) : ModelDownloadState()
    data class Completed(val model: ModelOption, val path: String) : ModelDownloadState()
    data class Error(val model: ModelOption, val message: String) : ModelDownloadState()
}
