package org.devil.hytranslator.data.repository

import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssetSpecsTest {

    @Test
    fun ocrPpOcrV5Spec_usesOfficialMobileAssets() {
        val spec = AiAssetSpecs.defaultSpecs.getValue(AiAsset.OcrPpOcrV5Mobile)

        assertEquals("pp-ocrv5-mobile", spec.directoryName)
        assertEquals(
            listOf(
                "PP-OCRv5_mobile_det.nb",
                "PP-OCRv5_mobile_rec.nb",
                "ppocr_keys_ocrv5.txt",
            ),
            spec.files.map { it.name },
        )

        val detSource = spec.files[0].source as AiAssetFileSource.TarGz
        assertEquals(R.string.url_ocr_ppocrv5_det_tar, detSource.urlResId)
        assertEquals("PP-OCRv5_mobile_det.nb", detSource.entryName)

        val recSource = spec.files[1].source as AiAssetFileSource.TarGz
        assertEquals(R.string.url_ocr_ppocrv5_rec_tar, recSource.urlResId)
        assertEquals("PP-OCRv5_mobile_rec.nb", recSource.entryName)

        val labelsSource = spec.files[2].source as AiAssetFileSource.TarGz
        assertEquals(R.string.url_ocr_labels_tar, labelsSource.urlResId)
        assertEquals("ppocr_keys_ocrv5.txt", labelsSource.entryName)
    }

    @Test
    fun allAiAssetSpecs_haveConfiguredSourcesAndValidationSizes() {
        AiAsset.entries.forEach { asset ->
            val spec = AiAssetSpecs.defaultSpecs.getValue(asset)
            assertTrue(spec.files.isNotEmpty())
            spec.files.forEach { file ->
                assertTrue("${file.name} minBytes must be positive", file.minBytes > 0L)
                assertTrue("${file.name} expectedBytes must be positive", file.expectedBytes > 0L)
                assertTrue("${file.name} source URL resource must be set", file.source.urlResId != 0)
            }
        }
    }
}
