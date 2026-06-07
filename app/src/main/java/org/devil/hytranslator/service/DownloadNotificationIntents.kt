package org.devil.hytranslator.service

import android.content.Context
import android.content.Intent
import org.devil.hytranslator.MainActivity
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.ModelOption

internal object DownloadNotificationIntents {
    fun modelCancel(context: Context): Intent =
        Intent(context, ModelDownloadService::class.java)
            .setAction(ModelDownloadService.ACTION_CANCEL)

    fun modelRetry(context: Context, model: ModelOption): Intent =
        Intent(context, ModelDownloadService::class.java)
            .setAction(ModelDownloadService.ACTION_START)
            .putExtra(ModelDownloadService.EXTRA_MODEL_KEY, model.key)

    fun modelContent(context: Context, model: ModelOption? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                NotificationNavigation.EXTRA_TARGET,
                NotificationNavigation.TARGET_MODEL_DOWNLOAD,
            )
            model?.let {
                putExtra(NotificationNavigation.EXTRA_MODEL_KEY, it.key)
            }
        }

    fun aiAssetCancel(context: Context, asset: AiAsset): Intent =
        Intent(context, AiAssetDownloadService::class.java)
            .setAction(AiAssetDownloadService.ACTION_CANCEL)
            .putExtra(AiAssetDownloadService.EXTRA_ASSET, asset.name)

    fun aiAssetRetry(context: Context, asset: AiAsset): Intent =
        Intent(context, AiAssetDownloadService::class.java)
            .setAction(AiAssetDownloadService.ACTION_START)
            .putExtra(AiAssetDownloadService.EXTRA_ASSET, asset.name)

    fun aiAssetContent(context: Context, asset: AiAsset): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                NotificationNavigation.EXTRA_TARGET,
                NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
            )
            putExtra(NotificationNavigation.EXTRA_AI_ASSET, asset.name)
        }
}
