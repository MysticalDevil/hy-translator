package org.devil.hytranslator.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PaddleLiteOcrTextRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createSession_whenModelFileIsMissing_reportsMissingFile() {
        val runtime = PaddleLiteOcrRuntime(
            filesDir = temporaryFolder.root,
            predictorFactory = { FakePaddleLitePredictorHandle },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runtime.createSession()
        }

        assertEquals("Missing PaddleOCR file: PP-OCRv5_mobile_det.nb", error.message)
    }

    @Test
    fun createSession_whenFilesExist_createsDetAndRecPredictors() {
        createOcrFiles(labels = "a\nb\n")
        val modelPaths = mutableListOf<String>()
        val runtime = PaddleLiteOcrRuntime(
            filesDir = temporaryFolder.root,
            predictorFactory = { config ->
                modelPaths += config.getModelFromFile()
                FakePaddleLitePredictorHandle
            },
        )

        val session = runtime.createSession()
        session.ensureReady()

        assertEquals(listOf("a", "b"), session.labels)
        assertEquals(2, modelPaths.size)
        assertTrue(modelPaths[0].endsWith("PP-OCRv5_mobile_det.nb"))
        assertTrue(modelPaths[1].endsWith("PP-OCRv5_mobile_rec.nb"))
    }

    @Test
    fun createSession_whenLabelsAreEmpty_reportsInvalidLabelFile() {
        createOcrFiles(labels = "\n")
        val runtime = PaddleLiteOcrRuntime(
            filesDir = temporaryFolder.root,
            predictorFactory = { FakePaddleLitePredictorHandle },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runtime.createSession()
        }

        assertEquals("PaddleOCR label file is empty", error.message)
    }

    @Test
    fun decode_whenOutputHasRepeatsAndBlank_decodesCtcText() {
        val result = PaddleOcrCtcDecoder.decode(
            probabilities = floatArrayOf(
                0.1f,
                0.8f,
                0.1f,
                0.1f,
                0.7f,
                0.2f,
                0.9f,
                0.05f,
                0.05f,
                0.05f,
                0.1f,
                0.85f,
            ),
            shape = longArrayOf(1, 4, 3),
            dictionary = listOf("#", "你", "好"),
        )

        assertEquals("你好", result.text)
        assertEquals(0.825f, result.score, 0.0001f)
    }

    @Test
    fun decode_whenDictionaryIsTooSmall_reportsInvalidLabels() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PaddleOcrCtcDecoder.decode(
                probabilities = floatArrayOf(0.1f, 0.9f),
                shape = longArrayOf(1, 1, 2),
                dictionary = listOf("#"),
            )
        }

        assertEquals(
            "PaddleOCR label dictionary is smaller than recognition output classes",
            error.message,
        )
    }

    @Test
    fun detectionResizePlan_downscalesLongestSideAndAlignsToStride() {
        val resize = PaddleOcrDetectionPreprocessor.resizePlan(
            sourceWidth = 1920,
            sourceHeight = 1080,
        )

        assertEquals(960, resize.width)
        assertEquals(544, resize.height)
        assertEquals(0.5f, resize.ratioWidth, 0.0001f)
        assertEquals(544f / 1080f, resize.ratioHeight, 0.0001f)
    }

    @Test
    fun detectionResizePlan_keepsSmallImagesAndAlignsToNearestStride() {
        val resize = PaddleOcrDetectionPreprocessor.resizePlan(
            sourceWidth = 321,
            sourceHeight = 240,
        )

        assertEquals(320, resize.width)
        assertEquals(256, resize.height)
        assertEquals(320f / 321f, resize.ratioWidth, 0.0001f)
        assertEquals(256f / 240f, resize.ratioHeight, 0.0001f)
    }

    @Test
    fun detectionResizePlan_rejectsEmptyInput() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PaddleOcrDetectionPreprocessor.resizePlan(
                sourceWidth = 0,
                sourceHeight = 240,
            )
        }

        assertEquals("PaddleOCR detection input bitmap is empty", error.message)
    }

    private fun createOcrFiles(labels: String) {
        val dir = File(temporaryFolder.root, "ai-assets/pp-ocrv5-mobile")
        check(dir.mkdirs())
        File(dir, "PP-OCRv5_mobile_det.nb").writeText("det")
        File(dir, "PP-OCRv5_mobile_rec.nb").writeText("rec")
        File(dir, "ppocr_keys_ocrv5.txt").writeText(labels)
    }

    private object FakePaddleLitePredictorHandle : PaddleLitePredictorHandle {
        override fun version(): String = "test"
        override fun setInput(shape: LongArray, data: FloatArray) = Unit
        override fun run(): Boolean = true
        override fun outputShape(): LongArray = longArrayOf(1, 1, 1)
        override fun outputFloatData(): FloatArray = floatArrayOf(1f)
    }
}
