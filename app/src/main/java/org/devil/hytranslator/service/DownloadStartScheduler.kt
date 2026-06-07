package org.devil.hytranslator.service

import android.content.Context
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.ModelOption

internal class DownloadStartScheduler(
    private val context: Context,
    private val policy: DownloadStartPolicy = DownloadStartPolicy,
) {
    fun startModelDownload(model: ModelOption) {
        when (policy.choose()) {
            DownloadTransport.UserInitiatedDataTransferJob -> {
                ModelDownloadUidtScheduler.schedule(context, model)
            }
            DownloadTransport.ForegroundService -> {
                ModelDownloadService.startForegroundService(context, model)
            }
        }
    }

    fun startAiAssetDownload(asset: AiAsset) {
        when (policy.choose()) {
            DownloadTransport.UserInitiatedDataTransferJob -> {
                AiAssetDownloadUidtScheduler.schedule(context, asset)
            }
            DownloadTransport.ForegroundService -> {
                AiAssetDownloadService.startForegroundService(context, asset)
            }
        }
    }
}
