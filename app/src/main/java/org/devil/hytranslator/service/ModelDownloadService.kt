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
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.platform.download.ModelDownloadStateStore

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentModel: ModelOption? = null
    private var terminalStateReached = false
    private lateinit var notifier: ModelDownloadNotifier
    private lateinit var stateStore: ModelDownloadStateStore

    override fun onCreate() {
        super.onCreate()
        notifier = ModelDownloadNotifier(applicationContext)
        stateStore = ModelDownloadStateStore(applicationContext)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        interruptActiveDownload()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        interruptActiveDownload()
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(model: ModelOption) {
        downloadJob?.cancel()
        currentModel = model
        terminalStateReached = false

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
            val repository = DownloadServiceDependencies.modelRepositoryFactory(applicationContext)
            repository.selectModel(model)
            stateStore.setDownloading(model, null)
            try {
                repository.download().collect { progress ->
                    stateStore.setDownloading(model, progress)
                    when (progress) {
                        is DownloadProgress.Started -> updateForeground(model, progress)
                        is DownloadProgress.Downloading -> updateForeground(model, progress)
                        is DownloadProgress.Completed -> {
                            terminalStateReached = true
                            stateStore.setCompleted(model, progress.path)
                            notifier.showComplete(model)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                        is DownloadProgress.Error -> {
                            terminalStateReached = true
                            stateStore.setError(model, progress.message)
                            notifier.showError(progress.message, model)
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                terminalStateReached = true
                stateStore.setError(model, message)
                notifier.showError(message, model)
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
            is DownloadProgress.Completed -> notifier.completeNotification(model)
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
        terminalStateReached = true
        downloadJob?.cancel()
        downloadJob = null
        runBlocking(Dispatchers.IO) {
            stateStore.setIdle()
        }
        notifier.cancel()
        currentModel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun interruptActiveDownload() {
        val model = currentModel?.takeIf {
            downloadJob?.isActive == true && !terminalStateReached
        } ?: return

        terminalStateReached = true
        runBlocking(Dispatchers.IO) {
            stateStore.setError(model, DOWNLOAD_INTERRUPTED_MESSAGE)
        }
        downloadJob?.cancel()
        downloadJob = null
        currentModel = null
        notifier.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "org.devil.hytranslator.action.START_MODEL_DOWNLOAD"
        const val EXTRA_MODEL_KEY = "model_key"
        const val ACTION_CANCEL = "org.devil.hytranslator.action.CANCEL_MODEL_DOWNLOAD"
        private const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"

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
