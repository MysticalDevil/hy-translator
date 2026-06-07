package org.devil.hytranslator.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.app.Notification
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AiAssetDownloadUidtJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = DownloadJobRegistry<AiAsset>()
    private lateinit var notifier: AiAssetDownloadNotifier
    private lateinit var stateStore: AiAssetDownloadStateStore

    override fun onCreate() {
        super.onCreate()
        notifier = AiAssetDownloadNotifier(applicationContext)
        stateStore = AiAssetDownloadStateStore(applicationContext)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val asset = params.transientExtras.getString(EXTRA_ASSET)
            ?.let { runCatching { AiAsset.valueOf(it) }.getOrNull() }
            ?: return false

        downloadJobs.cancel(asset)
        val runtime = DownloadForegroundRuntime(
            scope = serviceScope,
            publishNotification = { target, notification ->
                setNotification(
                    params,
                    AiAssetDownloadNotifier.notificationId(target),
                    notification,
                    JOB_END_NOTIFICATION_POLICY_DETACH,
                )
            },
            callbacks = AiAssetUidtDownloadCallbacks(params),
        )
        val job = runtime.start(
            target = asset,
            initialNotification = notifier.progressNotification(asset, downloaded = 0L, total = 0L),
        ) {
            val repository = DownloadServiceDependencies.aiAssetRepositoryFactory(applicationContext)
            repository.download(asset)
        }
        downloadJobs.put(asset, job)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val activeAssets = downloadJobs.cancelActiveAndClear()
        if (activeAssets.isEmpty()) return false

        runBlocking(Dispatchers.IO) {
            activeAssets.forEach { asset ->
                stateStore.setError(asset, DOWNLOAD_INTERRUPTED_MESSAGE)
            }
        }
        activeAssets.forEach { notifier.cancel(it) }
        return true
    }

    override fun onDestroy() {
        downloadJobs.cancelAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class AiAssetUidtDownloadCallbacks(
        private val params: JobParameters,
    ) : DownloadForegroundRuntime.Callbacks<AiAsset> {
        override suspend fun setDownloading(target: AiAsset, progress: DownloadProgress?) {
            stateStore.setDownloading(target, progress)
        }

        override suspend fun setCompleted(target: AiAsset, path: String) {
            stateStore.setCompleted(target, path)
        }

        override suspend fun setError(target: AiAsset, message: String) {
            stateStore.setError(target, message)
        }

        override fun shouldPublishProgress(
            target: AiAsset,
            downloaded: Long,
            total: Long,
        ): Boolean =
            notifier.shouldPublishProgress(downloaded, total)

        override fun progressNotification(
            target: AiAsset,
            downloaded: Long,
            total: Long,
        ): Notification =
            notifier.progressNotification(target, downloaded, total)

        override fun onCompleted(target: AiAsset) {
            downloadJobs.remove(target)
            notifier.showComplete(target)
            jobFinished(params, false)
        }

        override fun onError(target: AiAsset, message: String) {
            downloadJobs.remove(target)
            notifier.showError(target, message)
            jobFinished(params, false)
        }
    }

    companion object {
        private const val EXTRA_ASSET = "asset"
        private const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"

        fun extras(asset: AiAsset): Bundle =
            Bundle().apply {
                putString(EXTRA_ASSET, asset.name)
            }
    }
}
