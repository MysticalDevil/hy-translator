package org.devil.hytranslator.service

import org.devil.hytranslator.domain.model.AiAsset

sealed interface NotificationDestination {
    data class ModelDownload(val modelKey: String?) : NotificationDestination
    data class AiAssetDownload(val asset: AiAsset) : NotificationDestination
}

object NotificationNavigation {
    const val EXTRA_TARGET = "org.devil.hytranslator.extra.NOTIFICATION_TARGET"
    const val EXTRA_MODEL_KEY = "org.devil.hytranslator.extra.NOTIFICATION_MODEL_KEY"
    const val EXTRA_AI_ASSET = "org.devil.hytranslator.extra.NOTIFICATION_AI_ASSET"

    const val TARGET_MODEL_DOWNLOAD = "model_download"
    const val TARGET_AI_ASSET_DOWNLOAD = "ai_asset_download"

    fun destination(
        target: String?,
        modelKey: String?,
        aiAssetName: String?,
    ): NotificationDestination? =
        when (target) {
            TARGET_MODEL_DOWNLOAD -> NotificationDestination.ModelDownload(modelKey)
            TARGET_AI_ASSET_DOWNLOAD -> aiAssetName
                ?.let { name -> runCatching { AiAsset.valueOf(name) }.getOrNull() }
                ?.let(NotificationDestination::AiAssetDownload)

            else -> null
        }
}
