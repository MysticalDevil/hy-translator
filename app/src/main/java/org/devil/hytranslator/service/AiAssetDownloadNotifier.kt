package org.devil.hytranslator.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import kotlin.math.roundToInt

class AiAssetDownloadNotifier(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)
    private var lastProgressPercent = -1
    private var lastProgressUpdateMillis = 0L

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ai_asset_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun showComplete(asset: AiAsset) {
        if (!canPostNotifications()) return
        resetProgressThrottle()
        notificationManager.notify(notificationId(asset), completeNotification(asset))
    }

    fun showError(asset: AiAsset, message: String) {
        if (!canPostNotifications()) return
        resetProgressThrottle()
        notificationManager.notify(notificationId(asset), errorNotification(asset, message))
    }

    fun cancel(asset: AiAsset) {
        resetProgressThrottle()
        notificationManager.cancel(notificationId(asset))
    }

    fun shouldPublishProgress(downloaded: Long, total: Long): Boolean =
        !shouldSkipProgressUpdate(percent(downloaded, total))

    fun progressNotification(asset: AiAsset, downloaded: Long, total: Long): Notification {
        val percent = percent(downloaded, total)
        val builder = baseBuilder(asset)
            .setContentTitle(
                context.getString(
                    R.string.ai_asset_download_notification_progress_title,
                    percent,
                ),
            )
            .setContentText(progressText(asset, downloaded, total))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(NOTIFICATION_COLOR)
            .addAction(cancelAction(asset))

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
                builder.setStyle(progressStyle(percent, asset))
            }
        }

        return builder.build()
    }

    fun completeNotification(asset: AiAsset): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ai_asset_download_notification_complete))
            .setContentText(assetName(asset))
            .setContentIntent(contentIntent(asset))
            .setAutoCancel(true)
            .build()

    fun errorNotification(asset: AiAsset, message: String): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ai_asset_download_notification_error))
            .setContentText(context.getString(R.string.asset_error, assetName(asset), message))
            .setContentIntent(contentIntent(asset))
            .addAction(retryAction(asset))
            .setAutoCancel(true)
            .build()

    private fun baseBuilder(asset: AiAsset): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ai_asset_download_notification_title))
            .setSubText(assetName(asset))
            .setContentIntent(contentIntent(asset))
            .setLocalOnly(true)

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun supportsPromotedOngoing(): Boolean =
        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun progressStyle(percent: Int, asset: AiAsset): Notification.ProgressStyle {
        val progressUnits = percent.coerceIn(0, PROGRESS_MAX)
        val accentColor = when (asset) {
            AiAsset.AsrStreamingZipformer -> Color.rgb(77, 171, 247)
            AiAsset.OcrPpOcrV5Mobile -> Color.rgb(32, 201, 151)
        }
        return Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progressUnits)
            .setProgressTrackerIcon(
                Icon.createWithResource(context, R.drawable.ic_download_tracker),
            )
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(40)
                        .setColor(accentColor),
                    Notification.ProgressStyle.Segment(35)
                        .setColor(Color.rgb(255, 212, 59)),
                    Notification.ProgressStyle.Segment(25)
                        .setColor(Color.rgb(132, 94, 247)),
                ),
            )
            .setProgressPoints(
                listOf(
                    Notification.ProgressStyle.Point(50)
                        .setColor(Color.WHITE),
                    Notification.ProgressStyle.Point(80)
                        .setColor(Color.WHITE),
                ),
            )
    }

    private fun cancelAction(asset: AiAsset): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_cancel),
            context.getString(R.string.model_download_notification_cancel),
            PendingIntent.getService(
                context,
                notificationId(asset),
                DownloadNotificationIntents.aiAssetCancel(context, asset),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun retryAction(asset: AiAsset): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_download_tracker),
            context.getString(R.string.action_retry),
            PendingIntent.getService(
                context,
                notificationId(asset) + RETRY_REQUEST_CODE_OFFSET,
                DownloadNotificationIntents.aiAssetRetry(context, asset),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun contentIntent(asset: AiAsset): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(asset),
            DownloadNotificationIntents.aiAssetContent(context, asset),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun assetName(asset: AiAsset): String =
        when (asset) {
            AiAsset.AsrStreamingZipformer -> context.getString(R.string.asset_asr_zipformer)
            AiAsset.OcrPpOcrV5Mobile -> context.getString(R.string.asset_ocr_ppocrv5)
        }

    private fun progressText(asset: AiAsset, downloaded: Long, total: Long): String {
        val downloadedMb = downloaded / BYTES_PER_MB
        val totalMb = total / BYTES_PER_MB
        return context.getString(
            R.string.ai_asset_download_notification_progress_text,
            assetName(asset),
            downloadedMb,
            totalMb,
        )
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
    }

    private fun percent(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded.toDouble() / total.toDouble()) * PROGRESS_MAX)
            .roundToInt()
            .coerceIn(0, PROGRESS_MAX)
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "ai_asset_downloads"
        const val ASR_NOTIFICATION_ID = 1003
        const val OCR_NOTIFICATION_ID = 1004
        private const val PROGRESS_MAX = 100
        private const val BYTES_PER_MB = 1024.0 * 1024.0
        private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
        private const val RETRY_REQUEST_CODE_OFFSET = 100
        private val NOTIFICATION_COLOR = Color.rgb(77, 171, 247)

        fun notificationId(asset: AiAsset): Int =
            when (asset) {
                AiAsset.AsrStreamingZipformer -> ASR_NOTIFICATION_ID
                AiAsset.OcrPpOcrV5Mobile -> OCR_NOTIFICATION_ID
            }
    }
}
