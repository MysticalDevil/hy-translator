package org.devil.hytranslator.service

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.devil.hytranslator.platform.download.AiAssetDownloadStateStore
import org.devil.hytranslator.platform.download.ModelDownloadStateStore
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNotificationActionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelStateStore = ModelDownloadStateStore(context)
    private val aiAssetStateStore = AiAssetDownloadStateStore(context)

    @After
    fun tearDown() = runBlocking {
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @Test
    fun modelCancelAction_clearsPersistedDownloadState() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        modelStateStore.setDownloading(
            model = model,
            progress = DownloadProgress.Downloading(downloaded = 4L, total = 10L),
        )

        context.startService(
            Intent(context, ModelDownloadService::class.java)
                .setAction(ModelDownloadService.ACTION_CANCEL),
        )

        val state = withTimeout(5_000) {
            modelStateStore.state.first { it is ModelDownloadState.Idle }
        }

        assertSame(ModelDownloadState.Idle, state)
    }

    @Test
    fun aiAssetCancelAction_clearsOnlyTargetAssetPersistedDownloadState() = runBlocking {
        aiAssetStateStore.setDownloading(
            asset = AiAsset.AsrStreamingZipformer,
            progress = DownloadProgress.Downloading(downloaded = 4L, total = 10L),
        )
        aiAssetStateStore.setDownloading(
            asset = AiAsset.OcrPpOcrV5Mobile,
            progress = DownloadProgress.Downloading(downloaded = 2L, total = 10L),
        )

        context.startService(
            Intent(context, AiAssetDownloadService::class.java)
                .setAction(AiAssetDownloadService.ACTION_CANCEL)
                .putExtra(AiAssetDownloadService.EXTRA_ASSET, AiAsset.OcrPpOcrV5Mobile.name),
        )

        val ocrState = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { it is AiAssetDownloadState.Idle }
        }
        val asrState = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.AsrStreamingZipformer)
                .first { it is AiAssetDownloadState.Downloading }
        }

        assertSame(AiAssetDownloadState.Idle, ocrState)
        assertSame(AiAsset.AsrStreamingZipformer, (asrState as AiAssetDownloadState.Downloading).asset)
    }
}
