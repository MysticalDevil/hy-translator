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
            progressType = "downloading",
            downloaded = 20L,
            total = 100L,
        )

        assertEquals(
            ModelDownloadState.Downloading(
                model = org.devil.hytranslator.data.ModelOptions.getByKey("Q6_K"),
                progress = DownloadProgress.Downloading(downloaded = 20L, total = 100L),
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
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertEquals(
            ModelDownloadState.Completed(
                model = org.devil.hytranslator.data.ModelOptions.getByKey("Q4_K_M"),
                path = "/files/models/model.gguf",
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
            progressType = null,
            downloaded = null,
            total = null,
        )

        assertEquals(
            AiAssetDownloadState.Error(
                asset = AiAsset.AsrStreamingZipformer,
                message = "network failed",
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
