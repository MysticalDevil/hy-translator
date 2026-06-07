package org.devil.hytranslator.service

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.devil.hytranslator.MainActivity
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.AiAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNotificationIntentsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun modelRetryIntent_targetsStartServiceWithModelKey() {
        val model = ModelOptions.getByKey("Q4_K_M")

        val intent = DownloadNotificationIntents.modelRetry(context, model)

        assertEquals(ModelDownloadService.ACTION_START, intent.action)
        assertEquals(ModelDownloadService::class.java.name, intent.component?.className)
        assertEquals(model.key, intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_KEY))
    }

    @Test
    fun aiAssetRetryIntent_targetsStartServiceWithAssetName() {
        val intent = DownloadNotificationIntents.aiAssetRetry(
            context = context,
            asset = AiAsset.OcrPpOcrV5Mobile,
        )

        assertEquals(AiAssetDownloadService.ACTION_START, intent.action)
        assertEquals(AiAssetDownloadService::class.java.name, intent.component?.className)
        assertEquals(
            AiAsset.OcrPpOcrV5Mobile.name,
            intent.getStringExtra(AiAssetDownloadService.EXTRA_ASSET),
        )
    }

    @Test
    fun cancelIntents_targetMatchingCancelServices() {
        val modelIntent = DownloadNotificationIntents.modelCancel(context)
        val aiAssetIntent = DownloadNotificationIntents.aiAssetCancel(
            context = context,
            asset = AiAsset.AsrStreamingZipformer,
        )

        assertEquals(ModelDownloadService.ACTION_CANCEL, modelIntent.action)
        assertEquals(ModelDownloadService::class.java.name, modelIntent.component?.className)
        assertEquals(AiAssetDownloadService.ACTION_CANCEL, aiAssetIntent.action)
        assertEquals(AiAssetDownloadService::class.java.name, aiAssetIntent.component?.className)
        assertEquals(
            AiAsset.AsrStreamingZipformer.name,
            aiAssetIntent.getStringExtra(AiAssetDownloadService.EXTRA_ASSET),
        )
    }

    @Test
    fun contentIntents_targetMainActivityWithNotificationNavigationExtras() {
        val model = ModelOptions.getByKey("Q4_K_M")
        val modelIntent = DownloadNotificationIntents.modelContent(context, model)
        val aiAssetIntent = DownloadNotificationIntents.aiAssetContent(
            context = context,
            asset = AiAsset.OcrPpOcrV5Mobile,
        )

        assertEquals(MainActivity::class.java.name, modelIntent.component?.className)
        assertEquals(MainActivity::class.java.name, aiAssetIntent.component?.className)
        assertTrue(modelIntent.hasSingleTopClearTopFlags())
        assertTrue(aiAssetIntent.hasSingleTopClearTopFlags())
        assertEquals(
            NotificationNavigation.TARGET_MODEL_DOWNLOAD,
            modelIntent.getStringExtra(NotificationNavigation.EXTRA_TARGET),
        )
        assertEquals(model.key, modelIntent.getStringExtra(NotificationNavigation.EXTRA_MODEL_KEY))
        assertEquals(
            NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
            aiAssetIntent.getStringExtra(NotificationNavigation.EXTRA_TARGET),
        )
        assertEquals(
            AiAsset.OcrPpOcrV5Mobile.name,
            aiAssetIntent.getStringExtra(NotificationNavigation.EXTRA_AI_ASSET),
        )
    }

    private fun Intent.hasSingleTopClearTopFlags(): Boolean =
        flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0 &&
            flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0
}
