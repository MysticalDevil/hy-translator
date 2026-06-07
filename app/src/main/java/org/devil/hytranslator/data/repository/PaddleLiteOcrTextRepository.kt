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
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PaddleLiteOcrTextRepository(
    private val context: Context,
    private val runtime: PaddleLiteOcrRuntime = PaddleLiteOcrRuntime(context),
) : OcrTextRepository {
    private var session: PaddleLiteOcrSession? = null

    override suspend fun recognize(bitmap: Bitmap): String {
        val activeSession = session ?: runtime.createSession().also { session = it }
        return withContext(Dispatchers.Default) {
            activeSession.recognize(bitmap)
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
        add("")
    }

    fun ensureReady() {
        detPredictor.version()
        recPredictor.version()
    }

    fun recognizeSingleLine(bitmap: Bitmap): String {
        ensureReady()
        return recognizeBitmapLine(bitmap)
    }

    fun recognize(bitmap: Bitmap): String {
        ensureReady()
        val input = PaddleOcrDetectionPreprocessor.createInput(bitmap)
        detPredictor.setInput(input.shape, input.data)
        check(detPredictor.run()) {
            "PaddleOCR detection predictor failed"
        }

        val boxes = PaddleOcrDetectionPostprocessor.detectTextBoxes(
            probabilities = detPredictor.outputFloatData(),
            shape = detPredictor.outputShape(),
            resize = input.resize,
        )
        if (boxes.isEmpty()) return ""

        return PaddleOcrDetectionPostprocessor.sortForReading(boxes)
            .mapNotNull { box ->
                val crop = bitmap.crop(box)
                val text = recognizeBitmapLine(crop)
                text.takeIf { it.isNotBlank() }
            }
            .joinToString(separator = "\n")
    }

    private fun recognizeBitmapLine(bitmap: Bitmap): String {
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

    private fun Bitmap.crop(box: PaddleOcrTextBox): Bitmap {
        val paddedBox = box.expandForRecognition(sourceWidth = width, sourceHeight = height)
        val left = paddedBox.left.coerceIn(0, width - 1)
        val top = paddedBox.top.coerceIn(0, height - 1)
        val right = paddedBox.right.coerceIn(left + 1, width)
        val bottom = paddedBox.bottom.coerceIn(top + 1, height)
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
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

data class PaddleOcrDetectionResize(
    val width: Int,
    val height: Int,
    val ratioWidth: Float,
    val ratioHeight: Float,
)

data class PaddleOcrDetectionInput(
    val shape: LongArray,
    val data: FloatArray,
    val resize: PaddleOcrDetectionResize,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaddleOcrDetectionInput) return false
        return shape.contentEquals(other.shape) &&
            data.contentEquals(other.data) &&
            resize == other.resize
    }

    override fun hashCode(): Int {
        var result = shape.contentHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + resize.hashCode()
        return result
    }
}

object PaddleOcrDetectionPreprocessor {
    fun createInput(
        bitmap: Bitmap,
        maxSideLength: Int = DEFAULT_MAX_SIDE_LENGTH,
    ): PaddleOcrDetectionInput {
        val resize = resizePlan(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            maxSideLength = maxSideLength,
        )
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, resize.width, resize.height, true)
        val pixels = IntArray(resize.width * resize.height)
        resizedBitmap.getPixels(pixels, 0, resize.width, 0, 0, resize.width, resize.height)
        return createInputFromPixels(resize, pixels)
    }

    fun createInputFromPixels(
        resize: PaddleOcrDetectionResize,
        pixels: IntArray,
    ): PaddleOcrDetectionInput {
        require(pixels.size >= resize.width * resize.height) {
            "PaddleOCR detection pixel buffer is shorter than resize shape"
        }

        val planeSize = resize.width * resize.height
        val data = FloatArray(3 * planeSize)
        for (index in 0 until planeSize) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            data[index] = normalize(blue, DET_MEAN_BLUE, DET_STD_BLUE)
            data[planeSize + index] = normalize(green, DET_MEAN_GREEN, DET_STD_GREEN)
            data[planeSize * 2 + index] = normalize(red, DET_MEAN_RED, DET_STD_RED)
        }

        return PaddleOcrDetectionInput(
            shape = longArrayOf(1, 3, resize.height.toLong(), resize.width.toLong()),
            data = data,
            resize = resize,
        )
    }

    fun resizePlan(
        sourceWidth: Int,
        sourceHeight: Int,
        maxSideLength: Int = DEFAULT_MAX_SIDE_LENGTH,
    ): PaddleOcrDetectionResize {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "PaddleOCR detection input bitmap is empty"
        }
        require(maxSideLength >= MODEL_STRIDE) {
            "PaddleOCR detection max side must be at least $MODEL_STRIDE"
        }

        val scale = if (sourceWidth > sourceHeight) {
            maxSideLength.toFloat() / sourceWidth.toFloat()
        } else {
            maxSideLength.toFloat() / sourceHeight.toFloat()
        }.coerceAtMost(1f)

        val resizedWidth = alignToStride((sourceWidth * scale).roundToInt())
        val resizedHeight = alignToStride((sourceHeight * scale).roundToInt())

        return PaddleOcrDetectionResize(
            width = resizedWidth,
            height = resizedHeight,
            ratioWidth = resizedWidth.toFloat() / sourceWidth.toFloat(),
            ratioHeight = resizedHeight.toFloat() / sourceHeight.toFloat(),
        )
    }

    private fun alignToStride(value: Int): Int =
        value
            .coerceAtLeast(MODEL_STRIDE)
            .let { aligned -> ((aligned + MODEL_STRIDE / 2) / MODEL_STRIDE) * MODEL_STRIDE }
            .coerceAtLeast(MODEL_STRIDE)

    private fun normalize(channel: Int, mean: Float, std: Float): Float =
        (channel / 255f - mean) / std

    private const val DEFAULT_MAX_SIDE_LENGTH = 960
    private const val MODEL_STRIDE = 32
    private const val DET_MEAN_BLUE = 0.406f
    private const val DET_MEAN_GREEN = 0.456f
    private const val DET_MEAN_RED = 0.485f
    private const val DET_STD_BLUE = 0.225f
    private const val DET_STD_GREEN = 0.224f
    private const val DET_STD_RED = 0.229f
}

