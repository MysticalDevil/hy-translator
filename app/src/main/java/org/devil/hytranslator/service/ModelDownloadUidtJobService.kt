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
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.platform.download.ModelDownloadStateStore

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ModelDownloadUidtJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = DownloadJobRegistry<ModelOption>()
    private lateinit var notifier: ModelDownloadNotifier
    private lateinit var stateStore: ModelDownloadStateStore
    private var currentModel: ModelOption? = null

    override fun onCreate() {
        super.onCreate()
        notifier = ModelDownloadNotifier(applicationContext)
        stateStore = ModelDownloadStateStore(applicationContext)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val model = params.transientExtras.getString(EXTRA_MODEL_KEY)
            ?.let { runCatching { ModelOptions.getByKey(it) }.getOrNull() }
            ?: ModelOptions.recommend(applicationContext)

        downloadJobs.cancelAll()
        currentModel = model

        val runtime = DownloadForegroundRuntime(
            scope = serviceScope,
            publishNotification = { _, notification ->
                setNotification(
                    params,
                    ModelDownloadNotifier.NOTIFICATION_ID,
                    notification,
                    JOB_END_NOTIFICATION_POLICY_DETACH,
                )
            },
            callbacks = ModelUidtDownloadCallbacks(params),
        )
        val job = runtime.start(
            target = model,
            initialNotification = notifier.progressNotification(model, downloaded = 0L, total = 0L),
        ) {
            val repository = DownloadServiceDependencies.modelRepositoryFactory(applicationContext)
            repository.selectModel(model)
            repository.download()
        }
        downloadJobs.put(model, job)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val model = currentModel?.takeIf { downloadJobs.isActive(it) } ?: return false
        runBlocking(Dispatchers.IO) {
            stateStore.setError(model, DOWNLOAD_INTERRUPTED_MESSAGE)
        }
        downloadJobs.cancelAll()
        notifier.cancel()
        currentModel = null
        return true
    }

    override fun onDestroy() {
        downloadJobs.cancelAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class ModelUidtDownloadCallbacks(
        private val params: JobParameters,
    ) : DownloadForegroundRuntime.Callbacks<ModelOption> {
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
            downloadJobs.remove(target)
            currentModel = null
            notifier.showComplete(target)
            jobFinished(params, false)
        }

        override fun onError(target: ModelOption, message: String) {
            downloadJobs.remove(target)
            currentModel = null
            notifier.showError(message, target)
            jobFinished(params, false)
        }
    }

    companion object {
        private const val EXTRA_MODEL_KEY = "model_key"
        private const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"

        fun extras(model: ModelOption): Bundle =
            Bundle().apply {
                putString(EXTRA_MODEL_KEY, model.key)
            }
    }
}
