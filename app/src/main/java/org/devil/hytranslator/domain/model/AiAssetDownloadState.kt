package org.devil.hytranslator.domain.model

sealed class AiAssetDownloadState {
    data object Idle : AiAssetDownloadState()
    data class Downloading(
        val asset: AiAsset,
        val progress: DownloadProgress?,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : AiAssetDownloadState()
    data class Completed(
        val asset: AiAsset,
        val path: String,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : AiAssetDownloadState()
    data class Error(
        val asset: AiAsset,
        val message: String,
        val attempt: Long = 0L,
        val jobId: String = "",
    ) : AiAssetDownloadState()
}
