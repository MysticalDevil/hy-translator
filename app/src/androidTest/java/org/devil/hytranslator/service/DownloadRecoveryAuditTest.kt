package org.devil.hytranslator.service

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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRecoveryAuditTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelStateStore = ModelDownloadStateStore(context)
    private val aiAssetStateStore = AiAssetDownloadStateStore(context)

    @After
    fun tearDown() = runBlocking {
        modelStateStore.setIdle()
        aiAssetStateStore.setIdle()
    }

    @Test
    fun modelAudit_marksPersistedDownloadingStateAsInterruptedError() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        modelStateStore.setDownloading(
            model = model,
            progress = DownloadProgress.Downloading(downloaded = 4L, total = 10L),
        )

        ModelDownloadController(context).auditInterruptedDownloads()

        val state = withTimeout(5_000) {
            modelStateStore.state.first { it is ModelDownloadState.Error }
        }

        assertEquals(
            ModelDownloadState.Error(model = model, message = DOWNLOAD_INTERRUPTED_MESSAGE),
            state,
        )
    }

    @Test
    fun aiAssetAudit_marksEveryPersistedDownloadingAssetAsInterruptedError() = runBlocking {
        aiAssetStateStore.setDownloading(
            asset = AiAsset.AsrStreamingZipformer,
            progress = DownloadProgress.Downloading(downloaded = 4L, total = 10L),
        )
        aiAssetStateStore.setDownloading(
            asset = AiAsset.OcrPpOcrV5Mobile,
            progress = DownloadProgress.Downloading(downloaded = 2L, total = 10L),
        )

        AiAssetDownloadController(context).auditInterruptedDownloads()

        val asrState = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.AsrStreamingZipformer)
                .first { it is AiAssetDownloadState.Error }
        }
        val ocrState = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { it is AiAssetDownloadState.Error }
        }

        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.AsrStreamingZipformer,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
            ),
            asrState,
        )
        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.OcrPpOcrV5Mobile,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
            ),
            ocrState,
        )
    }

    private companion object {
        const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"
    }
}
