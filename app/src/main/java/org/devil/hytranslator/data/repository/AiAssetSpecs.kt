package org.devil.hytranslator.data.repository

import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset

internal data class AiAssetSpec(
    val directoryName: String,
    val files: List<AiAssetFileSpec>,
)

internal data class AiAssetFileSpec(
    val name: String,
    val minBytes: Long,
    val expectedBytes: Long = minBytes,
    val source: AiAssetFileSource,
)

internal sealed interface AiAssetFileSource {
    val urlResId: Int

    data class Direct(
        override val urlResId: Int,
    ) : AiAssetFileSource

    data class TarGz(
        override val urlResId: Int,
        val entryName: String,
    ) : AiAssetFileSource
}

internal object AiAssetSpecs {
    val defaultSpecs: Map<AiAsset, AiAssetSpec> = mapOf(
        AiAsset.AsrStreamingZipformer to AiAssetSpec(
            directoryName = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
            files = listOf(
                AiAssetFileSpec(
                    name = "encoder-epoch-99-avg-1.onnx",
                    minBytes = 300_000_000L,
                    expectedBytes = 330_000_000L,
                    source = AiAssetFileSource.Direct(R.string.url_asr_zipformer_encoder),
                ),
                AiAssetFileSpec(
                    name = "decoder-epoch-99-avg-1.onnx",
                    minBytes = 10_000_000L,
                    expectedBytes = 13_900_000L,
                    source = AiAssetFileSource.Direct(R.string.url_asr_zipformer_decoder),
                ),
                AiAssetFileSpec(
                    name = "joiner-epoch-99-avg-1.onnx",
                    minBytes = 10_000_000L,
                    expectedBytes = 12_800_000L,
                    source = AiAssetFileSource.Direct(R.string.url_asr_zipformer_joiner),
                ),
                AiAssetFileSpec(
                    name = "tokens.txt",
                    minBytes = 10_000L,
                    expectedBytes = 56_000L,
                    source = AiAssetFileSource.Direct(R.string.url_asr_zipformer_tokens),
                ),
            ),
        ),
        AiAsset.OcrPpOcrV5Mobile to AiAssetSpec(
            directoryName = "pp-ocrv5-mobile",
            files = listOf(
                AiAssetFileSpec(
                    name = "PP-OCRv5_mobile_det.nb",
                    minBytes = 4_000_000L,
                    expectedBytes = 4_556_735L,
                    source = AiAssetFileSource.TarGz(
                        urlResId = R.string.url_ocr_ppocrv5_det_tar,
                        entryName = "PP-OCRv5_mobile_det.nb",
                    ),
                ),
                AiAssetFileSpec(
                    name = "PP-OCRv5_mobile_rec.nb",
                    minBytes = 13_000_000L,
                    expectedBytes = 14_077_259L,
                    source = AiAssetFileSource.TarGz(
                        urlResId = R.string.url_ocr_ppocrv5_rec_tar,
                        entryName = "PP-OCRv5_mobile_rec.nb",
                    ),
                ),
                AiAssetFileSpec(
                    name = "ppocr_keys_ocrv5.txt",
                    minBytes = 10_000L,
                    expectedBytes = 63_096L,
                    source = AiAssetFileSource.TarGz(
                        urlResId = R.string.url_ocr_labels_tar,
                        entryName = "ppocr_keys_ocrv5.txt",
                    ),
                ),
            ),
        ),
    )
}
