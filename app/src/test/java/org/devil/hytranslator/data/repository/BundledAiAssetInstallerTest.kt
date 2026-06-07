package org.devil.hytranslator.data.repository

import org.devil.hytranslator.domain.model.AiAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledAiAssetInstallerTest {
    @Test
    fun requiredBundledAssetTargets_matchAiAssetSpecs() {
        val targets = BundledAiAssetInstaller.requiredBundledAssetTargets(AiAssetSpecs.defaultSpecs)

        assertEquals(
            AiAssetSpecs.defaultSpecs.values.sumOf { spec -> spec.files.size },
            targets.size,
        )
        assertTrue(
            targets.any {
                it.assetPath == "ai-assets/pp-ocrv5-mobile/PP-OCRv5_mobile_rec.nb" &&
                    it.outputPath == it.assetPath
            },
        )
        assertTrue(
            targets.any {
                it.assetPath ==
                    "ai-assets/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/tokens.txt" &&
                    it.outputPath == it.assetPath
            },
        )
        assertTrue(targets.all { it.minBytes > 0L })
    }

    @Test
    fun defaultAiAssetSpecs_keepAsrChineseEnglishOnlyForCurrentRuntime() {
        val asrSpec = AiAssetSpecs.defaultSpecs.getValue(AiAsset.AsrStreamingZipformer)

        assertEquals(
            "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
            asrSpec.directoryName,
        )
    }
}
