package org.devil.hytranslator.data.repository

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.devil.hytranslator.test.R
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class PaddleLiteOcrSmokeTest {
    @Test
    fun ppOcrV5Mobile_recognizesStandardSmokeImages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(context.filesDir, "ai-assets/pp-ocrv5-mobile")
        assumeTrue(
            "OCR model assets are not downloaded on device",
            REQUIRED_MODEL_FILES.all { File(modelDir, it).isFile },
        )

        PaddleLiteOcrTextRepository(context).use { repository ->
            STANDARD_SMOKE_IMAGES.forEach { sample ->
                val image = downloadSmokeImage(context.cacheDir, sample)
                val bitmap = BitmapFactory.decodeFile(image.absolutePath)
                assumeTrue("OCR smoke image could not be decoded: ${image.name}", bitmap != null)

                val text = repository.recognize(bitmap)

                assertTrue("Expected non-empty OCR text for ${image.name}", text.isNotBlank())
            }
        }
    }

    private fun downloadSmokeImage(cacheDir: File, sample: SmokeImage): File {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val dir = File(cacheDir, "ocr-smoke-images").apply { mkdirs() }
        val target = File(dir, sample.fileName)
        if (!target.isFile) {
            val url = instrumentationContext.getString(sample.urlResId)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private data class SmokeImage(
        val fileName: String,
        val urlResId: Int,
    )

    private companion object {
        val REQUIRED_MODEL_FILES = listOf(
            "PP-OCRv5_mobile_det.nb",
            "PP-OCRv5_mobile_rec.nb",
            "ppocr_keys_ocrv5.txt",
        )
        val STANDARD_SMOKE_IMAGES = listOf(
            SmokeImage(
                fileName = "general_ocr_rec_001.png",
                urlResId = R.string.url_ocr_smoke_rec_001,
            ),
            SmokeImage(
                fileName = "general_ocr_002.png",
                urlResId = R.string.url_ocr_smoke_general_002,
            ),
        )
    }
}
