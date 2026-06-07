package org.devil.hytranslator.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore

class AiAssetDownloadController(
    private val context: Context,
) : AiAssetDownloadActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateStore = AiAssetDownloadStateStore(context)
    private val startScheduler = DownloadStartScheduler(context)

    override val state: StateFlow<AiAssetDownloadState> = stateStore.state.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AiAssetDownloadState.Idle,
    )

    override fun auditInterruptedDownloads() {
        scope.launch {
            stateStore.markDownloadingAsInterrupted(DOWNLOAD_INTERRUPTED_MESSAGE)
        }
    }

    override fun state(asset: AiAsset): StateFlow<AiAssetDownloadState> =
        stateStore.state(asset).stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AiAssetDownloadState.Idle,
        )

    override fun start(asset: AiAsset) {
        startScheduler.startAiAssetDownload(asset)
    }

    override fun cancel() {
        AiAssetDownloadService.cancel(context)
    }

    override fun cancel(asset: AiAsset) {
        AiAssetDownloadService.cancel(context, asset)
    }

    private companion object {
        const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"
    }
}
