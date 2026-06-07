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
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.platform.download.ModelDownloadStateStore

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = DownloadJobRegistry<ModelOption>()
    private var currentModel: ModelOption? = null
    private var terminalStateReached = false
    private lateinit var notifier: ModelDownloadNotifier
    private lateinit var stateStore: ModelDownloadStateStore
    private lateinit var downloadRuntime: DownloadForegroundRuntime<ModelOption>

    override fun onCreate() {
        super.onCreate()
        notifier = ModelDownloadNotifier(applicationContext)
        stateStore = ModelDownloadStateStore(applicationContext)
        downloadRuntime = DownloadForegroundRuntime(
            service = this,
            scope = serviceScope,
            notificationId = { ModelDownloadNotifier.NOTIFICATION_ID },
            foregroundServiceType = ::foregroundServiceType,
            callbacks = ModelDownloadCallbacks(),
        )
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
        downloadJobs.cancelAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(model: ModelOption) {
        downloadJobs.cancelAll()
        currentModel = model
        terminalStateReached = false

        val initialNotification = notifier.progressNotification(model, downloaded = 0L, total = 0L)
        val job = downloadRuntime.start(
            target = model,
            initialNotification = initialNotification,
        ) {
            val repository = DownloadServiceDependencies.modelRepositoryFactory(applicationContext)
            repository.selectModel(model)
            repository.download()
        }
        downloadJobs.put(model, job)
    }

    private fun cancelDownload() {
        terminalStateReached = true
        downloadJobs.cancelAll()
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
            downloadJobs.isActive(it) && !terminalStateReached
        } ?: return

        terminalStateReached = true
        runBlocking(Dispatchers.IO) {
            stateStore.setError(model, DOWNLOAD_INTERRUPTED_MESSAGE)
        }
        downloadJobs.cancelAll()
        currentModel = null
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

    private inner class ModelDownloadCallbacks : DownloadForegroundRuntime.Callbacks<ModelOption> {
        override suspend fun setDownloading(target: ModelOption, progress: DownloadProgress?) {
            stateStore.setDownloading(target, progress)
        }

        override suspend fun setCompleted(target: ModelOption, path: String) {
            stateStore.setCompleted(target, path)
        }

        override suspend fun setError(target: ModelOption, message: String) {
            stateStore.setError(target, message)
        }

        override fun shouldPublishProgress(
            target: ModelOption,
            downloaded: Long,
            total: Long,
        ): Boolean =
            notifier.shouldPublishProgress(downloaded, total)

        override fun progressNotification(
            target: ModelOption,
            downloaded: Long,
            total: Long,
        ): Notification =
            notifier.progressNotification(target, downloaded, total)

        override fun onCompleted(target: ModelOption) {
            terminalStateReached = true
            downloadJobs.remove(target)
            notifier.showComplete(target)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }

        override fun onError(target: ModelOption, message: String) {
            terminalStateReached = true
            downloadJobs.remove(target)
            notifier.showError(message, target)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
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
