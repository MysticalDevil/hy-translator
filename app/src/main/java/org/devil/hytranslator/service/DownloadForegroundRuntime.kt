package org.devil.hytranslator.service

import android.app.Notification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.devil.hytranslator.domain.model.DownloadProgress

internal class DownloadForegroundRuntime<T>(
    private val scope: CoroutineScope,
    private val publishNotification: (target: T, notification: Notification) -> Unit,
    private val callbacks: Callbacks<T>,
) {
    fun start(
        target: T,
        initialNotification: Notification,
        downloadFlow: suspend () -> Flow<DownloadProgress>,
    ): Job {
        publishNotification(target, initialNotification)

        return scope.launch {
            callbacks.setDownloading(target, null)
            try {
                downloadFlow().collect { progress ->
                    callbacks.setDownloading(target, progress)
                    when (progress) {
                        is DownloadProgress.Started -> updateForeground(
                            target = target,
                            downloaded = progress.existing,
                            total = progress.total,
                        )
                        is DownloadProgress.Downloading -> updateForeground(
                            target = target,
                            downloaded = progress.downloaded,
                            total = progress.total,
                        )
                        is DownloadProgress.Completed -> {
                            callbacks.setCompleted(target, progress.path)
                            callbacks.onCompleted(target)
                        }
                        is DownloadProgress.Error -> {
                            callbacks.setError(target, progress.message)
                            callbacks.onError(target, progress.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                callbacks.setError(target, message)
                callbacks.onError(target, message)
            }
        }
    }

    private fun updateForeground(target: T, downloaded: Long, total: Long) {
        if (!callbacks.shouldPublishProgress(target, downloaded, total)) return

        publishNotification(target, callbacks.progressNotification(target, downloaded, total))
    }

    interface Callbacks<T> {
        suspend fun setDownloading(target: T, progress: DownloadProgress?)

        suspend fun setCompleted(target: T, path: String)

        suspend fun setError(target: T, message: String)

        fun shouldPublishProgress(target: T, downloaded: Long, total: Long): Boolean

        fun progressNotification(target: T, downloaded: Long, total: Long): Notification

        fun onCompleted(target: T)

        fun onError(target: T, message: String)
    }
}
