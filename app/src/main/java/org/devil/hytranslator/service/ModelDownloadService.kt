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
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.data.repository.ModelRepositoryImpl
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.domain.model.ModelOption

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private lateinit var notifier: ModelDownloadNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = ModelDownloadNotifier(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val model = intent.getStringExtra(EXTRA_MODEL_KEY)
                    ?.let { ModelOptions.getByKey(it) }
                    ?: ModelOptions.recommend(applicationContext)
                startDownload(model)
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

    private fun startDownload(model: ModelOption) {
        downloadJob?.cancel()
        _state.value = ModelDownloadState.Downloading(model, null)

        val initialNotification = notifier.progressNotification(model, downloaded = 0L, total = 0L)
        ServiceCompat.startForeground(
            this,
            ModelDownloadNotifier.NOTIFICATION_ID,
            initialNotification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        downloadJob = serviceScope.launch {
            val repository = ModelRepositoryImpl(applicationContext)
            repository.selectModel(model)
            try {
                repository.download().collect { progress ->
                    _state.value = ModelDownloadState.Downloading(model, progress)
                    when (progress) {
                        is DownloadProgress.Started -> updateForeground(model, progress)
                        is DownloadProgress.Downloading -> updateForeground(model, progress)
                        is DownloadProgress.Completed -> {
                            _state.value = ModelDownloadState.Completed(model, progress.path)
                            notifier.showLoading(model)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                        is DownloadProgress.Error -> {
                            _state.value = ModelDownloadState.Error(model, progress.message)
                            notifier.showError(progress.message)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                _state.value = ModelDownloadState.Error(model, message)
                notifier.showError(message)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun updateForeground(model: ModelOption, progress: DownloadProgress) {
        if (!shouldUpdateForeground(progress)) return

        val notification = when (progress) {
            is DownloadProgress.Started -> notifier.progressNotification(
                model = model,
                downloaded = progress.existing,
                total = progress.total,
            )
            is DownloadProgress.Downloading -> notifier.progressNotification(
                model = model,
                downloaded = progress.downloaded,
                total = progress.total,
            )
            is DownloadProgress.Completed -> notifier.loadingNotification(model)
            is DownloadProgress.Error -> notifier.errorNotification(progress.message)
        }
        ServiceCompat.startForeground(
            this,
            ModelDownloadNotifier.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
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
        _state.value = ModelDownloadState.Idle
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_START = "org.devil.hytranslator.action.START_MODEL_DOWNLOAD"
        private const val EXTRA_MODEL_KEY = "model_key"
        const val ACTION_CANCEL = "org.devil.hytranslator.action.CANCEL_MODEL_DOWNLOAD"

        private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
        val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

        fun start(context: Context, model: ModelOption) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODEL_KEY, model.key)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
