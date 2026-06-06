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
import kotlin.math.ceil

class PaddleLiteOcrTextRepository(
    private val context: Context,
    private val runtime: PaddleLiteOcrRuntime = PaddleLiteOcrRuntime(context),
) : OcrTextRepository {
    private var session: PaddleLiteOcrSession? = null

    override suspend fun recognize(bitmap: Bitmap): String {
        val activeSession = session ?: runtime.createSession().also { session = it }
        return withContext(Dispatchers.Default) {
            activeSession.recognizeSingleLine(bitmap)
        }
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
    private val characterDictionary = buildList {
        add("#")
        addAll(labels)
        add(" ")
    }

    fun ensureReady() {
        detPredictor.version()
        recPredictor.version()
    }

    fun recognizeSingleLine(bitmap: Bitmap): String {
        ensureReady()
        val input = PaddleOcrRecognitionPreprocessor.createInput(bitmap)
        recPredictor.setInput(input.shape, input.data)
        check(recPredictor.run()) {
            "PaddleOCR recognition predictor failed"
        }
        val decoded = PaddleOcrCtcDecoder.decode(
            probabilities = recPredictor.outputFloatData(),
            shape = recPredictor.outputShape(),
            dictionary = characterDictionary,
        )
        return decoded.text
    }
}

interface PaddleLitePredictorHandle {
    fun version(): String
    fun setInput(shape: LongArray, data: FloatArray)
    fun run(): Boolean
    fun outputShape(): LongArray
    fun outputFloatData(): FloatArray
}

private class DefaultPaddleLitePredictorHandle(
    private val predictor: PaddlePredictor,
) : PaddleLitePredictorHandle {
    override fun version(): String = predictor.getVersion()
    override fun setInput(shape: LongArray, data: FloatArray) {
        val input = predictor.getInput(0)
        input.resize(shape)
        input.setData(data)
    }

    override fun run(): Boolean = predictor.run()

    override fun outputShape(): LongArray = predictor.getOutput(0).shape()

    override fun outputFloatData(): FloatArray = predictor.getOutput(0).floatData
}

data class PaddleOcrDecodeResult(
    val text: String,
    val score: Float,
)

data class PaddleOcrRecognitionInput(
    val shape: LongArray,
    val data: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaddleOcrRecognitionInput) return false
        return shape.contentEquals(other.shape) && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = shape.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object PaddleOcrRecognitionPreprocessor {
    fun createInput(bitmap: Bitmap): PaddleOcrRecognitionInput {
        require(bitmap.width > 0 && bitmap.height > 0) {
            "PaddleOCR input bitmap is empty"
        }
        val resizedHeight = 32
        val widthRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val resizedWidth = ceil(resizedHeight * widthRatio).toInt().coerceAtLeast(1)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        val pixels = IntArray(resizedWidth * resizedHeight)
        resizedBitmap.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)

        val planeSize = resizedWidth * resizedHeight
        val data = FloatArray(3 * planeSize)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            data[index] = normalize(blue)
            data[planeSize + index] = normalize(green)
            data[planeSize * 2 + index] = normalize(red)
        }
        return PaddleOcrRecognitionInput(
            shape = longArrayOf(1, 3, resizedHeight.toLong(), resizedWidth.toLong()),
            data = data,
        )
    }

    private fun normalize(channel: Int): Float = (channel / 255f - 0.5f) / 0.5f
}

object PaddleOcrCtcDecoder {
    fun decode(
        probabilities: FloatArray,
        shape: LongArray,
        dictionary: List<String>,
    ): PaddleOcrDecodeResult {
        require(shape.size >= 3) {
            "PaddleOCR recognition output shape must have at least 3 dimensions"
        }
        val steps = shape[1].toInt()
        val classes = shape[2].toInt()
        require(steps > 0 && classes > 0) {
            "PaddleOCR recognition output shape is empty"
        }
        require(probabilities.size >= steps * classes) {
            "PaddleOCR recognition output data is shorter than its shape"
        }
        require(dictionary.size >= classes) {
            "PaddleOCR label dictionary is smaller than recognition output classes"
        }

        val text = StringBuilder()
        var lastIndex = 0
        var score = 0f
        var count = 0
        for (step in 0 until steps) {
            val offset = step * classes
            var maxIndex = 0
            var maxValue = probabilities[offset]
            for (index in 1 until classes) {
                val value = probabilities[offset + index]
                if (value > maxValue) {
                    maxValue = value
                    maxIndex = index
                }
            }
            if (maxIndex > 0 && maxIndex != lastIndex) {
                text.append(dictionary[maxIndex])
                score += maxValue
                count += 1
            }
            lastIndex = maxIndex
        }
        return PaddleOcrDecodeResult(
            text = text.toString(),
            score = if (count == 0) 0f else score / count,
        )
    }
}
