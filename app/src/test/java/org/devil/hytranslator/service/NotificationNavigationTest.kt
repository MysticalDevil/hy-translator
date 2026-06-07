package org.devil.hytranslator.service

import org.devil.hytranslator.domain.model.AiAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun destination_returnsModelDownloadTargetWithModelKey() {
        val destination = NotificationNavigation.destination(
            target = NotificationNavigation.TARGET_MODEL_DOWNLOAD,
            modelKey = "Q4_K_M",
            aiAssetName = null,
        )

        assertEquals(
            NotificationDestination.ModelDownload(modelKey = "Q4_K_M"),
            destination,
        )
    }

    @Test
    fun destination_returnsAiAssetDownloadTargetForKnownAsset() {
        val destination = NotificationNavigation.destination(
            target = NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
            modelKey = null,
            aiAssetName = AiAsset.OcrPpOcrV5Mobile.name,
        )

        assertEquals(
            NotificationDestination.AiAssetDownload(AiAsset.OcrPpOcrV5Mobile),
            destination,
        )
    }

    @Test
    fun destination_ignoresUnknownAiAssetTarget() {
        val destination = NotificationNavigation.destination(
            target = NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
            modelKey = null,
            aiAssetName = "UnknownAsset",
        )

        assertNull(destination)
    }

    @Test
    fun destination_ignoresUnknownTarget() {
        val destination = NotificationNavigation.destination(
            target = "unknown",
            modelKey = "Q4_K_M",
            aiAssetName = AiAsset.AsrStreamingZipformer.name,
        )

        assertNull(destination)
    }
}
