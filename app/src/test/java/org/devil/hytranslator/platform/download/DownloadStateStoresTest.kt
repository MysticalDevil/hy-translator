package org.devil.hytranslator.platform.download

import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetDownloadState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.ModelDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DownloadStateStoresTest {

    @Test
    fun modelDownloadStateFromRecord_restoresDownloadingProgress() {
        val state = modelDownloadStateFromRecord(
            status = STATUS_DOWNLOADING,
            modelKey = "Q6_K",
            path = null,
            error = null,
            attempt = 3L,
            progressType = "downloading",
            downloaded = 20L,
            total = 100L,
        )

        assertEquals(
            ModelDownloadState.Downloading(
                model = org.devil.hytranslator.data.ModelOptions.getByKey("Q6_K"),
                progress = DownloadProgress.Downloading(downloaded = 20L, total = 100L),
                attempt = 3L,
            ),
            state,
        )
    }

    @Test
    fun modelDownloadStateFromRecord_restoresCompletedPath() {
        val state = modelDownloadStateFromRecord(
            status = STATUS_COMPLETED,
            modelKey = "Q4_K_M",
            path = "/files/models/model.gguf",
            error = null,
            attempt = 4L,
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertEquals(
            ModelDownloadState.Completed(
                model = org.devil.hytranslator.data.ModelOptions.getByKey("Q4_K_M"),
                path = "/files/models/model.gguf",
                attempt = 4L,
            ),
            state,
        )
    }

    @Test
    fun modelDownloadStateFromRecord_returnsIdleForInvalidRecord() {
        val state = modelDownloadStateFromRecord(
            status = STATUS_COMPLETED,
            modelKey = "missing",
            path = "/files/models/model.gguf",
            error = null,
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertSame(ModelDownloadState.Idle, state)
    }

    @Test
    fun aiAssetDownloadStateFromRecord_restoresError() {
        val state = aiAssetDownloadStateFromRecord(
            status = STATUS_ERROR,
            assetName = AiAsset.AsrStreamingZipformer.name,
            path = null,
            error = "network failed",
            attempt = 2L,
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.AsrStreamingZipformer,
                message = "network failed",
                attempt = 2L,
            ),
            state,
        )
    }

    @Test
    fun aiAssetDownloadStateFromRecord_returnsIdleWhenCompletedPathIsMissing() {
        val state = aiAssetDownloadStateFromRecord(
            status = STATUS_COMPLETED,
            assetName = AiAsset.OcrPpOcrV5Mobile.name,
            path = null,
            error = null,
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertSame(AiAssetDownloadState.Idle, state)
    }
}
