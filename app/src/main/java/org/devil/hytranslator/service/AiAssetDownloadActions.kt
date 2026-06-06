package org.devil.hytranslator.service

import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState

interface AiAssetDownloadActions {
    val state: StateFlow<AiAssetDownloadState>

    fun state(asset: AiAsset): StateFlow<AiAssetDownloadState>

    fun start(asset: AiAsset)

    fun cancel()

    fun cancel(asset: AiAsset)
}
