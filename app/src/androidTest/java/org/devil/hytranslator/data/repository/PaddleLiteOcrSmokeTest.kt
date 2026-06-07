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
import java.net.URL

@RunWith(AndroidJUnit4::class)
class PaddleLiteOcrSmokeTest {
    @Test
    fun ppOcrV5Mobile_recognizesStandardSmokeImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(context.filesDir, "ai-assets/pp-ocrv5-mobile")
        assumeTrue(
            "OCR model assets are not downloaded on device",
            REQUIRED_MODEL_FILES.all { File(modelDir, it).isFile },
        )

        val image = downloadSmokeImage(context.cacheDir)
        val bitmap = BitmapFactory.decodeFile(image.absolutePath)
        assumeTrue("OCR smoke image could not be decoded", bitmap != null)

        val text = PaddleLiteOcrTextRepository(context).use { repository ->
            repository.recognize(bitmap)
        }

        assertTrue("Expected non-empty OCR text for ${image.name}", text.isNotBlank())
    }

    private fun downloadSmokeImage(cacheDir: File): File {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val dir = File(cacheDir, "ocr-smoke-images").apply { mkdirs() }
        val target = File(dir, "general_ocr_rec_001.png")
        if (!target.isFile) {
            val url = instrumentationContext.getString(R.string.url_ocr_smoke_rec_001)
            URL(url).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private companion object {
        val REQUIRED_MODEL_FILES = listOf(
            "PP-OCRv5_mobile_det.nb",
            "PP-OCRv5_mobile_rec.nb",
            "ppocr_keys_ocrv5.txt",
        )
    }
}
