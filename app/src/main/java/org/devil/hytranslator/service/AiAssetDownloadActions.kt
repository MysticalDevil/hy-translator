package org.devil.hytranslator.service

import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.AiAsset

interface AiAssetDownloadActions {
    val state: StateFlow<AiAssetDownloadService.State>

    fun start(asset: AiAsset)

    fun cancel()
}
