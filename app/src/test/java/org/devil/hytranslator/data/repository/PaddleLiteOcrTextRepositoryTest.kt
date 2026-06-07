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
            "PaddleOCR label dictionary size 1 is smaller than recognition output classes 2 " +
                "for shape [1, 1, 2]",
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

    @Test
    fun detectionCreateInputFromPixels_returnsNchwBgrNormalizedTensor() {
        val resize = PaddleOcrDetectionResize(
            width = 2,
            height = 1,
            ratioWidth = 1f,
            ratioHeight = 1f,
        )
        val input = PaddleOcrDetectionPreprocessor.createInputFromPixels(
            resize = resize,
            pixels = intArrayOf(
                rgb(255, 128, 0),
                rgb(0, 64, 255),
            ),
        )

        assertEquals(listOf(1L, 3L, 1L, 2L), input.shape.toList())
        assertEquals(resize, input.resize)

        val planeSize = input.resize.width * input.resize.height
        assertEquals(normalize(0, mean = 0.406f, std = 0.225f), input.data[0], 0.0001f)
        assertEquals(normalize(128, mean = 0.456f, std = 0.224f), input.data[planeSize], 0.0001f)
        assertEquals(normalize(255, mean = 0.485f, std = 0.229f), input.data[planeSize * 2], 0.0001f)
    }

    @Test
    fun detectionPostprocessor_extractsConnectedTextBoxesInSourceCoordinates() {
        val output = FloatArray(8 * 6)
        fillRect(output, width = 8, left = 1, top = 1, right = 4, bottom = 3, value = 0.9f)
        fillRect(output, width = 8, left = 5, top = 4, right = 7, bottom = 6, value = 0.8f)

        val boxes = PaddleOcrDetectionPostprocessor.detectTextBoxes(
            probabilities = output,
            shape = longArrayOf(1, 1, 6, 8),
            resize = PaddleOcrDetectionResize(
                width = 8,
                height = 6,
                ratioWidth = 0.5f,
                ratioHeight = 0.25f,
            ),
            minArea = 4,
        )

        assertEquals(2, boxes.size)
        assertTextBox(left = 2, top = 4, right = 8, bottom = 12, score = 0.9f, actual = boxes[0])
        assertTextBox(left = 10, top = 16, right = 14, bottom = 24, score = 0.8f, actual = boxes[1])
    }

    @Test
    fun detectionPostprocessor_filtersSmallComponentsAndSortsForReading() {
        val output = FloatArray(10 * 10)
        fillRect(output, width = 10, left = 7, top = 1, right = 9, bottom = 4, value = 0.8f)
        fillRect(output, width = 10, left = 1, top = 1, right = 3, bottom = 4, value = 0.9f)
        output[9 * 10 + 9] = 0.95f

        val boxes = PaddleOcrDetectionPostprocessor.detectTextBoxes(
            probabilities = output,
            shape = longArrayOf(1, 1, 10, 10),
            resize = PaddleOcrDetectionResize(
                width = 10,
                height = 10,
                ratioWidth = 1f,
                ratioHeight = 1f,
            ),
            minArea = 4,
        )

        assertEquals(2, boxes.size)
        assertEquals(listOf(1, 7), PaddleOcrDetectionPostprocessor.sortForReading(boxes).map { it.left })
    }

    @Test
    fun textBoxExpandForRecognition_addsPaddingAndClampsToSourceBounds() {
        val expanded = PaddleOcrTextBox(
            left = 10,
            top = 5,
            right = 60,
            bottom = 25,
            score = 0.9f,
        ).expandForRecognition(sourceWidth = 64, sourceHeight = 28)

        assertTextBox(left = 6, top = 2, right = 64, bottom = 28, score = 0.9f, actual = expanded)
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

    private companion object {
        fun normalize(channel: Int, mean: Float, std: Float): Float =
            (channel / 255f - mean) / std

        fun rgb(red: Int, green: Int, blue: Int): Int =
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

        fun fillRect(
            output: FloatArray,
            width: Int,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            value: Float,
        ) {
            for (y in top until bottom) {
                for (x in left until right) {
                    output[y * width + x] = value
                }
            }
        }

        fun assertTextBox(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            score: Float,
            actual: PaddleOcrTextBox,
        ) {
            assertEquals(left, actual.left)
            assertEquals(top, actual.top)
            assertEquals(right, actual.right)
            assertEquals(bottom, actual.bottom)
            assertEquals(score, actual.score, 0.0001f)
        }
    }
}
