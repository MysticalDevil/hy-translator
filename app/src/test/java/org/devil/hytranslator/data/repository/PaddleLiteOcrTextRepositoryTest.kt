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

    private fun createOcrFiles(labels: String) {
        val dir = File(temporaryFolder.root, "ai-assets/pp-ocrv5-mobile")
        check(dir.mkdirs())
        File(dir, "PP-OCRv5_mobile_det.nb").writeText("det")
        File(dir, "PP-OCRv5_mobile_rec.nb").writeText("rec")
        File(dir, "ppocr_keys_ocrv5.txt").writeText(labels)
    }

    private object FakePaddleLitePredictorHandle : PaddleLitePredictorHandle {
        override fun version(): String = "test"
    }
}
