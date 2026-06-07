package org.devil.hytranslator.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.ModelOption

internal object ModelDownloadUidtScheduler {
    fun schedule(context: Context, model: ModelOption) {
        val job = JobInfo.Builder(
            MODEL_DOWNLOAD_UIDT_JOB_ID,
            ComponentName(context, ModelDownloadUidtJobService::class.java),
        )
            .setUserInitiated(true)
            .setRequiredNetwork(downloadNetworkRequest())
            .setEstimatedNetworkBytes(model.estimatedBytes, NETWORK_BYTES_UNKNOWN)
            .setTransientExtras(ModelDownloadUidtJobService.extras(model))
            .build()

        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}

internal object AiAssetDownloadUidtScheduler {
    fun schedule(context: Context, asset: AiAsset) {
        val job = JobInfo.Builder(
            aiAssetDownloadUidtJobId(asset),
            ComponentName(context, AiAssetDownloadUidtJobService::class.java),
        )
            .setUserInitiated(true)
            .setRequiredNetwork(downloadNetworkRequest())
            .setEstimatedNetworkBytes(NETWORK_BYTES_UNKNOWN, NETWORK_BYTES_UNKNOWN)
            .setTransientExtras(AiAssetDownloadUidtJobService.extras(asset))
            .build()

        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}

private fun downloadNetworkRequest(): NetworkRequest =
    NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

private val ModelOption.estimatedBytes: Long
    get() = (sizeGb * BYTES_PER_GB).toLong().coerceAtLeast(1L)

internal const val MODEL_DOWNLOAD_UIDT_JOB_ID = 2101
internal const val ASR_DOWNLOAD_UIDT_JOB_ID = 2102
internal const val OCR_DOWNLOAD_UIDT_JOB_ID = 2103

internal fun aiAssetDownloadUidtJobId(asset: AiAsset): Int =
    when (asset) {
        AiAsset.AsrStreamingZipformer -> ASR_DOWNLOAD_UIDT_JOB_ID
        AiAsset.OcrPpOcrV5Mobile -> OCR_DOWNLOAD_UIDT_JOB_ID
    }

private const val BYTES_PER_GB = 1_000_000_000L
private const val NETWORK_BYTES_UNKNOWN = -1L
