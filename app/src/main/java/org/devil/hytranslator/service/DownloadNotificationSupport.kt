package org.devil.hytranslator.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.devil.hytranslator.R
import kotlin.math.roundToInt

internal class DownloadProgressThrottle(
    private val minUpdateIntervalMillis: Long = MIN_PROGRESS_UPDATE_INTERVAL_MS,
) {
    private var lastProgressPercent = -1
    private var lastProgressUpdateMillis = 0L

    fun shouldPublish(downloaded: Long, total: Long): Boolean {
        val percent = downloadPercent(downloaded, total)
        val now = SystemClock.elapsedRealtime()
        val isSamePercent = percent == lastProgressPercent
        val isTooSoon = now - lastProgressUpdateMillis < minUpdateIntervalMillis
        if (isSamePercent && isTooSoon) return false

        lastProgressPercent = percent
        lastProgressUpdateMillis = now
        return true
    }

    fun reset() {
        lastProgressPercent = -1
        lastProgressUpdateMillis = 0L
    }
}

internal data class DownloadProgressSegment(
    val units: Int,
    @param:ColorInt val color: Int,
)

internal data class DownloadProgressPoint(
    val units: Int,
    @param:ColorInt val color: Int,
)

internal fun downloadPercent(downloaded: Long, total: Long): Int {
    if (total <= 0L) return 0
    return ((downloaded.toDouble() / total.toDouble()) * DOWNLOAD_PROGRESS_MAX)
        .roundToInt()
        .coerceIn(0, DOWNLOAD_PROGRESS_MAX)
}

internal fun canPostDownloadNotifications(
    context: Context,
    notificationManager: NotificationManager,
): Boolean {
    if (!notificationManager.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal fun downloadProgressStyle(
    context: Context,
    percent: Int,
    segments: List<DownloadProgressSegment>,
    points: List<DownloadProgressPoint>,
): Notification.ProgressStyle =
    Notification.ProgressStyle()
        .setStyledByProgress(true)
        .setProgress(percent.coerceIn(0, DOWNLOAD_PROGRESS_MAX))
        .setProgressTrackerIcon(
            Icon.createWithResource(context, R.drawable.ic_download_tracker),
        )
        .setProgressSegments(
            segments.map { segment ->
                Notification.ProgressStyle.Segment(segment.units)
                    .setColor(segment.color)
            },
        )
        .setProgressPoints(
            points.map { point ->
                Notification.ProgressStyle.Point(point.units)
                    .setColor(point.color)
            },
        )

internal const val DOWNLOAD_PROGRESS_MAX = 100
private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
