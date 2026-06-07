package org.devil.hytranslator.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
            ModelDownloadState.Error(
                model = model,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
                attempt = 1L,
                jobId = "model:Q4_K_M:1",
            ),
            state,
        )
    }

    @Test
    fun modelStateStore_incrementsAttemptOnlyForNewDownloadAttempt() = runBlocking {
        val model = ModelOptions.getByKey("Q4_K_M")
        modelStateStore.setIdle()

        modelStateStore.setDownloading(
            model = model,
            progress = DownloadProgress.Started(total = 100L, existing = 0L),
        )
        val first = withTimeout(5_000) {
            modelStateStore.state.first { it is ModelDownloadState.Downloading }
        } as ModelDownloadState.Downloading

        delay(SPEED_SAMPLE_DELAY_MS)
        modelStateStore.setDownloading(
            model = model,
            progress = DownloadProgress.Downloading(downloaded = 50L, total = 100L),
        )
        val progressUpdate = withTimeout(5_000) {
            modelStateStore.state.first { state ->
                state is ModelDownloadState.Downloading &&
                    state.progress == DownloadProgress.Downloading(downloaded = 50L, total = 100L)
            }
        } as ModelDownloadState.Downloading

        modelStateStore.setError(model, "network failed")
        modelStateStore.setDownloading(
            model = model,
            progress = DownloadProgress.Started(total = 100L, existing = 0L),
        )
        val retry = withTimeout(5_000) {
            modelStateStore.state.first { state ->
                state is ModelDownloadState.Downloading &&
                    state.progress == DownloadProgress.Started(total = 100L, existing = 0L) &&
                    state.attempt == 2L
            }
        } as ModelDownloadState.Downloading

        assertEquals(1L, first.attempt)
        assertEquals(1L, progressUpdate.attempt)
        assertEquals(2L, retry.attempt)
        assertEquals("model:Q4_K_M:1", first.jobId)
        assertEquals(first.jobId, progressUpdate.jobId)
        assertEquals("model:Q4_K_M:2", retry.jobId)
        assertTrue(
            "Expected positive model transfer speed",
            (progressUpdate.bytesPerSecond ?: 0L) > 0L,
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
                attempt = 1L,
                jobId = "ai:AsrStreamingZipformer:1",
            ),
            asrState,
        )
        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.OcrPpOcrV5Mobile,
                message = DOWNLOAD_INTERRUPTED_MESSAGE,
                attempt = 1L,
                jobId = "ai:OcrPpOcrV5Mobile:1",
            ),
            ocrState,
        )
    }

    @Test
    fun aiAssetStateStore_tracksAttemptsPerAsset() = runBlocking {
        aiAssetStateStore.setIdle()

        aiAssetStateStore.setDownloading(
            asset = AiAsset.AsrStreamingZipformer,
            progress = DownloadProgress.Started(total = 100L, existing = 0L),
        )
        aiAssetStateStore.setDownloading(
            asset = AiAsset.OcrPpOcrV5Mobile,
            progress = DownloadProgress.Started(total = 80L, existing = 0L),
        )
        val asrFirst = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.AsrStreamingZipformer)
                .first { it is AiAssetDownloadState.Downloading }
        } as AiAssetDownloadState.Downloading
        val ocrFirst = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { it is AiAssetDownloadState.Downloading }
        } as AiAssetDownloadState.Downloading

        delay(SPEED_SAMPLE_DELAY_MS)
        aiAssetStateStore.setDownloading(
            asset = AiAsset.OcrPpOcrV5Mobile,
            progress = DownloadProgress.Downloading(downloaded = 40L, total = 80L),
        )
        val ocrProgressUpdate = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { state ->
                    state is AiAssetDownloadState.Downloading &&
                        state.progress == DownloadProgress.Downloading(downloaded = 40L, total = 80L)
                }
        } as AiAssetDownloadState.Downloading

        aiAssetStateStore.setError(AiAsset.OcrPpOcrV5Mobile, "network failed")
        aiAssetStateStore.setDownloading(
            asset = AiAsset.OcrPpOcrV5Mobile,
            progress = DownloadProgress.Started(total = 80L, existing = 0L),
        )
        val ocrRetry = withTimeout(5_000) {
            aiAssetStateStore.state(AiAsset.OcrPpOcrV5Mobile)
                .first { state ->
                    state is AiAssetDownloadState.Downloading &&
                        state.attempt == 2L
                }
        } as AiAssetDownloadState.Downloading

        assertEquals(1L, asrFirst.attempt)
        assertEquals(1L, ocrFirst.attempt)
        assertEquals(2L, ocrRetry.attempt)
        assertEquals("ai:AsrStreamingZipformer:1", asrFirst.jobId)
        assertEquals("ai:OcrPpOcrV5Mobile:1", ocrFirst.jobId)
        assertEquals(ocrFirst.jobId, ocrProgressUpdate.jobId)
        assertEquals("ai:OcrPpOcrV5Mobile:2", ocrRetry.jobId)
        assertTrue(
            "Expected positive OCR transfer speed",
            (ocrProgressUpdate.bytesPerSecond ?: 0L) > 0L,
        )
        assertSame(AiAsset.OcrPpOcrV5Mobile, ocrRetry.asset)
    }

    private companion object {
        const val DOWNLOAD_INTERRUPTED_MESSAGE = "Download was interrupted"
        const val SPEED_SAMPLE_DELAY_MS = 25L
    }
}
