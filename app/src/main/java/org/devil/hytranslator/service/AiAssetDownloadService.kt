package org.devil.hytranslator.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore

class AiAssetDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = DownloadJobRegistry<AiAsset>()
    private lateinit var notifier: AiAssetDownloadNotifier
    private lateinit var stateStore: AiAssetDownloadStateStore
    private lateinit var downloadRuntime: DownloadForegroundRuntime<AiAsset>

    override fun onCreate() {
        super.onCreate()
        notifier = AiAssetDownloadNotifier(applicationContext)
        stateStore = AiAssetDownloadStateStore(applicationContext)
        downloadRuntime = DownloadForegroundRuntime(
            service = this,
            scope = serviceScope,
            notificationId = AiAssetDownloadNotifier::notificationId,
            foregroundServiceType = ::foregroundServiceType,
            callbacks = AiAssetDownloadCallbacks(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val asset = intent.getStringExtra(EXTRA_ASSET)
                    ?.let { runCatching { AiAsset.valueOf(it) }.getOrNull() }
                cancelDownload(asset)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val asset = intent.getStringExtra(EXTRA_ASSET)
                    ?.let { runCatching { AiAsset.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                startDownload(asset)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        interruptActiveDownloads()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        interruptActiveDownloads()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(asset: AiAsset) {
        downloadJobs.cancel(asset)

        val job = downloadRuntime.start(
            target = asset,
            initialNotification = notifier.progressNotification(asset, downloaded = 0L, total = 0L),
        ) {
            val repository = DownloadServiceDependencies.aiAssetRepositoryFactory(applicationContext)
            repository.download(asset)
        }
        downloadJobs.put(asset, job)
    }

    private fun cancelDownload(asset: AiAsset? = null) {
        val cancelledAssets =
            if (asset != null) {
                downloadJobs.cancel(asset)
            } else {
                downloadJobs.cancelAll()
            }
        runBlocking(Dispatchers.IO) {
            if (asset != null) {
                stateStore.setIdle(asset)
            } else {
                stateStore.setIdle()
            }
        }
        cancelledAssets.forEach { notifier.cancel(it) }
        if (!hasActiveDownloads()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun interruptActiveDownloads() {
        val activeAssets = downloadJobs.cancelActiveAndClear()
        if (activeAssets.isEmpty()) return

        runBlocking(Dispatchers.IO) {
            activeAssets.forEach { asset ->
                stateStore.setError(asset, DOWNLOAD_INTERRUPTED_MESSAGE)
            }
        }
        activeAssets.forEach { notifier.cancel(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishDownload(asset: AiAsset) {
        downloadJobs.remove(asset)
        if (!hasActiveDownloads()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun hasActiveDownloads(): Boolean =
        downloadJobs.hasActive()

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private inner class AiAssetDownloadCallbacks : DownloadForegroundRuntime.Callbacks<AiAsset> {
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
            notifier.showComplete(target)
            finishDownload(target)
        }

        override fun onError(target: AiAsset, message: String) {
            notifier.showError(target, message)
            finishDownload(target)
        }
    }

    companion object {
        const val ACTION_START = "org.devil.hytranslator.action.START_AI_ASSET_DOWNLOAD"
        const val ACTION_CANCEL = "org.devil.hytranslator.action.CANCEL_AI_ASSET_DOWNLOAD"
        const val EXTRA_ASSET = "asset"
        private const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"

        fun start(context: Context, asset: AiAsset) {
            val intent = Intent(context, AiAssetDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ASSET, asset.name)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, AiAssetDownloadService::class.java)
                .setAction(ACTION_CANCEL)
            context.startService(intent)
        }

        fun cancel(context: Context, asset: AiAsset) {
            val intent = Intent(context, AiAssetDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ASSET, asset.name)
            context.startService(intent)
        }
    }
}
