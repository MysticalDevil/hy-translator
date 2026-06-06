package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore

class AiAssetDownloadController(
    private val context: Context,
) : AiAssetDownloadActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateStore = AiAssetDownloadStateStore(context)

    override val state: StateFlow<AiAssetDownloadState> = stateStore.state.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AiAssetDownloadState.Idle,
    )

    override fun state(asset: AiAsset): StateFlow<AiAssetDownloadState> =
        stateStore.state(asset).stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AiAssetDownloadState.Idle,
        )

    override fun start(asset: AiAsset) {
        AiAssetDownloadService.start(context, asset)
    }

    override fun cancel() {
        AiAssetDownloadService.cancel(context)
    }

    override fun cancel(asset: AiAsset) {
        AiAssetDownloadService.cancel(context, asset)
    }
}
