package org.devil.hytranslator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devil.hytranslator.platform.ocr.OcrTextRepository
import java.io.File

class PaddleLiteOcrTextRepository(
    private val context: Context,
    private val runtime: PaddleLiteOcrRuntime = PaddleLiteOcrRuntime(context),
) : OcrTextRepository {
    private var session: PaddleLiteOcrSession? = null

    override suspend fun recognize(bitmap: Bitmap): String {
        val activeSession = session ?: runtime.createSession().also { session = it }
        activeSession.ensureReady()
        throw OcrProcessingException("PaddleOCR recognition pipeline is not integrated yet")
    }

    override suspend fun recognize(uri: Uri, decodeFailedMessage: String): String {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val raw = BitmapFactory.decodeStream(inputStream)
                raw ?: throw OcrProcessingException(decodeFailedMessage)
            }
                ?: throw OcrProcessingException(decodeFailedMessage)
        }
        val corrected = withContext(Dispatchers.IO) {
            val orientation = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: return@withContext bitmap
            rotateBitmap(bitmap, orientation)
        }
        return recognize(corrected)
    }

    override fun close() {
        session = null
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

class PaddleLiteOcrRuntime(
    filesDir: File,
    private val predictorFactory: (MobileConfig) -> PaddleLitePredictorHandle = { config ->
        DefaultPaddleLitePredictorHandle(PaddlePredictor.createPaddlePredictor(config))
    },
) {
    constructor(
        context: Context,
        predictorFactory: (MobileConfig) -> PaddleLitePredictorHandle = { config ->
            DefaultPaddleLitePredictorHandle(PaddlePredictor.createPaddlePredictor(config))
        },
    ) : this(context.filesDir, predictorFactory)

    private val assetDir = File(filesDir, "ai-assets/pp-ocrv5-mobile")

    fun createSession(): PaddleLiteOcrSession {
        val detModel = requiredFile("PP-OCRv5_mobile_det.nb")
        val recModel = requiredFile("PP-OCRv5_mobile_rec.nb")
        val labels = requiredFile("ppocr_keys_ocrv5.txt")

        val detPredictor = createPredictor(detModel)
        val recPredictor = createPredictor(recModel)
        val labelLines = labels.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        require(labelLines.isNotEmpty()) {
            "PaddleOCR label file is empty"
        }

        return PaddleLiteOcrSession(detPredictor, recPredictor, labelLines)
    }

    private fun requiredFile(name: String): File {
        val file = assetDir.resolve(name)
        require(file.isFile) {
            "Missing PaddleOCR file: $name"
        }
        return file
    }

    private fun createPredictor(model: File): PaddleLitePredictorHandle {
        val config = MobileConfig().apply {
            setModelFromFile(model.absolutePath)
            setThreads(2)
            setPowerMode(PowerMode.LITE_POWER_HIGH)
        }
        return predictorFactory(config)
    }
}

class PaddleLiteOcrSession(
    private val detPredictor: PaddleLitePredictorHandle,
    private val recPredictor: PaddleLitePredictorHandle,
    val labels: List<String>,
) {
    fun ensureReady() {
        detPredictor.version()
        recPredictor.version()
    }
}

interface PaddleLitePredictorHandle {
    fun version(): String
}

private class DefaultPaddleLitePredictorHandle(
    private val predictor: PaddlePredictor,
) : PaddleLitePredictorHandle {
    override fun version(): String = predictor.getVersion()
}
