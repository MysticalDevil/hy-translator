package org.devil.hytranslator.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import org.devil.hytranslator.domain.model.AiAsset

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AiAssetDownloadUidtJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val asset = params.transientExtras.getString(EXTRA_ASSET)
            ?.let { runCatching { AiAsset.valueOf(it) }.getOrNull() }
            ?: return false

        setNotification(
            params,
            AiAssetDownloadNotifier.notificationId(asset),
            AiAssetDownloadNotifier(applicationContext)
                .progressNotification(asset, downloaded = 0L, total = 0L),
            JOB_END_NOTIFICATION_POLICY_DETACH,
        )
        AiAssetDownloadService.startForegroundService(applicationContext, asset)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val EXTRA_ASSET = "asset"

        fun extras(asset: AiAsset): Bundle =
            Bundle().apply {
                putString(EXTRA_ASSET, asset.name)
            }
    }
}
