package org.devil.hytranslator.domain.model

enum class AiAsset {
    AsrStreamingZipformer,
    OcrPpOcrV5Mobile,
}

sealed interface AiAssetState {
    data object NotDownloaded : AiAssetState
    data object Ready : AiAssetState
    data class Downloading(val progress: DownloadProgress?) : AiAssetState
    data class Error(val message: String) : AiAssetState
}