data class PaddleOcrTextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val score: Float,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width * height

    fun expandForRecognition(
        sourceWidth: Int,
        sourceHeight: Int,
        horizontalRatio: Float = DEFAULT_RECOGNITION_HORIZONTAL_PADDING_RATIO,
        verticalRatio: Float = DEFAULT_RECOGNITION_VERTICAL_PADDING_RATIO,
    ): PaddleOcrTextBox {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "PaddleOCR crop source size must be positive"
        }
        require(horizontalRatio >= 0f && verticalRatio >= 0f) {
            "PaddleOCR crop padding ratio must be non-negative"
        }

        val horizontalPadding = (width * horizontalRatio).roundToInt().coerceAtLeast(2)
        val verticalPadding = (height * verticalRatio).roundToInt().coerceAtLeast(2)
        return copy(
            left = (left - horizontalPadding).coerceAtLeast(0),
            top = (top - verticalPadding).coerceAtLeast(0),
            right = (right + horizontalPadding).coerceAtMost(sourceWidth),
            bottom = (bottom + verticalPadding).coerceAtMost(sourceHeight),
        )
    }

    private companion object {
        const val DEFAULT_RECOGNITION_HORIZONTAL_PADDING_RATIO = 0.08f
        const val DEFAULT_RECOGNITION_VERTICAL_PADDING_RATIO = 0.15f
    }
}

object PaddleOcrDetectionPostprocessor {
    fun detectTextBoxes(
        probabilities: FloatArray,
        shape: LongArray,
        resize: PaddleOcrDetectionResize,
        threshold: Float = DEFAULT_THRESHOLD,
        minArea: Int = DEFAULT_MIN_AREA,
    ): List<PaddleOcrTextBox> {
        require(shape.size >= 4) {
            "PaddleOCR detection output shape must have at least 4 dimensions"
        }
        val height = shape[shape.size - 2].toInt()
        val width = shape[shape.size - 1].toInt()
        require(width > 0 && height > 0) {
            "PaddleOCR detection output shape is empty"
        }
        require(probabilities.size >= width * height) {
            "PaddleOCR detection output data is shorter than its shape"
        }
        require(threshold in 0f..1f) {
            "PaddleOCR detection threshold must be between 0 and 1"
        }

        val visited = BooleanArray(width * height)
        val boxes = mutableListOf<PaddleOcrTextBox>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (visited[index] || probabilities[index] < threshold) continue
                val component = floodFill(
                    probabilities = probabilities,
                    visited = visited,
                    width = width,
                    height = height,
                    startX = x,
                    startY = y,
                    threshold = threshold,
                )
                val box = component.toSourceBox(resize)
                if (box.area >= minArea) {
                    boxes += box
                }
            }
        }
        return boxes
    }

    fun sortForReading(boxes: List<PaddleOcrTextBox>): List<PaddleOcrTextBox> =
        boxes.sortedWith(
            compareBy<PaddleOcrTextBox> { box -> box.top }
                .thenBy { box -> box.left },
        )

    private fun floodFill(
        probabilities: FloatArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        threshold: Float,
    ): DetectionComponent {
        val queue = ArrayDeque<Int>()
        queue.add(startY * width + startX)

        var left = startX
        var top = startY
        var right = startX
        var bottom = startY
        var score = 0f
        var count = 0

        while (queue.isNotEmpty()) {
            val encoded = queue.removeFirst()
            val x = encoded % width
            val y = encoded / width

            if (x !in 0 until width || y !in 0 until height) continue
            val index = y * width + x
            if (visited[index] || probabilities[index] < threshold) continue
            visited[index] = true

            left = min(left, x)
            top = min(top, y)
            right = max(right, x)
            bottom = max(bottom, y)
            score += probabilities[index]
            count += 1

            if (x > 0) queue.add(y * width + x - 1)
            if (x < width - 1) queue.add(y * width + x + 1)
            if (y > 0) queue.add((y - 1) * width + x)
            if (y < height - 1) queue.add((y + 1) * width + x)
        }

        return DetectionComponent(
            left = left,
            top = top,
            right = right + 1,
            bottom = bottom + 1,
            score = if (count == 0) 0f else score / count,
        )
    }

    private data class DetectionComponent(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float,
    ) {
        fun toSourceBox(resize: PaddleOcrDetectionResize): PaddleOcrTextBox =
            PaddleOcrTextBox(
                left = (left / resize.ratioWidth).roundToInt(),
                top = (top / resize.ratioHeight).roundToInt(),
                right = (right / resize.ratioWidth).roundToInt(),
                bottom = (bottom / resize.ratioHeight).roundToInt(),
                score = score,
            )
    }

    private const val DEFAULT_THRESHOLD = 0.3f
    private const val DEFAULT_MIN_AREA = 16
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
            "PaddleOCR label dictionary size ${dictionary.size} is smaller than " +
                "recognition output classes $classes for shape ${shape.toList()}"
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
