package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState

class AiAssetDownloadController(
    private val context: Context,
) : AiAssetDownloadActions {
    override val state: StateFlow<AiAssetDownloadState> = AiAssetDownloadService.state

    override fun start(asset: AiAsset) {
        AiAssetDownloadService.start(context, asset)
    }

    override fun cancel() {
        AiAssetDownloadService.cancel(context)
    }
}
