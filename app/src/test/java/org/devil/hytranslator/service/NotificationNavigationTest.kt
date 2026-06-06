package org.devil.hytranslator.service

import org.devil.hytranslator.domain.model.AiAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun destination_returnsModelDownloadTarget() {
        val destination = NotificationNavigation.destination(
            target = NotificationNavigation.TARGET_MODEL_DOWNLOAD,
            modelKey = "Q6_K",
            aiAssetName = null,
        )

        assertEquals(NotificationDestination.ModelDownload("Q6_K"), destination)
    }

    @Test
    fun destination_returnsAiAssetDownloadTarget() {
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
    fun destination_returnsNullForInvalidAiAsset() {
        val destination = NotificationNavigation.destination(
            target = NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
            modelKey = null,
            aiAssetName = "missing",
        )

        assertNull(destination)
    }
}
