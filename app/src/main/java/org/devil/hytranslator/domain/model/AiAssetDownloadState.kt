package org.devil.hytranslator.domain.model

sealed class AiAssetDownloadState {
    data object Idle : AiAssetDownloadState()
    data class Downloading(
        val asset: AiAsset,
        val progress: DownloadProgress?,
    ) : AiAssetDownloadState()
    data class Completed(val asset: AiAsset, val path: String) : AiAssetDownloadState()
    data class Error(val asset: AiAsset, val message: String) : AiAssetDownloadState()
}
