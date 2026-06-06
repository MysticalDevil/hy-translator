package org.devil.hytranslator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.devil.hytranslator.data.repository.AiAssetRepositoryImpl
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore

class AiAssetDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<AiAsset, Job>()
    private lateinit var notifier: AiAssetDownloadNotifier
    private lateinit var stateStore: AiAssetDownloadStateStore

    override fun onCreate() {
        super.onCreate()
        notifier = AiAssetDownloadNotifier(applicationContext)
        stateStore = AiAssetDownloadStateStore(applicationContext)
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

    override fun onDestroy() {
        val activeAssets = synchronized(downloadJobs) {
            downloadJobs.filterValues { it.isActive }.keys.toList()
        }
        if (activeAssets.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                activeAssets.forEach { asset ->
                    stateStore.setError(asset, DOWNLOAD_INTERRUPTED_MESSAGE)
                }
            }
        }
        synchronized(downloadJobs) {
            downloadJobs.values.forEach { it.cancel() }
            downloadJobs.clear()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(asset: AiAsset) {
        synchronized(downloadJobs) {
            downloadJobs.remove(asset)?.cancel()
        }

        ServiceCompat.startForeground(
            this,
            AiAssetDownloadNotifier.notificationId(asset),
            notifier.progressNotification(asset, downloaded = 0L, total = 0L),
            foregroundServiceType(),
        )

        val job = serviceScope.launch {
            val repository = AiAssetRepositoryImpl(applicationContext)
            stateStore.setDownloading(asset, null)
            try {
                repository.download(asset).collect { progress ->
                    stateStore.setDownloading(asset, progress)
                    when (progress) {
                        is DownloadProgress.Started -> updateForeground(asset, progress)
                        is DownloadProgress.Downloading -> updateForeground(asset, progress)
                        is DownloadProgress.Completed -> {
                            stateStore.setCompleted(asset, progress.path)
                            notifier.showComplete(asset)
                            finishDownload(asset)
                        }
                        is DownloadProgress.Error -> {
                            stateStore.setError(asset, progress.message)
                            notifier.showError(asset, progress.message)
                            finishDownload(asset)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                stateStore.setError(asset, message)
                notifier.showError(asset, message)
                finishDownload(asset)
            }
        }
        synchronized(downloadJobs) {
            downloadJobs[asset] = job
        }
    }

    private fun updateForeground(asset: AiAsset, progress: DownloadProgress) {
        if (!shouldUpdateForeground(progress)) return

        val notification = when (progress) {
            is DownloadProgress.Started -> notifier.progressNotification(
                asset = asset,
                downloaded = progress.existing,
                total = progress.total,
            )
            is DownloadProgress.Downloading -> notifier.progressNotification(
                asset = asset,
                downloaded = progress.downloaded,
                total = progress.total,
            )
            is DownloadProgress.Completed -> notifier.completeNotification(asset)
            is DownloadProgress.Error -> notifier.errorNotification(asset, progress.message)
        }
        ServiceCompat.startForeground(
            this,
            AiAssetDownloadNotifier.notificationId(asset),
            notification,
            foregroundServiceType(),
        )
    }

    private fun shouldUpdateForeground(progress: DownloadProgress): Boolean =
        when (progress) {
            is DownloadProgress.Started -> notifier.shouldPublishProgress(
                downloaded = progress.existing,
                total = progress.total,
            )

            is DownloadProgress.Downloading -> notifier.shouldPublishProgress(
                downloaded = progress.downloaded,
                total = progress.total,
            )

            is DownloadProgress.Completed,
            is DownloadProgress.Error,
            -> true
        }

    private fun cancelDownload(asset: AiAsset? = null) {
        val cancelledAssets = synchronized(downloadJobs) {
            if (asset != null) {
                downloadJobs.remove(asset)?.cancel()
                listOf(asset)
            } else {
                val assets = downloadJobs.keys.toList()
                downloadJobs.values.forEach { it.cancel() }
                downloadJobs.clear()
                assets
            }
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

    private fun finishDownload(asset: AiAsset) {
        synchronized(downloadJobs) {
            downloadJobs.remove(asset)
        }
        if (!hasActiveDownloads()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun hasActiveDownloads(): Boolean =
        synchronized(downloadJobs) {
            downloadJobs.values.any { it.isActive }
        }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
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
