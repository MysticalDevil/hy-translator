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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.devil.hytranslator.data.repository.AiAssetRepositoryImpl
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.DownloadProgress

class AiAssetDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private lateinit var notifier: AiAssetDownloadNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = AiAssetDownloadNotifier(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload()
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
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(asset: AiAsset) {
        downloadJob?.cancel()
        _state.value = AiAssetDownloadState.Downloading(asset, null)

        ServiceCompat.startForeground(
            this,
            AiAssetDownloadNotifier.NOTIFICATION_ID,
            notifier.progressNotification(asset, downloaded = 0L, total = 0L),
            foregroundServiceType(),
        )

        downloadJob = serviceScope.launch {
            val repository = AiAssetRepositoryImpl(applicationContext)
            try {
                repository.download(asset).collect { progress ->
                    _state.value = AiAssetDownloadState.Downloading(asset, progress)
                    when (progress) {
                        is DownloadProgress.Started -> updateForeground(asset, progress)
                        is DownloadProgress.Downloading -> updateForeground(asset, progress)
                        is DownloadProgress.Completed -> {
                            _state.value = AiAssetDownloadState.Completed(asset, progress.path)
                            notifier.showComplete(asset)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                        is DownloadProgress.Error -> {
                            _state.value = AiAssetDownloadState.Error(asset, progress.message)
                            notifier.showError(asset, progress.message)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                _state.value = AiAssetDownloadState.Error(asset, message)
                notifier.showError(asset, message)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
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
            AiAssetDownloadNotifier.NOTIFICATION_ID,
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

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = AiAssetDownloadState.Idle
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    companion object {
        private const val ACTION_START = "org.devil.hytranslator.action.START_AI_ASSET_DOWNLOAD"
        const val ACTION_CANCEL = "org.devil.hytranslator.action.CANCEL_AI_ASSET_DOWNLOAD"
        private const val EXTRA_ASSET = "asset"

        private val _state = MutableStateFlow<AiAssetDownloadState>(AiAssetDownloadState.Idle)
        val state: StateFlow<AiAssetDownloadState> = _state.asStateFlow()

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
    }
}
