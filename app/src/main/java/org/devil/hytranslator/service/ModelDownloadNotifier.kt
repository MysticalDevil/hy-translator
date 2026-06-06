package org.devil.hytranslator.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.devil.hytranslator.MainActivity
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelOption
import kotlin.math.roundToInt

class ModelDownloadNotifier(
    private val context: Context,
) : ModelDownloadNotifications {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)
    private var lastProgressPercent = -1
    private var lastProgressUpdateMillis = 0L
    private var lastRateSampleBytes = 0L
    private var lastRateSampleMillis = 0L
    private var smoothedBytesPerSecond: Double? = null

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.model_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun showProgress(model: ModelOption, progress: DownloadProgress) {
        if (!canPostNotifications()) return

        when (progress) {
            is DownloadProgress.Started -> notifyProgress(
                model = model,
                downloaded = progress.existing,
                total = progress.total,
            )

            is DownloadProgress.Downloading -> notifyProgress(
                model = model,
                downloaded = progress.downloaded,
                total = progress.total,
            )

            is DownloadProgress.Completed -> showComplete(model)
            is DownloadProgress.Error -> showError(progress.message)
        }
    }

    fun showLoading(model: ModelOption) {
        if (!canPostNotifications()) return
        resetProgressThrottle()
        notificationManager.notify(
            NOTIFICATION_ID,
            loadingNotification(model),
        )
    }

    override fun showComplete() {
        showComplete(model = null)
    }

    fun showComplete(model: ModelOption?) {
        if (!canPostNotifications()) return
        resetProgressThrottle()
        notificationManager.notify(
            NOTIFICATION_ID,
            completeNotification(model),
        )
    }

    override fun showError(message: String) {
        showError(message = message, model = null)
    }

    fun showError(message: String, model: ModelOption?) {
        if (!canPostNotifications()) return
        resetProgressThrottle()
        notificationManager.notify(
            NOTIFICATION_ID,
            errorNotification(message, model),
        )
    }

    fun cancel() {
        resetProgressThrottle()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    private fun notifyProgress(model: ModelOption, downloaded: Long, total: Long) {
        if (!shouldPublishProgress(downloaded, total)) return

        notificationManager.notify(
            NOTIFICATION_ID,
            progressNotification(model, downloaded, total),
        )
    }

    fun shouldPublishProgress(downloaded: Long, total: Long): Boolean =
        !shouldSkipProgressUpdate(percent(downloaded, total))

    fun progressNotification(model: ModelOption, downloaded: Long, total: Long): Notification {
        val percent = percent(downloaded, total)
        val bytesPerSecond = updateTransferRate(downloaded)

        val builder = baseBuilder(model)
            .setContentTitle(
                context.getString(
                    R.string.model_download_notification_progress_title,
                    percent,
                ),
            )
            .setContentText(progressText(model, downloaded, total))
            .setSubText(progressStatus(downloaded, total, bytesPerSecond))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(NOTIFICATION_COLOR)
            .addAction(cancelAction())

        if (total > 0) {
            builder.setProgress(PROGRESS_MAX, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            if (supportsPromotedOngoing()) {
                builder
                    .setRequestPromotedOngoing(true)
                    .setShortCriticalText("$percent%")
            }
            if (total > 0) {
                builder.setStyle(progressStyle(percent))
            }
        }

        return builder.build()
    }

    fun loadingNotification(model: ModelOption): Notification =
        baseBuilder(model)
            .setContentText(context.getString(R.string.model_download_notification_loading))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setColor(NOTIFICATION_COLOR)
            .build()

    fun completeNotification(model: ModelOption? = null): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.model_download_notification_complete))
            .setContentIntent(contentIntent(model))
            .setAutoCancel(true)
        model?.let { builder.setSubText(it.name) }
        return builder.build()
    }

    fun errorNotification(message: String, model: ModelOption? = null): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.model_download_notification_title))
            .setContentText(
                context.getString(R.string.model_download_notification_error, message),
            )
            .setContentIntent(model?.let(::contentIntent) ?: contentIntent())
            .setAutoCancel(true)
        model?.let { builder.addAction(retryAction(it)) }
        return builder.build()
    }

    private fun shouldSkipProgressUpdate(percent: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isSamePercent = percent == lastProgressPercent
        val isTooSoon = now - lastProgressUpdateMillis < MIN_PROGRESS_UPDATE_INTERVAL_MS
        if (isSamePercent && isTooSoon) return true

        lastProgressPercent = percent
        lastProgressUpdateMillis = now
        return false
    }

    private fun resetProgressThrottle() {
        lastProgressPercent = -1
        lastProgressUpdateMillis = 0L
        lastRateSampleBytes = 0L
        lastRateSampleMillis = 0L
        smoothedBytesPerSecond = null
    }

    private fun baseBuilder(model: ModelOption): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.model_download_notification_title))
            .setSubText(model.name)
            .setContentIntent(contentIntent(model))
            .setLocalOnly(true)

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun supportsPromotedOngoing(): Boolean =
        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun progressStyle(percent: Int): Notification.ProgressStyle {
        val progressUnits = percent.coerceIn(0, PROGRESS_MAX)
        return Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progressUnits)
            .setProgressTrackerIcon(
                Icon.createWithResource(context, R.drawable.ic_download_tracker),
            )
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(25)
                        .setColor(Color.rgb(77, 171, 247)),
                    Notification.ProgressStyle.Segment(50)
                        .setColor(Color.rgb(32, 201, 151)),
                    Notification.ProgressStyle.Segment(25)
                        .setColor(Color.rgb(255, 212, 59)),
                ),
            )
            .setProgressPoints(
                listOf(
                    Notification.ProgressStyle.Point(25)
                        .setColor(Color.WHITE),
                    Notification.ProgressStyle.Point(50)
                        .setColor(Color.WHITE),
                    Notification.ProgressStyle.Point(75)
                        .setColor(Color.WHITE),
                ),
            )
    }

    private fun cancelAction(): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_cancel),
            context.getString(R.string.model_download_notification_cancel),
            PendingIntent.getService(
                context,
                CANCEL_REQUEST_CODE,
                Intent(context, ModelDownloadService::class.java)
                    .setAction(ModelDownloadService.ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun retryAction(model: ModelOption): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_download_tracker),
            context.getString(R.string.action_retry),
            PendingIntent.getService(
                context,
                RETRY_REQUEST_CODE,
                Intent(context, ModelDownloadService::class.java)
                    .setAction(ModelDownloadService.ACTION_START)
                    .putExtra(ModelDownloadService.EXTRA_MODEL_KEY, model.key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun contentIntent(model: ModelOption? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    NotificationNavigation.EXTRA_TARGET,
                    NotificationNavigation.TARGET_MODEL_DOWNLOAD,
                )
                model?.let {
                    putExtra(NotificationNavigation.EXTRA_MODEL_KEY, it.key)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun progressText(model: ModelOption, downloaded: Long, total: Long): String {
        val downloadedMb = downloaded / BYTES_PER_MB
        val totalMb = total / BYTES_PER_MB
        return context.getString(
            R.string.model_download_notification_progress_text,
            model.name,
            downloadedMb,
            totalMb,
        )
    }

    private fun progressStatus(
        downloaded: Long,
        total: Long,
        bytesPerSecond: Double?,
    ): String {
        if (total <= 0L || bytesPerSecond == null || bytesPerSecond <= 0.0) {
            return context.getString(R.string.model_download_notification_progress_calculating)
        }

        val remainingSeconds = ((total - downloaded).coerceAtLeast(0L) / bytesPerSecond)
            .roundToInt()
        return context.getString(
            R.string.model_download_notification_progress_status,
            formatRate(bytesPerSecond),
            formatRemaining(remainingSeconds),
        )
    }

    private fun updateTransferRate(downloaded: Long): Double? {
        val now = SystemClock.elapsedRealtime()
        val previousBytes = lastRateSampleBytes
        val previousMillis = lastRateSampleMillis

        lastRateSampleBytes = downloaded
        lastRateSampleMillis = now

        if (previousMillis == 0L || downloaded <= previousBytes) return smoothedBytesPerSecond

        val elapsedSeconds = (now - previousMillis) / 1_000.0
        if (elapsedSeconds <= 0.0) return smoothedBytesPerSecond

        val currentBytesPerSecond = (downloaded - previousBytes) / elapsedSeconds
        smoothedBytesPerSecond = smoothedBytesPerSecond
            ?.let { previous -> (previous * RATE_SMOOTHING_WEIGHT) + (currentBytesPerSecond * (1 - RATE_SMOOTHING_WEIGHT)) }
            ?: currentBytesPerSecond
        return smoothedBytesPerSecond
    }

    private fun formatRate(bytesPerSecond: Double): String =
        if (bytesPerSecond >= BYTES_PER_MB) {
            "%.1f MB".format(bytesPerSecond / BYTES_PER_MB)
        } else {
            "%.0f KB".format(bytesPerSecond / BYTES_PER_KB)
        }

    private fun formatRemaining(seconds: Int): String {
        if (seconds < 60) return "${seconds.coerceAtLeast(1)}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (remainingMinutes == 0) {
            "${hours}h"
        } else {
            "${hours}h ${remainingMinutes}m"
        }
    }

    private fun percent(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded.toDouble() / total.toDouble()) * PROGRESS_MAX)
            .roundToInt()
            .coerceIn(0, PROGRESS_MAX)
    }

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_ID = 1001
        private const val PROGRESS_MAX = 100
        private const val CANCEL_REQUEST_CODE = 1002
        private const val RETRY_REQUEST_CODE = 1005
        const val BYTES_PER_MB = 1024.0 * 1024.0
        private const val BYTES_PER_KB = 1024.0
        private const val RATE_SMOOTHING_WEIGHT = 0.7
        private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
        private val NOTIFICATION_COLOR = Color.rgb(32, 201, 151)
    }
}
