package org.devil.hytranslator.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.ModelOption

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ModelDownloadUidtJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val model = params.transientExtras.getString(EXTRA_MODEL_KEY)
            ?.let { runCatching { ModelOptions.getByKey(it) }.getOrNull() }
            ?: ModelOptions.recommend(applicationContext)

        setNotification(
            params,
            ModelDownloadNotifier.NOTIFICATION_ID,
            ModelDownloadNotifier(applicationContext)
                .progressNotification(model, downloaded = 0L, total = 0L),
            JOB_END_NOTIFICATION_POLICY_DETACH,
        )
        ModelDownloadService.startForegroundService(applicationContext, model)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val EXTRA_MODEL_KEY = "model_key"

        fun extras(model: ModelOption): Bundle =
            Bundle().apply {
                putString(EXTRA_MODEL_KEY, model.key)
            }
    }
}
